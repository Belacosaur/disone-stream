package com.disone.core.subtitle

import java.io.File
import java.util.regex.Pattern

/**
 * Parsed subtitle cue: start/end in milliseconds, display text.
 * Used for custom overlay rendering synced with playback position.
 */
data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

/**
 * Simple SRT parser. Handles standard SRT format:
 * Index
 * HH:MM:SS,mmm --> HH:MM:SS,mmm
 * Text lines
 * [blank line]
 */
object SubtitleParser {

    // SRT and VTT both use HH:MM:SS,mmm or HH:MM:SS.mmm --> ...
    private val TIMECODE_PATTERN = Pattern.compile(
        "(\\d{2}):(\\d{2}):(\\d{2})[,.]?(\\d{0,3})\\s*-->\\s*(\\d{2}):(\\d{2}):(\\d{2})[,.]?(\\d{0,3})"
    )

    fun parse(file: File): List<SubtitleCue> = parse(file.readText(Charsets.UTF_8))

    fun parse(content: String): List<SubtitleCue> {
        // Strip WEBVTT header and BOM if present
        val cleaned = content
            .replace(Regex("^\\uFEFF"), "")
            .replace(Regex("^WEBVTT.*?\\n+", RegexOption.DOT_MATCHES_ALL), "")
        val cues = mutableListOf<SubtitleCue>()
        val blocks = cleaned.split(Regex("\\n\\s*\\n")).map { it.trim() }.filter { it.isNotEmpty() }

        for (block in blocks) {
            val lines = block.lines()
            if (lines.size < 2) continue

            val matcher = TIMECODE_PATTERN.matcher(lines[1])
            if (!matcher.find()) continue

            val startMs = parseTimecode(matcher.group(1)!!, matcher.group(2)!!, matcher.group(3)!!, matcher.group(4) ?: "0")
            val endMs = parseTimecode(matcher.group(5)!!, matcher.group(6)!!, matcher.group(7)!!, matcher.group(8) ?: "0")
            val text = lines.drop(2).joinToString("\n").trim()

            if (text.isNotEmpty()) {
                cues.add(SubtitleCue(startMs = startMs, endMs = endMs, text = text))
            }
        }
        return cues
    }

    private fun parseTimecode(h: String, m: String, s: String, ms: String): Long {
        val msVal = ms.padEnd(3, '0').take(3).toIntOrNull() ?: 0
        return h.toLongOrNull()!! * 3600000L +
                m.toLongOrNull()!! * 60000L +
                s.toLongOrNull()!! * 1000L +
                msVal
    }
}
