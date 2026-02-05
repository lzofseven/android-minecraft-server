package com.lzofseven.mcserver.core.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class ApiKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val API_KEY = stringPreferencesKey("gemini_api_key")

    val apiKeyFlow: Flow<String?> = context.dataStore.data
        .map { preferences ->
            preferences[API_KEY]
        }

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { settings ->
            settings[API_KEY] = key
        }
    }

    suspend fun clearApiKey() {
        context.dataStore.edit { settings ->
            settings.remove(API_KEY)
        }
    }
}
