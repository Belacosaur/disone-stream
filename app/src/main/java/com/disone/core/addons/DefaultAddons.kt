package com.disone.core.addons

/**
 * Default Stremio addons for new users. Installed and enabled on first launch.
 * - Cinemeta: Catalog & metadata (Popular, New, Featured, genres)
 * - Streaming Catalogs: Netflix (nfx), HBO Max (hbm), Disney+ (dnp), Prime Video (amp), Apple TV+ (atp).
 *   Uses Stremio's Streaming Catalogs addon; configure at /configure to enable all 5 platforms.
 * - Torrentio Lite: Torrent streams
 * - OpenSubtitles v3: Subtitle provider for movies and series
 */
object DefaultAddons {
    /** Cinemeta - metadata (poster, background, logo) for movies/series with IMDB IDs */
    val cinemetaUrl: String = "https://v3-cinemeta.strem.io/"

    /** Hardcoded Streaming Catalogs addon - Netflix, HBO Max, Disney+, Prime Video, Apple TV+. Used for Featured page rows. */
    val streamingCatalogsUrl: String = "https://7a82163c306e-stremio-netflix-catalog-addon.baby-beamup.club/"

    val urls: List<String> = listOf(
        cinemetaUrl,
        streamingCatalogsUrl,
        "https://torrentio.strem.fun/lite/manifest.json",
        "https://opensubtitles-v3.strem.io/"
    )
}
