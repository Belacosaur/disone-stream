package com.disone.core.addons

import kotlinx.coroutines.flow.Flow
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
        addonsOverride: List<InstalledAddon>? = null
    ): Result<List<AddonMetaPreview>> =
        addonRepository.getCatalogFromAddons(type, catalogId, addonsOverride)

    suspend fun getMeta(addonUrl: String, type: String, id: String): Result<AddonMeta?> =
        addonRepository.getMeta(addonUrl, type, id)

    suspend fun getStreams(type: String, videoId: String, addonsOverride: List<InstalledAddon>? = null): Result<List<DisoneStream>> =
        addonRepository.getStreamsFromAllAddons(type, videoId, addonsOverride)
}
