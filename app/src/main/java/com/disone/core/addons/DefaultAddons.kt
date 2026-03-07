package com.disone.core.addons

/**
 * Default Stremio addons for new users. Both are installed and enabled on first launch.
 * - Cinemeta: Catalog & metadata (IMDB, movies, series) - powers discovery page
 * - Torrentio Lite: Torrent streams (https://torrentio.strem.fun/lite/manifest.json)
 */
object DefaultAddons {
    val urls: List<String> = listOf(
        "https://v3-cinemeta.strem.io/",
        "https://torrentio.strem.fun/lite/manifest.json"
    )
}
