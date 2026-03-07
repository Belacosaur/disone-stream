package com.disone.core.catalog

import com.disone.core.addons.AddonMetaPreview
import com.disone.core.models.CatalogItem

fun AddonMetaPreview.toCatalogItem(): CatalogItem? {
    val id = effectiveId()
    if (id.isBlank()) return null
    return CatalogItem(
        id = "${type ?: "movie"}:$id",
        title = name ?: "",
        poster = poster,
        year = year?.toIntOrNull(),
        streams = emptyList()
    )
}
