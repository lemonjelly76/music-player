package io.github.lemonjelly76.musicplayer.desktop

import io.github.lemonjelly76.musicplayer.data.ServerSettings
import io.github.lemonjelly76.musicplayer.data.SettingsStore
import java.util.prefs.Preferences

/**
 * Java's per-user preferences, which Windows keeps in the user's own registry hive; like the phone's app-private
 * storage, only that Windows user can read the login.
 */
class PreferencesSettingsStore(
    private val preferences: Preferences = Preferences.userRoot().node("io/github/lemonjelly76/music-player"),
) : SettingsStore {

    override fun server(): ServerSettings = ServerSettings(
        preferences.get(SERVER_URL, ""),
        preferences.get(USERNAME, ""),
        preferences.get(PASSWORD, ""),
    )

    override fun saveServer(settings: ServerSettings) {
        preferences.put(SERVER_URL, settings.serverUrl)
        preferences.put(USERNAME, settings.username)
        preferences.put(PASSWORD, settings.password)
        preferences.flush()
    }

    override fun personId(): String? = preferences.get(PERSON_ID, null)

    override fun savePersonId(id: String) {
        preferences.put(PERSON_ID, id)
        preferences.flush()
    }

    private companion object {
        const val SERVER_URL = "serverUrl"
        const val USERNAME = "username"
        const val PASSWORD = "password"
        const val PERSON_ID = "personId"
    }
}
