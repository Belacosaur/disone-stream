package com.disone.core.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "disone_prefs")

@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val MAX_CONCURRENT_TORRENTS = intPreferencesKey("max_concurrent_torrents")
        val PIN_TORRENT_ON_EXIT = booleanPreferencesKey("pin_torrent_on_exit")
    }

    val wifiOnly: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.WIFI_ONLY] ?: false
    }

    val maxConcurrentTorrents: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.MAX_CONCURRENT_TORRENTS] ?: 2
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WIFI_ONLY] = enabled }
    }

    suspend fun setMaxConcurrentTorrents(count: Int) {
        context.dataStore.edit { it[Keys.MAX_CONCURRENT_TORRENTS] = count.coerceIn(1, 2) }
    }
}
