package com.disone.core.utils

/**
 * Parses an item ID that may include a type prefix (e.g. "movie:tt1234567").
 * @return Pair of (type, id). If no prefix, defaults to ("movie", id).
 */
fun parseItemId(itemId: String): Pair<String, String> {
    val idx = itemId.indexOf(':')
    return if (idx > 0) {
        itemId.substring(0, idx) to itemId.substring(idx + 1)
    } else {
        "movie" to itemId
    }
}

/**
 * Builds a combined item ID from type and id (e.g. "movie", "tt123" -> "movie:tt123").
 */
fun toItemId(type: String, id: String): String = "$type:$id"
