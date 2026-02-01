package com.focusguard.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class PreferenceManager(private val context: Context) {
    
    companion object {
        val KEY_SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
    }
    
    val isSoundEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_SOUND_ENABLED] ?: false // Default to false (Vibrate only)
        }
    
    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SOUND_ENABLED] = enabled
        }
    }
}
