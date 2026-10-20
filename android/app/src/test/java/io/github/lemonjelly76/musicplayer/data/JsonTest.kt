package io.github.lemonjelly76.musicplayer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonTest {

    @Test
    fun readsListsWithPicksVerdictsAndLinks() {
        val lists = Json.lists("""
            [{"id":"l1","name":"AI Picks — Running","categoryId":"c1","createdAt":"2026-10-20T18:00:00Z",
              "playlistLink":"https://tidal.com/browse/playlist/p1",
              "picks":[{"id":"p1","reason":"Driving beat","source":"WILDCARD","verdict":"NOT_FOR_ME","comment":"Slow",
                "album":{"ref":"tidal:9","artist":"Portishead","title":"Dummy","year":1994,
                  "link":"https://tidal.com/browse/album/9"}},
                {"id":"p2","reason":"Bright","source":"POOL",
                "album":{"ref":"Air — Moon Safari","artist":"Air","title":"Moon Safari"}}]}]
        """.trimIndent())

        val list = lists.single()
        assertEquals("AI Picks — Running", list.name)
        assertEquals("https://tidal.com/browse/playlist/p1", list.playlistLink)
        val (first, second) = list.picks
        assertEquals(Verdict.NOT_FOR_ME, first.verdict)
        assertTrue(first.wildcard)
        assertEquals(1994, first.album.year)
        assertNull(second.verdict)
        assertNull(second.album.year)
        assertNull(second.album.link)
    }

    @Test
    fun readsTheLibraryWithoutARating() {
        val entry = Json.library("""
            [{"id":"a1","album":{"ref":"tidal:1","artist":"Joni Mitchell","title":"Blue"},"tags":["folk"],
              "addedOn":"2026-10-20"}]
        """.trimIndent()).single()

        assertNull(entry.rating)
        assertEquals(listOf("folk"), entry.tags)
        assertEquals("", entry.notes)
    }

    @Test
    fun readsTrackingPerCategory() {
        val tracking = Json.tracking("""
            {"enabled":true,"categories":[{"categoryId":"c1","name":"Focus","enabled":false,"overridden":true}]}
        """.trimIndent())

        assertTrue(tracking.enabled)
        assertEquals(CategoryTracking("c1", "Focus", enabled = false, overridden = true), tracking.categories.single())
    }

    @Test
    fun readsPeopleAndTheServersErrorMessage() {
        val person = Json.people("""[{"id":"1","name":"Craig","service":"tidal","connected":false,"playTracking":true}]""")
            .single()

        assertEquals("tidal", person.service)
        assertEquals("No person with id x", Json.errorMessage("""{"message":"No person with id x"}"""))
        assertNull(Json.errorMessage("<html>"))
    }

    @Test
    fun signsInWithBasicAuth() {
        assertEquals("Basic dGVzdGVyOnNlY3JldA==", ApiClient.basic("tester", "secret"))
    }

    @Test
    fun readsWhichServicesTheServerIsSetUpFor() {
        val services = Json.services("""[{"service":"tidal","configured":true},{"service":"spotify","configured":false}]""")

        assertEquals(listOf(Service("tidal", true), Service("spotify", false)), services)
        assertEquals("Spotify", services[1].label)
    }

    @Test
    fun readsOnePerson() {
        val person = Json.person("""{"id":"1","name":"Craig","service":"spotify","connected":false,"playTracking":true}""")

        assertEquals("spotify", person.service)
    }

    @Test
    fun aServerAddressWithoutASchemeIsTakenAsHttps() {
        assertEquals(ServerSettings("https://pc.tailnet.ts.net:8444", "jelly", " pw"),
            ServerSettings(" pc.tailnet.ts.net:8444/ ", " jelly ", " pw").normalised())
        assertEquals("http://192.168.1.2:8082", ServerSettings("http://192.168.1.2:8082", "a", "b").normalised().serverUrl)
    }

    @Test
    fun namesServicesTheSameWayEverywhere() {
        assertEquals("Spotify", Service("spotify", true).label)
        assertEquals("Spotify", serviceOfLink("https://open.spotify.com/album/1"))
        assertEquals("Tidal", serviceOfLink("https://tidal.com/browse/album/1"))
        assertEquals("app", serviceOfLink("https://example.com/album/1"))
    }
}
