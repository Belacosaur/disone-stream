package com.disone.core.addons

import com.disone.core.api.AddAddonRequest
import com.disone.core.api.AddonApi
import com.disone.core.api.SetAddonEnabledRequest
import com.disone.core.storage.SecureTokenStorage
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddonRepository @Inject constructor(
    private val addonService: AddonService,
    private val addonParser: AddonParser,
    private val streamNormalizer: StreamNormalizer,
    private val addonStorage: AddonStorage,
    private val addonApi: AddonApi,
    private val tokenStorage: SecureTokenStorage,
    private val gson: Gson
) {

    private val manifestCache = mutableMapOf<String, CachedManifest>()
    private val cacheMutex = Mutex()
    private val ensureDefaultMutex = Mutex()

    val installedAddons: Flow<List<InstalledAddon>> = addonStorage.installedAddons

    /**
     * Ensures Cinemeta and Torrentio are installed. Returns the current addon list
     * (after ensuring) so callers get fresh data without Flow/DataStore race.
     * Mutex prevents concurrent init (e.g. AuthRepository + DiscoveryScreen).
     */
    suspend fun ensureDefaultAddons(): List<InstalledAddon> = ensureDefaultMutex.withLock {
        var current = addonStorage.installedAddons.first()
        if (current.isEmpty()) {
            DefaultAddons.urls.forEach { url ->
                runCatching { addAddonByUrl(url) }.onFailure {
                    kotlinx.coroutines.delay(500)
                    runCatching { addAddonByUrl(url) }
                }
            }
            kotlinx.coroutines.delay(100)
            current = addonStorage.installedAddons.first()
        }
        return current
    }

    suspend fun syncFromServer() {
        val auth = tokenStorage.getToken()?.let { "Bearer $it" } ?: return
        runCatching {
            val resp = addonApi.getAddons(auth)
            if (resp.isSuccessful) {
                val body = resp.body()
                if (body != null) {
                    if (body.addons.isNotEmpty()) {
                        val serverAddons = body.addons.map { r ->
                            InstalledAddon(
                                url = r.url,
                                manifest = gson.fromJson(gson.toJson(r.manifest), AddonManifest::class.java),
                                enabled = r.enabled
                            )
                        }
                        // Ensure default addons (Cinemeta, Torrentio) are never lost after sync
                        val merged = mergeWithDefaults(serverAddons)
                        addonStorage.saveAddons(merged)
                        val serverBases = serverAddons.map { normalizeUrlForCompare(it.url) }.toSet()
                        merged.filter { normalizeUrlForCompare(it.url) !in serverBases }
                            .forEach { pushAddonToServer(it) }
                    } else {
                        val local = addonStorage.installedAddons.first()
                        local.forEach { pushAddonToServer(it) }
                    }
                }
            }
        }
    }

    private suspend fun mergeWithDefaults(serverAddons: List<InstalledAddon>): List<InstalledAddon> {
        val byBaseUrl = serverAddons.associateBy { normalizeUrlForCompare(it.url) }.toMutableMap()
        for (defaultUrl in DefaultAddons.urls) {
            val base = normalizeUrlForCompare(defaultUrl)
            if (base !in byBaseUrl) {
                addAddonByUrl(defaultUrl).getOrNull()?.let { addon ->
                    byBaseUrl[base] = addon
                }
            }
        }
        return byBaseUrl.values.toList()
    }

    private fun normalizeUrlForCompare(url: String): String {
        val u = url.trim().removeSuffix("/")
        return when {
            u.endsWith("/manifest.json") -> u.removeSuffix("manifest.json").trimEnd('/')
            else -> u
        }
    }

    suspend fun getManifest(addonBaseUrl: String): Result<AddonManifest> {
        return addonService.fetchManifest(addonBaseUrl).fold(
            onSuccess = { json -> addonParser.parseManifest(json) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun addAddonByUrl(addonUrl: String): Result<InstalledAddon> {
        val normalizedUrl = addonUrl.trim().let {
            if (!it.startsWith("http")) "https://$it" else it
        }.removeSuffix("/").let { base ->
            if (base.endsWith("/manifest.json")) base.removeSuffix("manifest.json").let { s ->
                if (s.endsWith("/")) s else "$s/"
            }
            else "$base/"
        }

        val manifestResult = addonService.fetchManifest(normalizedUrl).fold(
            onSuccess = { json -> addonParser.parseManifest(json) },
            onFailure = { Result.failure<AddonManifest>(it) }
        )

        val addon = manifestResult.getOrNull()?.let { manifest ->
            InstalledAddon(url = normalizedUrl, manifest = manifest, enabled = true)
        }
        return if (addon != null) {
            addonStorage.addAddon(addon)
            pushAddonToServer(addon)
            Result.success(addon)
        } else {
            Result.failure(manifestResult.exceptionOrNull() ?: Exception("Unknown error"))
        }
    }

    suspend fun removeAddon(url: String) {
        addonStorage.removeAddon(url)
        pushRemoveToServer(url)
        cacheMutex.withLock { manifestCache.remove(url) }
    }

    suspend fun setAddonEnabled(url: String, enabled: Boolean) {
        addonStorage.setAddonEnabled(url, enabled)
        pushSetEnabledToServer(url, enabled)
    }

    private suspend fun pushAddonToServer(addon: InstalledAddon) {
        val auth = tokenStorage.getToken()?.let { "Bearer $it" } ?: return
        runCatching {
            @Suppress("UNCHECKED_CAST")
            val manifestMap = gson.fromJson<Map<String, Any?>>(
                gson.toJson(addon.manifest),
                Map::class.java
            ) as Map<String, Any?>
            addonApi.addAddon(auth, AddAddonRequest(addon.url, manifestMap, addon.enabled))
        }
    }

    private suspend fun pushRemoveToServer(url: String) {
        val auth = tokenStorage.getToken()?.let { "Bearer $it" } ?: return
        runCatching { addonApi.removeAddon(auth, url) }
    }

    private suspend fun pushSetEnabledToServer(url: String, enabled: Boolean) {
        val auth = tokenStorage.getToken()?.let { "Bearer $it" } ?: return
        runCatching { addonApi.setAddonEnabled(auth, SetAddonEnabledRequest(url, enabled)) }
    }

    /** Streaming platform catalog IDs - only fetch from addons that declare them (not Cinemeta) */
    private val STREAMING_CATALOG_IDS = setOf("nfx", "hbm", "dnp", "amp", "atp")

    /** Effective catalogs = manifest.catalogs + addonCatalogs (some addons use addonCatalogs) */
    private fun InstalledAddon.effectiveCatalogs(): List<AddonCatalog> =
        manifest.catalogs + (manifest.addonCatalogs.orEmpty())

    suspend fun getCatalogFromAddons(
        type: String = "movie",
        catalogId: String = "top",
        extraParams: Map<String, String>? = null,
        addonsOverride: List<InstalledAddon>? = null
    ): Result<List<AddonMetaPreview>> {
        val addons = addonsOverride ?: addonStorage.installedAddons.first()
        val enabled = addons.filter { it.enabled && it.manifest.resources.contains("catalog") }
        val urlsToTry = when {
            // Streaming catalogs FIRST: Cinemeta returns the SAME "popular" list for nfx/hbm/dnp/amp/atp (unknown IDs).
            // Never use Cinemeta for these - always use the Streaming Catalogs addon.
            catalogId in STREAMING_CATALOG_IDS -> {
                val fromInstalled = enabled
                    .filter { it.effectiveCatalogs().any { c -> c.type == type && c.id == catalogId } }
                    .map { it.url }
                (fromInstalled + DefaultAddons.streamingCatalogsUrl).distinct()
            }
            enabled.isEmpty() -> listOf(DefaultAddons.urls.first { it.contains("cinemeta") })
            else -> enabled.map { it.url }
        }

        val allMetas = mutableListOf<AddonMetaPreview>()
        val seenIds = mutableSetOf<String>()

        for (addonUrl in urlsToTry) {
            val result = addonService.fetchCatalog(addonUrl, type, catalogId, extraParams = extraParams).fold(
                onSuccess = { json -> addonParser.parseCatalogResponse(json) },
                onFailure = { Result.failure<AddonCatalogResponse>(it) }
            )
            result.getOrNull()?.metas?.orEmpty()?.forEach { meta ->
                val id = meta.effectiveId()
                if (id.isNotBlank() && seenIds.add(id)) {
                    allMetas.add(meta)
                }
            }
        }

        return Result.success(allMetas)
    }

    suspend fun getMeta(addonUrl: String, type: String, id: String): Result<AddonMeta?> {
        val result = addonService.fetchMeta(addonUrl, type, id).fold(
            onSuccess = { json -> addonParser.parseMetaResponse(json) },
            onFailure = { Result.failure<AddonMetaResponse>(it) }
        )
        return result.map { resp -> resp.meta }
    }

    suspend fun getStreams(addonUrl: String, type: String, videoId: String): Result<List<DisoneStream>> {
        val result = addonService.fetchStreams(addonUrl, type, videoId).fold(
            onSuccess = { json -> addonParser.parseStreamResponse(json) },
            onFailure = { Result.failure<AddonStreamResponse>(it) }
        )
        return result.map { response ->
            streamNormalizer.normalizeAll(response.streams.orEmpty(), addonUrl)
        }
    }

    suspend fun getStreamsFromAllAddons(
        type: String,
        videoId: String,
        addonsOverride: List<InstalledAddon>? = null
    ): Result<List<DisoneStream>> {
        val addons = addonsOverride ?: addonStorage.installedAddons.first()
        val streamAddons = addons.filter { it.enabled && it.manifest.resources.contains("stream") }
        if (streamAddons.isEmpty()) return Result.success(emptyList())

        val allStreams = mutableListOf<DisoneStream>()
        val seenKeys = mutableSetOf<String>()

        for (addon in streamAddons) {
            if (!addon.manifest.types.contains(type)) continue
            val result = getStreams(addon.url, type, videoId)
            result.getOrNull()?.forEach { stream ->
                val key = "${stream.infoHash ?: stream.url ?: stream.title}"
                if (seenKeys.add(key)) {
                    allStreams.add(stream)
                }
            }
        }

        return Result.success(allStreams)
    }

    /**
     * Fetches subtitles from all addons that expose the "subtitles" resource.
     * Falls back to OpenSubtitles v3 directly when no addon has subtitles (e.g. fresh install).
     * Merges results; first addon to return subtitles wins per language (dedup by lang).
     */
    suspend fun getSubtitlesFromAddons(
        type: String,
        videoId: String,
        addonsOverride: List<InstalledAddon>? = null
    ): Result<List<AddonSubtitleRaw>> {
        val addons = addonsOverride ?: addonStorage.installedAddons.first()
        val subtitleAddons = addons.filter { it.enabled && it.manifest.resources.contains("subtitles") }

        val allSubtitles = mutableListOf<AddonSubtitleRaw>()
        val seenUrls = mutableSetOf<String>()

        val addonsToTry = if (subtitleAddons.isEmpty()) {
            listOf(DefaultAddons.urls.find { it.contains("opensubtitles") } ?: "https://opensubtitles-v3.strem.io/")
                .map { InstalledAddon(url = it, manifest = AddonManifest(id = "fallback", version = "1", name = "OpenSubtitles", resources = listOf("subtitles"), types = listOf("movie", "series")), enabled = true) }
        } else subtitleAddons

        for (addon in addonsToTry) {
            if (!addon.manifest.types.contains(type)) continue
            val result = addonService.fetchSubtitles(addon.url, type, videoId).fold(
                onSuccess = { json -> addonParser.parseSubtitlesResponse(json) },
                onFailure = { Result.failure<AddonSubtitlesResponse>(it) }
            )
            result.getOrNull()?.subtitles?.orEmpty()?.forEach { sub ->
                if (sub.url != null && seenUrls.add(sub.url)) {
                    allSubtitles.add(sub)
                }
            }
        }
        return Result.success(allSubtitles)
    }
}

private data class CachedManifest(val manifest: AddonManifest, val expiresAt: Long)