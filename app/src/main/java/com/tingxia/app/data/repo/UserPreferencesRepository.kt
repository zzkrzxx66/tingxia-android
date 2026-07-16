package com.tingxia.app.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val DEFAULT_SPEED = floatPreferencesKey("default_speed")
    }

    /** null = follow system; true/false = force */
    val darkTheme: Flow<Boolean?> = context.dataStore.data.map { prefs ->
        if (prefs.contains(Keys.DARK_THEME)) prefs[Keys.DARK_THEME] else true // default dark-first
    }

    val defaultSpeed: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_SPEED] ?: 1.0f
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setDefaultSpeed(speed: Float) {
        context.dataStore.edit { it[Keys.DEFAULT_SPEED] = speed }
    }
}
