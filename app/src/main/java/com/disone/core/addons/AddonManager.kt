package com.disone.core.addons

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * High-level addon lifecycle manager.
 * Orchestrates addon installation, catalog aggregation, and stream fetching.
 */
@Singleton
class AddonManager @Inject constructor(
    private val addonRepository: AddonRepository
) {

    val installedAddons: Flow<List<InstalledAddon>> = addonRepository.installedAddons

    /** Preferred catalog order for Discovery: Popular, New, Featured, then streaming platforms */
    private val catalogDisplayOrder = listOf("top", "year", "imdbRating", "nfx", "hbm", "dnp", "amp", "atp")

    suspend fun getManifestCatalogs(addonUrl: String? = null): Result<List<AddonCatalog>> {
        if (addonUrl != null) {
            return addonRepository.getManifest(addonUrl).map { it.catalogs }
        }
        val catalogAddons = addonRepository.installedAddons.first()
            .filter { it.enabled && it.manifest.resources.contains("catalog") }
        if (catalogAddons.isEmpty()) {
            return addonRepository.getManifest("https://v3-cinemeta.strem.io/").map { it.catalogs }
        }
        val allCatalogs = mutableListOf<AddonCatalog>()
        val seen = mutableSetOf<Pair<String, String>>()
        for (addon in catalogAddons) {
            val manifest = addonRepository.getManifest(addon.url).getOrNull() ?: continue
            val cats = manifest.catalogs + (manifest.addonCatalogs.orEmpty())
            cats.forEach { cat ->
                val key = cat.type to cat.id
                if (seen.add(key)) allCatalogs.add(cat)
            }
        }
        val sorted = allCatalogs.sortedWith(
            compareBy<AddonCatalog> { catalogDisplayOrder.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }
                .thenBy { it.name }
        )
        return Result.success(sorted)
    }

    suspend fun ensureDefaultAddons(): List<InstalledAddon> =
        addonRepository.ensureDefaultAddons()

    suspend fun addAddon(url: String): Result<InstalledAddon> =
        addonRepository.addAddonByUrl(url)

    suspend fun removeAddon(url: String) {
        addonRepository.removeAddon(url)
    }

    suspend fun setEnabled(url: String, enabled: Boolean) {
        addonRepository.setAddonEnabled(url, enabled)
    }

    suspend fun getAggregatedCatalog(
        type: String = "movie",
        catalogId: String = "top",
        extraParams: Map<String, String>? = null,
        addonsOverride: List<InstalledAddon>? = null
    ): Result<List<AddonMetaPreview>> =
        addonRepository.getCatalogFromAddons(type, catalogId, extraParams, addonsOverride)

    suspend fun getMeta(addonUrl: String, type: String, id: String): Result<AddonMeta?> =
        addonRepository.getMeta(addonUrl, type, id)

    suspend fun getStreams(type: String, videoId: String, addonsOverride: List<InstalledAddon>? = null): Result<List<DisoneStream>> =
        addonRepository.getStreamsFromAllAddons(type, videoId, addonsOverride)

    suspend fun getSubtitles(type: String, videoId: String): Result<List<AddonSubtitleRaw>> =
        addonRepository.getSubtitlesFromAddons(type, videoId)
}
