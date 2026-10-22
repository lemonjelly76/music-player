package io.github.lemonjelly76.musicplayer.desktop

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Turns Spotify's web links into the `spotify:` links its desktop app answers, so albums and playlists open there
 * rather than in the web player; without the app installed they stay web links.
 */
class AppLinks(private val spotifyInstalled: () -> Boolean = ::spotifyOnWindows) {

    private val installed by lazy(spotifyInstalled)

    fun forThisComputer(url: String): String {
        val match = SPOTIFY_PAGE.matchEntire(url) ?: return url
        if (!installed) return url
        val (kind, id) = match.destructured
        return "spotify:$kind:$id"
    }

    private companion object {
        val SPOTIFY_PAGE = Regex("https://open\\.spotify\\.com/(album|track|playlist|artist|search)/([^?#]+)(?:[?#].*)?")
    }
}

/**
 * Whether Windows knows the `spotify:` link, which the Spotify installer and the Microsoft Store app both register;
 * the folders each one installs to are checked too, as the Store app's registration is not in the usual place.
 */
private fun spotifyOnWindows(): Boolean {
    if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return false
    val appData = System.getenv("APPDATA")?.let { File(it, "Spotify/Spotify.exe") }
    val storeApps = System.getenv("LOCALAPPDATA")?.let { File(it, "Packages") }
        ?.listFiles { file -> file.name.startsWith("SpotifyAB.SpotifyMusic") }
    return appData?.exists() == true || !storeApps.isNullOrEmpty() || protocolRegistered()
}

private fun protocolRegistered(): Boolean = try {
    val query = ProcessBuilder("reg", "query", "HKCR\\spotify").redirectErrorStream(true).start()
    query.inputStream.readAllBytes()
    query.waitFor(5, TimeUnit.SECONDS) && query.exitValue() == 0
} catch (e: Exception) {
    false
}
