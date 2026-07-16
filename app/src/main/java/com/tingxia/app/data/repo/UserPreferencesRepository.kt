package com.tingxia.app.data.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tingxia.app.data.model.ShelfFilter
import com.tingxia.app.data.model.ShelfSort
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
        val SHELF_SORT = stringPreferencesKey("shelf_sort")
        val SHELF_FILTER = stringPreferencesKey("shelf_filter")
    }

    val darkTheme: Flow<Boolean?> = context.dataStore.data.map { prefs ->
        if (prefs.contains(Keys.DARK_THEME)) prefs[Keys.DARK_THEME] else true
    }

    val defaultSpeed: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_SPEED] ?: 1.0f
    }

    val shelfSort: Flow<ShelfSort> = context.dataStore.data.map { prefs ->
        runCatching { ShelfSort.valueOf(prefs[Keys.SHELF_SORT] ?: ShelfSort.RECENT.name) }
            .getOrDefault(ShelfSort.RECENT)
    }

    val shelfFilter: Flow<ShelfFilter> = context.dataStore.data.map { prefs ->
        runCatching { ShelfFilter.valueOf(prefs[Keys.SHELF_FILTER] ?: ShelfFilter.ALL.name) }
            .getOrDefault(ShelfFilter.ALL)
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = enabled }
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
