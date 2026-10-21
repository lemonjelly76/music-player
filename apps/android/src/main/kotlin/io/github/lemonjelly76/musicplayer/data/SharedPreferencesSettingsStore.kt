package io.github.lemonjelly76.musicplayer.data

import android.content.Context

/** A private APK on family phones, so plain app-private preferences are enough. */
class SharedPreferencesSettingsStore(context: Context) : SettingsStore {

    private val preferences = context.getSharedPreferences("music-player", Context.MODE_PRIVATE)

    override fun server(): ServerSettings = ServerSettings(
        preferences.getString(SERVER_URL, "").orEmpty(),
        preferences.getString(USERNAME, "").orEmpty(),
        preferences.getString(PASSWORD, "").orEmpty(),
    )

    override fun saveServer(settings: ServerSettings) {
        preferences.edit()
            .putString(SERVER_URL, settings.serverUrl)
            .putString(USERNAME, settings.username)
            .putString(PASSWORD, settings.password)
            .apply()
    }

    override fun personId(): String? = preferences.getString(PERSON_ID, null)

    override fun savePersonId(id: String) {
        preferences.edit().putString(PERSON_ID, id).apply()
    }

    private companion object {
        const val SERVER_URL = "serverUrl"
        const val USERNAME = "username"
        const val PASSWORD = "password"
        const val PERSON_ID = "personId"
    }
}
