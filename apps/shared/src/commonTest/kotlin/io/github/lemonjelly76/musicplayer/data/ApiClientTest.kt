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
