package com.disone.core.addons

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonSyntaxException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AddonParser @Inject constructor(
    private val gson: Gson
) {

    fun parseManifest(json: String): Result<AddonManifest> = runCatching {
        val manifest = gson.fromJson(json, AddonManifest::class.java)
        require(manifest.id.isNotBlank()) { "Manifest id required" }
        require(manifest.name.isNotBlank()) { "Manifest name required" }
        require(manifest.version.isNotBlank()) { "Manifest version required" }
        require(manifest.resources.isNotEmpty()) { "Manifest resources required" }
        require(manifest.types.isNotEmpty()) { "Manifest types required" }
        manifest
    }.recoverCatching { e ->
        when (e) {
            is JsonSyntaxException -> throw AddonParseException("Invalid manifest JSON", e)
            is IllegalArgumentException -> throw AddonParseException(e.message ?: "Invalid manifest", e)
            else -> throw AddonParseException("Failed to parse manifest", e)
        }
    }

    /**
     * Parses Stremio catalog response. Supports both:
     * - Standard: { "metas": [...] }
     * - Alternative: top-level array [...]
     */
    fun parseCatalogResponse(json: String): Result<AddonCatalogResponse> = runCatching {
        val element = gson.fromJson(json, JsonElement::class.java) ?: return@runCatching AddonCatalogResponse(null)
        val metas = when {
            element.isJsonObject && element.asJsonObject.has("metas") -> {
                gson.fromJson(element.asJsonObject.get("metas"), Array<AddonMetaPreview>::class.java)?.toList().orEmpty()
            }
            element.isJsonArray -> {
                gson.fromJson(element.asJsonArray, Array<AddonMetaPreview>::class.java)?.toList().orEmpty()
            }
            else -> {
                gson.fromJson(json, AddonCatalogResponse::class.java)?.metas.orEmpty()
            }
        }
        AddonCatalogResponse(metas.ifEmpty { null })
    }.recoverCatching { e ->
        throw AddonParseException("Invalid catalog response", e as? Exception ?: Exception(e))
    }

    fun parseMetaResponse(json: String): Result<AddonMetaResponse> = runCatching {
        gson.fromJson(json, AddonMetaResponse::class.java)
    }.recoverCatching { e ->
        throw AddonParseException("Invalid meta response", e as? Exception ?: Exception(e))
    }

    /**
     * Parses Stremio subtitles response. Standard: { "subtitles": [...] }
     */
    fun parseSubtitlesResponse(json: String): Result<AddonSubtitlesResponse> = runCatching {
        gson.fromJson(json, AddonSubtitlesResponse::class.java)
            ?: AddonSubtitlesResponse(emptyList())
    }.recoverCatching { e ->
        throw AddonParseException("Invalid subtitles response", e as? Exception ?: Exception(e))
    }

    /**
     * Parses Stremio stream response. Supports both:
     * - Standard: { "streams": [...] }
     * - Alternative: top-level array [...]
     */
    fun parseStreamResponse(json: String): Result<AddonStreamResponse> = runCatching {
        val element = gson.fromJson(json, JsonElement::class.java) ?: return@runCatching AddonStreamResponse(null)
        val streams = when {
            element.isJsonObject && element.asJsonObject.has("streams") -> {
                gson.fromJson(element.asJsonObject.get("streams"), Array<AddonStreamRaw>::class.java)?.toList().orEmpty()
            }
            element.isJsonArray -> {
                gson.fromJson(element.asJsonArray, Array<AddonStreamRaw>::class.java)?.toList().orEmpty()
            }
            else -> {
                gson.fromJson(json, AddonStreamResponse::class.java)?.streams.orEmpty()
            }
        }
        AddonStreamResponse(streams.ifEmpty { null })
    }.recoverCatching { e ->
        throw AddonParseException("Invalid stream response", e as? Exception ?: Exception(e))
    }
}

class AddonParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
