package com.disone.core.catalog

import com.disone.core.addons.AddonManager
import com.disone.core.models.CatalogItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogRepository @Inject constructor(
    private val addonManager: AddonManager
) {

    suspend fun fetchCatalog(
        type: String = "movie",
        catalogId: String = "top",
        extraParams: Map<String, String>? = null
    ): Result<List<CatalogItem>> {
        return addonManager.getAggregatedCatalog(type, catalogId, extraParams)
            .map { metas ->
                metas.mapNotNull { it.toCatalogItem() }
            }
    }
}
