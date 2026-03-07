package com.disone.core.addons

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import java.lang.reflect.Type

/** Deserializes Stremio resources: ["stream"] or [{name:"stream",...}] */
private class ResourcesDeserializer : JsonDeserializer<List<String>> {
    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): List<String> {
        if (!json.isJsonArray) return emptyList()
        return json.asJsonArray.mapNotNull { el ->
            when {
                el.isJsonPrimitive && el.asJsonPrimitive.isString -> el.asString
                el.isJsonObject && el.asJsonObject.has("name") -> el.asJsonObject.get("name")?.let { if (it.isJsonPrimitive) it.asString else null }
                else -> null
            }
        }
    }
}

// Stremio addon manifest
data class AddonManifest(
    val id: String,
    val version: String,
    val name: String,
    val description: String? = null,
    @JsonAdapter(ResourcesDeserializer::class) val resources: List<String>,
    val types: List<String>,
    val catalogs: List<AddonCatalog> = emptyList(),
    @SerializedName("addonCatalogs") val addonCatalogs: List<AddonCatalog>? = null
)

data class AddonCatalog(
    val type: String,
    val id: String,
    val name: String,
    val extra: List<AddonCatalogExtra>? = null
)

data class AddonCatalogExtra(
    val name: String,
    @SerializedName("isRequired") val isRequired: Boolean? = false,
    val options: List<String>? = null
)

// Stremio catalog response - array of Meta Preview
// GET {addonUrl}/catalog/{type}/{id}.json or /catalog/{type}/{id}/{extra}.json
data class AddonCatalogResponse(
    val metas: List<AddonMetaPreview>? = null
)

data class AddonMetaPreview(
    val id: String? = null,
    @SerializedName("imdb_id") val imdbId: String? = null,
    val type: String? = "movie",
    val name: String? = "",
    val poster: String? = null,
    @SerializedName("posterShape") val posterShape: String? = null,
    @SerializedName("releaseInfo") val releaseInfo: String? = null,
    val year: String? = null,
    @SerializedName("imdbRating") val imdbRating: String? = null
) {
    fun effectiveId(): String = id ?: imdbId ?: ""
}

// Stremio meta response - full metadata
// GET {addonUrl}/meta/{type}/{id}.json
data class AddonMetaResponse(
    val meta: AddonMeta? = null
)

data class AddonMeta(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val description: String? = null,
    @SerializedName("releaseInfo") val releaseInfo: String? = null,
    val videos: List<AddonVideo>? = null
)

data class AddonVideo(
    val id: String,
    val title: String,
    @SerializedName("released") val released: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val streams: List<AddonStreamRaw>? = null
)

// Stremio stream response - array of stream objects
// GET {addonUrl}/stream/{type}/{videoId}.json
data class AddonStreamResponse(
    val streams: List<AddonStreamRaw>? = null
)

// Raw addon stream - Stremio format (supports both camelCase and snake_case from addons)
data class AddonStreamRaw(
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
    @SerializedName("infoHash") val infoHash: String? = null,
    @SerializedName("fileIdx") val fileIdx: Int? = null,
    val url: String? = null,
    @SerializedName("externalUrl") val externalUrl: String? = null,
    @SerializedName(value = "ytId", alternate = ["yt_id"]) val ytId: String? = null
)

// Normalized internal model - all streams route through backend
data class DisoneStream(
    val title: String,
    val infoHash: String? = null,
    val magnet: String? = null,
    val url: String? = null,
    val fileIdx: Int? = null,
    val quality: String? = null,
    val seeders: Int? = null,
    val sizeBytes: Long? = null,
    val sourceAddonUrl: String? = null
)

// Persisted installed addon
data class InstalledAddon(
    val url: String,
    val manifest: AddonManifest,
    val enabled: Boolean = true,
    @Transient val addedAt: Long = System.currentTimeMillis()
)
