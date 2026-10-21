package io.github.lemonjelly76.musicplayer.data

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

/** Where the server is, the household login and whose picks were last shown; each app keeps them its own way. */
interface SettingsStore {
    fun server(): ServerSettings
    fun saveServer(settings: ServerSettings)
    fun personId(): String?
    fun savePersonId(id: String)
}
