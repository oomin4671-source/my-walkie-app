package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.walkieDataStore: DataStore<Preferences> by preferencesDataStore(name = "my_walkie_prefs")

class PreferencesStore(context: Context) {
    private val dataStore = context.applicationContext.walkieDataStore

    private object Keys {
        val BACKEND_URL = stringPreferencesKey("backend_url")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val SPEAKERPHONE_ENABLED = booleanPreferencesKey("speakerphone_enabled")
    }

    val backendUrlFlow: Flow<String> = dataStore.data.map { prefs ->
        val saved = normalizeBackendBaseUrl(prefs[Keys.BACKEND_URL].orEmpty())
        saved.ifEmpty { normalizeBackendBaseUrl(BuildConfig.WALKIE_BACKEND_URL) }
    }

    val hapticsEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.HAPTICS_ENABLED] ?: true
    }

    val speakerphoneEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.SPEAKERPHONE_ENABLED] ?: true
    }

    suspend fun setBackendUrl(url: String) {
        dataStore.edit { prefs ->
            prefs[Keys.BACKEND_URL] = normalizeBackendBaseUrl(url)
        }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.HAPTICS_ENABLED] = enabled
        }
    }

    suspend fun setSpeakerphoneEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SPEAKERPHONE_ENABLED] = enabled
        }
    }
}
