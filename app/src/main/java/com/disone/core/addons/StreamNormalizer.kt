package com.disone.core.addons

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Normalizes addon-provided streams into DisoneStream.
 * Parses seeders and size from Torrentio-style title/description (👤 N 💾 X GB).
 * Rules:
 * - If infoHash: construct magnet:?xt=urn:btih:<infoHash>
 * - If url: treat as hosted candidate (backend decides)
 * - All streams MUST go through backend /play - never trust addon URLs directly
 */
@Singleton
class StreamNormalizer @Inject constructor() {

    fun normalize(raw: AddonStreamRaw, sourceAddonUrl: String? = null): DisoneStream? {
        val magnet = when {
            raw.infoHash != null -> buildMagnet(raw.infoHash)
            else -> raw.url?.takeIf { it.startsWith("magnet:") }
        }

        val url = when {
            raw.url != null && !raw.url.startsWith("magnet:") -> raw.url
            raw.externalUrl != null -> raw.externalUrl
            else -> null
        }

        if (magnet == null && url == null && raw.ytId == null) {
            return null
        }

        val desc = raw.title ?: raw.description ?: ""
        val (seeders, sizeBytes) = parseStreamInfo(desc)

        return DisoneStream(
            title = raw.name ?: raw.title ?: raw.description ?: "Stream",
            infoHash = raw.infoHash,
            magnet = magnet,
            url = if (raw.ytId != null) null else url,
            fileIdx = raw.fileIdx,
            quality = raw.name ?: raw.title,
            seeders = seeders,
            sizeBytes = sizeBytes,
            sourceAddonUrl = sourceAddonUrl
        )
    }

    fun normalizeAll(rawStreams: List<AddonStreamRaw>, sourceAddonUrl: String? = null): List<DisoneStream> {
        return rawStreams.mapNotNull { normalize(it, sourceAddonUrl) }
    }

    private fun buildMagnet(infoHash: String): String {
        val hash = infoHash.trim().lowercase()
        return if (hash.length == 40) {
            "magnet:?xt=urn:btih:$hash"
        } else {
            "magnet:?xt=urn:btih:$infoHash"
        }
    }

    /**
     * Parse seeders and size from Torrentio-style description.
     * Format: "👤 34 💾 48.1 GB ⚙️ 1337x" or "Seeds: 123 Size: 2.5 GB"
     */
    private fun parseStreamInfo(desc: String?): Pair<Int?, Long?> {
        if (desc.isNullOrBlank()) return null to null
        var seeders: Int? = null
        var sizeBytes: Long? = null

        // Seeders: 👤 N, ⬆️ N, Seeds: N, S: N
        Regex("""(?:👤|⬆️|↑)\s*(\d+)""").find(desc)?.groupValues?.get(1)?.toIntOrNull()?.let { seeders = it }
        if (seeders == null) {
            Regex("""[Ss]eeds?[:\s]+(\d+)""", RegexOption.IGNORE_CASE).find(desc)?.groupValues?.get(1)?.toIntOrNull()?.let { seeders = it }
        }

        // Size: 💾 X GB/MB/KB
        val sizeMatch = Regex("""(?:💾\s*)?([\d.]+)\s*(GB|MB|KB|TB)""", RegexOption.IGNORE_CASE).find(desc)
        if (sizeMatch != null) {
            val value = sizeMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            val unit = sizeMatch.groupValues[2].uppercase()
            sizeBytes = when (unit) {
                "TB" -> (value * 1024 * 1024 * 1024 * 1024).toLong()
                "GB" -> (value * 1024 * 1024 * 1024).toLong()
                "MB" -> (value * 1024 * 1024).toLong()
                "KB" -> (value * 1024).toLong()
                else -> value.toLong()
            }
        }

        return seeders to sizeBytes
    }
}
