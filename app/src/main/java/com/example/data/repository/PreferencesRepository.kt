package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "notifyr_settings")

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class UserPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val autoMarkReadOnOpen: Boolean = true,
    val autoCleanExpired: Boolean = false
)

class PreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val AUTO_MARK_READ = booleanPreferencesKey("auto_mark_read")
        val AUTO_CLEAN_EXPIRED = booleanPreferencesKey("auto_clean_expired")
    }

    val userPreferences: Flow<UserPreferences> = context.dataStore.data.map { preferences ->
        val themeModeStr = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode = try {
            ThemeMode.valueOf(themeModeStr)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
        val sound = preferences[PreferencesKeys.SOUND_ENABLED] ?: true
        val vibration = preferences[PreferencesKeys.VIBRATION_ENABLED] ?: true
        val autoMarkRead = preferences[PreferencesKeys.AUTO_MARK_READ] ?: true
        val autoCleanExpired = preferences[PreferencesKeys.AUTO_CLEAN_EXPIRED] ?: false

        UserPreferences(
            themeMode = themeMode,
            soundEnabled = sound,
            vibrationEnabled = vibration,
            autoMarkReadOnOpen = autoMarkRead,
            autoCleanExpired = autoCleanExpired
        )
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = themeMode.name
        }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SOUND_ENABLED] = enabled
        }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.VIBRATION_ENABLED] = enabled
        }
    }

    suspend fun setAutoMarkRead(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_MARK_READ] = enabled
        }
    }

    suspend fun setAutoCleanExpired(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_CLEAN_EXPIRED] = enabled
        }
    }
}
