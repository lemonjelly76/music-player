package io.github.lemonjelly76.musicplayer.data

/** The Tidal app's package on Android, so its links can be handed to it rather than to the browser. */
const val TIDAL_PACKAGE = "com.aspiro.tidal"

private val TIDAL_PAGE =
    Regex("https://(?:tidal\\.com/browse|listen\\.tidal\\.com)/(album|track|playlist|artist)/([^?#/]+).*")
private val TIDAL_SITE = Regex("https://(?:tidal\\.com|listen\\.tidal\\.com)/.*")

/**
 * The forms of a Tidal link to offer the Tidal app, most likely first: the link itself, the web player's form and
 * the app's own scheme. Android only opens a web link in an app the person has allowed to open them, so each is
 * tried with the app named; a link that is not Tidal's gives none.
 */
fun tidalAppLinks(url: String): List<String> {
    val page = TIDAL_PAGE.matchEntire(url)
    if (page == null) return if (TIDAL_SITE.matches(url)) listOf(url) else emptyList()
    val (kind, id) = page.destructured
    return listOf(url, "https://listen.tidal.com/$kind/$id", "tidal://$kind/$id").distinct()
}
