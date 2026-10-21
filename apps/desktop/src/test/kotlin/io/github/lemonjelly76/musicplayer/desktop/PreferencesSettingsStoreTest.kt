package io.github.lemonjelly76.musicplayer.desktop

import io.github.lemonjelly76.musicplayer.data.ServerSettings
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PreferencesSettingsStoreTest {

    private val node = Preferences.userRoot().node("music-player-test-${System.nanoTime()}")

    @AfterTest
    fun removeNode() = node.removeNode()

    @Test
    fun startsEmptySoSettingsOpenFirst() {
        val store = PreferencesSettingsStore(node)

        assertEquals(ServerSettings("", "", ""), store.server())
        assertNull(store.personId())
    }

    @Test
    fun remembersTheServerAndPersonAcrossRestarts() {
        PreferencesSettingsStore(node).apply {
            saveServer(ServerSettings("https://pc.tailnet.ts.net:8444", "jelly", "secret"))
            savePersonId("p1")
        }

        val reopened = PreferencesSettingsStore(node)
        assertEquals(ServerSettings("https://pc.tailnet.ts.net:8444", "jelly", "secret"), reopened.server())
        assertEquals("p1", reopened.personId())
    }
}
