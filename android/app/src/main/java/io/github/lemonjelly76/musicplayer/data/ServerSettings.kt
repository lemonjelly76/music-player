package io.github.lemonjelly76.musicplayer.data

import android.content.Context

data class ServerSettings(val serverUrl: String, val username: String, val password: String) {
    val complete: Boolean get() = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()

    /**
     * Trims what was typed and assumes HTTPS when no scheme is given: Android refuses plain HTTP, and the server is
     * reached through Tailscale Serve's HTTPS address.
     */
    fun normalised(): ServerSettings {
        val url = serverUrl.trim().trimEnd('/')
        val withScheme = if (url.isEmpty() || url.contains("://")) url else "https://$url"
        return ServerSettings(withScheme, username.trim(), password)
    }
}

/**
 * Where the server is, the household login and whose picks were last shown. A private APK on family phones, so
 * plain app-private preferences are enough.
 */
class SettingsStore(context: Context) {

    private val preferences = context.getSharedPreferences("music-player", Context.MODE_PRIVATE)

    fun server(): ServerSettings = ServerSettings(
        preferences.getString(SERVER_URL, "").orEmpty(),
        preferences.getString(USERNAME, "").orEmpty(),
        preferences.getString(PASSWORD, "").orEmpty(),
    )

    fun saveServer(settings: ServerSettings) {
        preferences.edit()
            .putString(SERVER_URL, settings.serverUrl)
            .putString(USERNAME, settings.username)
            .putString(PASSWORD, settings.password)
            .apply()
    }

    fun personId(): String? = preferences.getString(PERSON_ID, null)

    fun savePersonId(id: String) {
        preferences.edit().putString(PERSON_ID, id).apply()
    }

    private companion object {
        const val SERVER_URL = "serverUrl"
        const val USERNAME = "username"
        const val PASSWORD = "password"
        const val PERSON_ID = "personId"
    }
}
