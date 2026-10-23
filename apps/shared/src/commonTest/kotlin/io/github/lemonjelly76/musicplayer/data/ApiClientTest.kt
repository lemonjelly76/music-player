package io.github.lemonjelly76.musicplayer.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiClientTest {

    private val server = ServerSettings("https://pc.tailnet.ts.net:8444", "tester", "secret")
    private val requests = mutableListOf<HttpRequestData>()

    private fun client(settings: ServerSettings = server, answer: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        ApiClient({ settings }, HttpClient(MockEngine { request -> requests += request; answer(request) }))

    private fun MockRequestHandleScope.json(text: String) =
        respond(text, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    @Test
    fun signsInWithTheHouseholdLogin() = runTest {
        val people = client { json("""[{"id":"1","name":"Craig"}]""") }.people()

        assertEquals("Craig", people.single().name)
        val request = requests.single()
        assertEquals("https://pc.tailnet.ts.net:8444/api/people", request.url.toString())
        assertEquals("Basic dGVzdGVyOnNlY3JldA==", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun asksForAnActivitysLibraryWithItsIdEncoded() = runTest {
        client { json("[]") }.library("1", "c 1&x")

        assertEquals("https://pc.tailnet.ts.net:8444/api/people/1/library?category=c+1%26x", requests.single().url.toString())
    }

    @Test
    fun searchesTheCatalogueForTheActivity() = runTest {
        client { json("[]") }.search("1", "Talk Talk", "c1")

        assertEquals("https://pc.tailnet.ts.net:8444/api/people/1/search?q=Talk+Talk&category=c1",
            requests.single().url.toString())
    }

    @Test
    fun copiesAnActivitysPlaylistsToAnotherServiceAndConnectsIt() = runTest {
        val api = client { request -> json(when {
            "connect" in request.url.encodedPath -> """{"service":"spotify","url":"https://accounts.spotify.com/a"}"""
            request.method == HttpMethod.Put -> """{"status":"copied to spotify"}"""
            else -> "[]"
        }) }

        val status = api.copyPlaylists("1", "c1", "spotify")
        api.copyPlaylists("1", null, null)
        api.playlistCopies("1")
        val url = api.connectUrl("1", "spotify")

        val (copy, stop, list, connect) = requests
        assertEquals("copied to spotify", status)
        assertEquals("https://pc.tailnet.ts.net:8444/api/people/1/playlist-copy?category=c1", copy.url.toString())
        assertEquals("""{"service":"spotify"}""", copy.body.toByteArray().decodeToString())
        assertEquals("https://pc.tailnet.ts.net:8444/api/people/1/playlist-copy?category=none", stop.url.toString())
        assertEquals("""{"service":null}""", stop.body.toByteArray().decodeToString())
        assertEquals("https://pc.tailnet.ts.net:8444/api/people/1/playlist-copies", list.url.toString())
        assertEquals("https://pc.tailnet.ts.net:8444/api/people/1/connect?service=spotify", connect.url.toString())
        assertEquals("https://accounts.spotify.com/a", url)
    }

    @Test
    fun deletesPlaylistsListsPicksAndLibraryAlbums() = runTest {
        val api = client { json("""{"status":"Deleted AI Picks 1 here and from tidal"}""") }

        val status = api.deletePlaylist("1", "p1")
        api.deleteList("1", "l1")
        api.removePick("1", "l1", "k1")
        api.removeFromLibrary("1", "a1")

        assertEquals("Deleted AI Picks 1 here and from tidal", status)
        assertEquals(List(4) { HttpMethod.Delete }, requests.map { it.method })
        assertEquals(listOf("/api/people/1/playlists/p1", "/api/people/1/lists/l1", "/api/people/1/lists/l1/picks/k1",
            "/api/people/1/library/a1"), requests.map { it.url.encodedPath })
    }

    @Test
    fun renamesAnActivity() = runTest {
        val renamed = client { json("""{"id":"c1","name":"Focus","rules":"Instrumental"}""") }
            .renameCategory("c1", "Focus")

        assertEquals(Category("c1", "Focus", "Instrumental"), renamed)
        val request = requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("https://pc.tailnet.ts.net:8444/api/categories/c1", request.url.toString())
        assertEquals("""{"name":"Focus"}""", request.body.toByteArray().decodeToString())
    }

    @Test
    fun ratesASongAsTheAppShowedItAndSearchesSongs() = runTest {
        val api = client { request -> json(if (request.method == HttpMethod.Post) "{}" else "[]") }
        val song = Track("t1", "Air", "Sexy Boy", "Moon Safari", null, null, ref = "spotify:t1")

        api.rateSong("1", song, "c1", Rating.LOVE)
        api.searchSongs("1", "sexy boy", GENERAL)
        api.songs("1", "c1")

        val (rate, search, songs) = requests
        assertEquals("https://pc.tailnet.ts.net:8444/api/people/1/song-ratings", rate.url.toString())
        assertEquals("""{"ref":"spotify:t1","artist":"Air","title":"Sexy Boy","album":"Moon Safari","cover":null,""" +
            """"categoryId":"c1","rating":3}""", rate.body.toByteArray().decodeToString())
        assertEquals("https://pc.tailnet.ts.net:8444/api/people/1/songs/search?q=sexy+boy&category=none",
            search.url.toString())
        assertEquals("https://pc.tailnet.ts.net:8444/api/people/1/songs?category=c1", songs.url.toString())
    }

    @Test
    fun reordersTheActivities() = runTest {
        val order = client { json("""[{"id":"c2","name":"Dinner"},{"id":"c1","name":"Running"}]""") }
            .reorderCategories(listOf("c2", "c1"))

        assertEquals(listOf("Dinner", "Running"), order.map { it.name })
        val request = requests.single()
        assertEquals("https://pc.tailnet.ts.net:8444/api/categories/order", request.url.toString())
        assertEquals("""{"ids":["c2","c1"]}""", request.body.toByteArray().decodeToString())
    }

    @Test
    fun readsTheChartAndTurnsTheWeeklyPlaylistOff() = runTest {
        val api = client { request ->
            json(if (request.method == HttpMethod.Put) """{"enabled":false}""" else """{"songs":[],"albums":[]}""")
        }

        api.charts("1")
        val enabled = api.setChartPlaylist("1", false)

        assertFalse(enabled)
        val (read, change) = requests
        assertEquals("https://pc.tailnet.ts.net:8444/api/people/1/charts", read.url.toString())
        assertEquals("""{"enabled":false}""", change.body.toByteArray().decodeToString())
    }

    @Test
    fun removesAnActivity() = runTest {
        client { respond("", HttpStatusCode.NoContent) }.removeCategory("c1")

        val request = requests.single()
        assertEquals(HttpMethod.Delete, request.method)
        assertEquals("https://pc.tailnet.ts.net:8444/api/categories/c1", request.url.toString())
    }

    @Test
    fun putsAPlaylistInASpotifySlot() = runTest {
        val placed = client { json("""{"id":"pl1","name":"AI Picks — Running","slot":3,"tracks":[]}""") }
            .putPlaylistInSlot("1", "pl1", 3)

        assertEquals(3, placed.slot)
        val request = requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("https://pc.tailnet.ts.net:8444/api/people/1/playlists/pl1/slot", request.url.toString())
        assertEquals("""{"slot":3}""", request.body.toByteArray().decodeToString())
    }

    @Test
    fun asksForTheActivitysPlaylists() = runTest {
        client { json("[]") }.playlists("1", "c1", 5)

        assertEquals("https://pc.tailnet.ts.net:8444/api/people/1/playlists?category=c1&limit=5",
            requests.single().url.toString())
    }

    @Test
    fun asksForPlaysOverAPeriodForTheActivity() = runTest {
        client { json("""{"tracking":true,"days":182,"tracks":0,"albumCount":0}""") }.listening("1", "c1", 182)

        assertEquals("https://pc.tailnet.ts.net:8444/api/people/1/plays?category=c1&days=182",
            requests.single().url.toString())
    }

    @Test
    fun asksForTheAlbumsNotForThePersonAndThoseStillToSort() = runTest {
        val api = client { json("[]") }

        api.disliked("1", "c1")
        api.toSort("1", GENERAL)

        assertEquals(listOf("https://pc.tailnet.ts.net:8444/api/people/1/library/disliked?category=c1",
            "https://pc.tailnet.ts.net:8444/api/people/1/library/to-sort?category=none"),
            requests.map { it.url.toString() })
    }

    @Test
    fun ratesWithTheActivityAndTakesARatingAwayWithNull() = runTest {
        val api = client { json("{}") }

        api.rate("1", "tidal:9", "c1", Rating.LOVE)
        api.rate("1", "tidal:9", GENERAL, null)

        val (love, removal) = requests
        assertEquals(HttpMethod.Post, love.method)
        assertEquals("https://pc.tailnet.ts.net:8444/api/people/1/ratings", love.url.toString())
        assertEquals("""{"albumRef":"tidal:9","categoryId":"c1","rating":3}""", love.body.toByteArray().decodeToString())
        assertEquals("""{"albumRef":"tidal:9","categoryId":"none","rating":null}""",
            removal.body.toByteArray().decodeToString())
    }

    @Test
    fun turnsPlayCountingOnForEveryActivity() = runTest {
        val tracking = client { json("""{"enabled":true}""") }.setPlayCounting("1", true)

        assertTrue(tracking.enabled)
        assertEquals(HttpMethod.Put, requests.single().method)
        assertEquals("""{"categoryId":null,"enabled":true}""", requests.single().body.toByteArray().decodeToString())
    }

    @Test
    fun saysWhenTheServerRefusesTheLogin() = runTest {
        val error = assertFailsWith<ApiException> { client { respondError(HttpStatusCode.Unauthorized) }.people() }

        assertTrue(error.unauthorized)
        assertEquals("The server did not accept the login; check Settings", error.message)
    }

    @Test
    fun passesOnTheServersOwnErrorMessage() = runTest {
        val error = assertFailsWith<ApiException> {
            client { respond("""{"message":"No person with id 9"}""", HttpStatusCode.NotFound) }.people()
        }

        assertFalse(error.unauthorized)
        assertEquals("No person with id 9", error.message)
    }

    @Test
    fun givesTheStatusWhenTheServerSaysNothingUseful() = runTest {
        val error = assertFailsWith<ApiException> { client { respond("<html>", HttpStatusCode.BadGateway) }.people() }

        assertEquals("The server answered 502", error.message)
    }

    @Test
    fun suggestsTailscaleWhenTheServerCannotBeReached() = runTest {
        val error = assertFailsWith<ApiException> { client { throw IOException("connect failed") }.people() }

        assertEquals("Can't reach the server at https://pc.tailnet.ts.net:8444. Is Tailscale on?", error.message)
    }

    @Test
    fun saysWhenTheServerAddressIsNotAWebAddress() = runTest {
        val error = assertFailsWith<ApiException> {
            client(server.copy(serverUrl = "https://exa mple:99999")) { json("[]") }.people()
        }

        assertEquals("The server address \"https://exa mple:99999\" is not a web address; check Settings", error.message)
    }
}
