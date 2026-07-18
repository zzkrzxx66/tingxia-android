package com.tingxia.app.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tingxia.app.data.model.ShelfFilter
import com.tingxia.app.data.model.ShelfSort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val preferencesFlow = context.dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    private object Keys {
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_SPEED = floatPreferencesKey("default_speed")
        val SHELF_SORT = stringPreferencesKey("shelf_sort")
        val SHELF_FILTER = stringPreferencesKey("shelf_filter")
    }

    val themeMode: Flow<ThemeMode> = preferencesFlow.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { stored ->
            runCatching { ThemeMode.valueOf(stored) }.getOrNull()
        } ?: if (prefs.contains(Keys.DARK_THEME)) {
            if (prefs[Keys.DARK_THEME] == true) ThemeMode.DARK else ThemeMode.LIGHT
        } else {
            ThemeMode.SYSTEM
        }
    }

    val defaultSpeed: Flow<Float> = preferencesFlow.map { prefs ->
        prefs[Keys.DEFAULT_SPEED] ?: 1.0f
    }

    val shelfSort: Flow<ShelfSort> = preferencesFlow.map { prefs ->
        runCatching { ShelfSort.valueOf(prefs[Keys.SHELF_SORT] ?: ShelfSort.RECENT.name) }
            .getOrDefault(ShelfSort.RECENT)
    }

    val shelfFilter: Flow<ShelfFilter> = preferencesFlow.map { prefs ->
        runCatching { ShelfFilter.valueOf(prefs[Keys.SHELF_FILTER] ?: ShelfFilter.ALL.name) }
            .getOrDefault(ShelfFilter.ALL)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit {
            it[Keys.THEME_MODE] = mode.name
            it.remove(Keys.DARK_THEME)
        }
    }

    suspend fun setDefaultSpeed(speed: Float) {
        context.dataStore.edit { it[Keys.DEFAULT_SPEED] = speed }
    }

    suspend fun setShelfSort(sort: ShelfSort) {
        context.dataStore.edit { it[Keys.SHELF_SORT] = sort.name }
    }

    suspend fun setShelfFilter(filter: ShelfFilter) {
        context.dataStore.edit { it[Keys.SHELF_FILTER] = filter.name }
    }
}
