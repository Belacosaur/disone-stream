package com.disone.core.addons

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.addonDataStore: DataStore<Preferences> by preferencesDataStore(name = "disone_addons")

@Singleton
class AddonStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {

    companion object {
        private const val ADDONS_JSON_KEY = "installed_addons"
    }

    val installedAddons: Flow<List<InstalledAddon>> = context.addonDataStore.data.map { prefs ->
        val json = prefs[stringPreferencesKey(ADDONS_JSON_KEY)] ?: "[]"
        parseAddonsJson(json)
    }

    suspend fun saveAddons(addons: List<InstalledAddon>) {
        context.addonDataStore.edit { prefs ->
            prefs[stringPreferencesKey(ADDONS_JSON_KEY)] = gson.toJson(
                addons.map { StorableAddon(it.url, gson.toJson(it.manifest), it.enabled) }
            )
        }
    }

    suspend fun addAddon(addon: InstalledAddon) {
        context.addonDataStore.edit { prefs ->
            val current = parseAddonsJson(prefs[stringPreferencesKey(ADDONS_JSON_KEY)] ?: "[]")
            val updated = current.filter { it.url != addon.url } + addon
            prefs[stringPreferencesKey(ADDONS_JSON_KEY)] = gson.toJson(
                updated.map { StorableAddon(it.url, gson.toJson(it.manifest), it.enabled) }
            )
        }
    }

    suspend fun removeAddon(url: String) {
        context.addonDataStore.edit { prefs ->
            val current = parseAddonsJson(prefs[stringPreferencesKey(ADDONS_JSON_KEY)] ?: "[]")
            val updated = current.filter { it.url != url }
            prefs[stringPreferencesKey(ADDONS_JSON_KEY)] = gson.toJson(
                updated.map { StorableAddon(it.url, gson.toJson(it.manifest), it.enabled) }
            )
        }
    }

    suspend fun setAddonEnabled(url: String, enabled: Boolean) {
        context.addonDataStore.edit { prefs ->
            val current = parseAddonsJson(prefs[stringPreferencesKey(ADDONS_JSON_KEY)] ?: "[]")
            val updated = current.map {
                if (it.url == url) it.copy(enabled = enabled) else it
            }
            prefs[stringPreferencesKey(ADDONS_JSON_KEY)] = gson.toJson(
                updated.map { StorableAddon(it.url, gson.toJson(it.manifest), it.enabled) }
            )
        }
    }

    private fun parseAddonsJson(json: String): List<InstalledAddon> = runCatching {
        val listType = object : com.google.gson.reflect.TypeToken<List<StorableAddon>>() {}.type
        val stored: List<StorableAddon> = gson.fromJson(json, listType) ?: emptyList()
        stored.map { s ->
            InstalledAddon(
                url = s.url,
                manifest = gson.fromJson(s.manifestJson, AddonManifest::class.java),
                enabled = s.enabled
            )
        }
    }.getOrElse { emptyList() }
}

private data class StorableAddon(
    val url: String,
    val manifestJson: String,
    val enabled: Boolean
)
