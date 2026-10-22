package io.github.lemonjelly76.musicplayer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JsonTest {

    @Test
    fun readsAlbumsToTryWithRatingsAndLinks() {
        val lists = Json.lists("""
            [{"id":"l1","name":"AI Picks — Running","categoryId":"c1","createdAt":"2026-10-20T18:00:00Z",
              "picks":[{"id":"p1","reason":"Driving beat","source":"WILDCARD","rating":1,
                "album":{"ref":"tidal:9","artist":"Portishead","title":"Dummy","year":1994,
                  "link":"https://tidal.com/browse/album/9","cover":"https://resources.tidal.com/images/9.jpg"}},
                {"id":"p2","reason":"Bright","source":"POOL",
                "album":{"ref":"Air — Moon Safari","artist":"Air","title":"Moon Safari"}}]}]
        """.trimIndent())

        val list = lists.single()
        assertEquals("AI Picks — Running", list.name)
        val (first, second) = list.picks
        assertEquals(Rating.DISLIKE, first.rating)
        assertTrue(first.wildcard)
        assertEquals(1994, first.album.year)
        assertNull(second.rating)
        assertNull(second.album.year)
        assertNull(second.album.link)
        assertEquals("https://resources.tidal.com/images/9.jpg", first.album.cover)
        assertNull(second.album.cover)
    }

    @Test
    fun readsPlaylistsOfSongs() {
        val playlists = Json.playlists("""
            [{"id":"pl1","name":"AI Picks — Running","description":"Bright and fast","categoryId":"c1",
              "createdAt":"2026-10-21T18:00:00Z","slot":2,"link":"https://open.spotify.com/playlist/9",
              "tracks":[{"id":"t1","artist":"Air","title":"Sexy Boy","album":"Moon Safari",
                "cover":"https://i/300","link":"https://open.spotify.com/track/t1"}]},
             {"id":"pl0","name":"AI Picks — Running","createdAt":"2026-10-20T18:00:00Z","link":null,
              "tracks":[{"id":"t2","artist":"Air","title":"Kelly Watch the Stars","album":"Moon Safari"}]}]
        """.trimIndent())

        val (newest, older) = playlists
        assertEquals("Bright and fast", newest.description)
        assertEquals("https://open.spotify.com/playlist/9", newest.link)
        assertEquals(Track("t1", "Air", "Sexy Boy", "Moon Safari", "https://i/300", "https://open.spotify.com/track/t1"),
            newest.tracks.single())
        assertNull(newest.tracks.single().rating)
        assertEquals(2, newest.slot)
        assertNull(older.link)
        assertNull(older.slot)
        assertEquals("", older.description)
        assertNull(older.tracks.single().cover)
    }

    @Test
    fun readsSongsWithTheirRatingForTheActivity() {
        val (loved, general) = Json.songs("""
            [{"ref":"spotify:t1","id":"t1","artist":"Air","title":"Sexy Boy","album":"Moon Safari","rating":3,
              "general":false},
             {"ref":"spotify:t2","id":"t2","artist":"Air","title":"Talisman","album":"Moon Safari","rating":1,
              "general":true}]
        """.trimIndent())

        assertEquals("spotify:t1", loved.ref)
        assertEquals(Rating.LOVE, loved.rating)
        assertEquals(Rating.DISLIKE, general.rating)
        assertTrue(general.general)
    }

    @Test
    fun readsTheWeeksChart() {
        val charts = Json.charts("""
            {"country":"gb","updatedAt":"2026-10-21T09:00:00Z","playlistWanted":true,
             "playlistLink":"https://open.spotify.com/playlist/p9",
             "songs":[{"position":1,"song":{"ref":"spotify:t1","id":"t1","artist":"Tate McRae","title":"Housewife",
               "album":"Housewife","cover":"https://i/1","rating":3,"general":false}}],
             "albums":[{"position":2,"album":{"album":{"ref":"spotify:a1","artist":"Fontaines D.C.",
               "title":"Dopamine Chamber"},"rating":null,"general":false}}]}
        """.trimIndent())

        assertEquals("https://open.spotify.com/playlist/p9", charts.playlistLink)
        assertEquals(1, charts.songs.single().position)
        assertEquals(Rating.LOVE, charts.songs.single().song.rating)
        assertEquals(2, charts.albums.single().position)
        assertEquals("Dopamine Chamber", charts.albums.single().album.album.title)
        assertEquals(listOf("https://i/1"), charts.covers.map { it.cover })
    }

    @Test
    fun readsAChartNotFetchedYet() {
        val charts = Json.charts("""{"updatedAt":null,"playlistWanted":true,"songs":[],"albums":[]}""")

        assertNull(charts.updatedAt)
        assertTrue(charts.songs.isEmpty())
    }

    @Test
    fun aPlaylistShowsItsSongsAlbumCoversOnceEach() {
        val tracks = listOf("a", "a", "b").mapIndexed { n, cover -> Track("t$n", "Air", "Song $n", "Album $cover", cover, null) }

        assertEquals(listOf("a", "b"), Playlist("p", "AI Picks", "", null, "", null, tracks).covers.map { it.cover })
    }

    @Test
    fun readsALibraryAlbumsRatingAndWhetherItIsTheGeneralOne() {
        val entry = Json.library("""
            [{"id":"a1","album":{"ref":"tidal:1","artist":"Joni Mitchell","title":"Blue"},"rating":3,"general":true}]
        """.trimIndent()).single()

        assertEquals(Rating.LOVE, entry.rating)
        assertTrue(entry.general)
    }

    @Test
    fun readsSearchResultsWithThePersonsRating() {
        val (known, new) = Json.found("""
            [{"album":{"ref":"tidal:1","artist":"Talk Talk","title":"Spirit of Eden"},"rating":3,"general":true},
             {"album":{"ref":"tidal:2","artist":"Talk Talk","title":"Laughing Stock"},"rating":null,"general":false}]
        """.trimIndent())

        assertEquals(Rating.LOVE, known.rating)
        assertTrue(known.general)
        assertEquals("Laughing Stock", new.album.title)
        assertNull(new.rating)
    }

    @Test
    fun readsWhatThePersonPlayedMost() {
        val listening = Json.listening("""
            {"tracking":true,"days":28,"tracks":12,"albumCount":2,
             "albums":[{"album":{"ref":"spotify:1","artist":"Air","title":"Moon Safari"},"plays":9}],
             "artists":[{"name":"Air","plays":12,"albums":2}]}
        """.trimIndent())

        assertTrue(listening.tracking)
        assertEquals(12, listening.tracks)
        assertEquals(2, listening.albumCount)
        assertEquals(PlayedAlbum(Album("spotify:1", "Air", "Moon Safari", null, null), 9), listening.albums.single())
        assertEquals(ArtistPlays("Air", 12, 2), listening.artists.single())
    }

    @Test
    fun readsThatCountingIsOff() {
        val listening = Json.listening("""{"tracking":false,"days":28,"tracks":0,"albumCount":0}""")

        assertFalse(listening.tracking)
        assertTrue(listening.albums.isEmpty())
    }

    @Test
    fun readsTheLibraryWithoutARating() {
        val entry = Json.library("""
            [{"id":"a1","album":{"ref":"tidal:1","artist":"Joni Mitchell","title":"Blue"},"tags":["folk"],
              "addedOn":"2026-10-20"}]
        """.trimIndent()).single()

        assertNull(entry.rating)
        assertEquals(false, entry.general)
        assertEquals(listOf("folk"), entry.tags)
        assertEquals("", entry.notes)
    }

    @Test
    fun ratingsGoBothWaysAsOneToThree() {
        assertEquals(Rating.OK, Rating.of(2))
        assertNull(Rating.of(7))
        assertEquals(3, Rating.LOVE.value)
    }

    @Test
    fun readsWhetherPlaysAreCountedIgnoringPerActivitySettings() {
        val tracking = Json.tracking("""
            {"enabled":true,"categories":[{"categoryId":"c1","name":"Focus","enabled":false,"overridden":true}]}
        """.trimIndent())

        assertEquals(Tracking(enabled = true), tracking)
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
