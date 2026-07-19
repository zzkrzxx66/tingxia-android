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
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class PlaybackErrorPolicy { STOP, SKIP }

data class PreferencesSnapshot(
    val themeMode: ThemeMode,
    val defaultSpeed: Float,
    val shelfSort: ShelfSort,
    val shelfFilter: ShelfFilter,
    val playbackErrorPolicy: PlaybackErrorPolicy,
)

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
        val PLAYBACK_ERROR_POLICY = stringPreferencesKey("playback_error_policy")
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

    val playbackErrorPolicy: Flow<PlaybackErrorPolicy> = preferencesFlow.map { prefs ->
        runCatching {
            PlaybackErrorPolicy.valueOf(
                prefs[Keys.PLAYBACK_ERROR_POLICY] ?: PlaybackErrorPolicy.STOP.name,
            )
        }.getOrDefault(PlaybackErrorPolicy.STOP)
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

    suspend fun setPlaybackErrorPolicy(policy: PlaybackErrorPolicy) {
        context.dataStore.edit { it[Keys.PLAYBACK_ERROR_POLICY] = policy.name }
    }

    suspend fun snapshot(): PreferencesSnapshot = PreferencesSnapshot(
        themeMode = themeMode.first(),
        defaultSpeed = defaultSpeed.first(),
        shelfSort = shelfSort.first(),
        shelfFilter = shelfFilter.first(),
        playbackErrorPolicy = playbackErrorPolicy.first(),
    )

    suspend fun restore(snapshot: PreferencesSnapshot) {
        context.dataStore.edit {
            it[Keys.THEME_MODE] = snapshot.themeMode.name
            it[Keys.DEFAULT_SPEED] = snapshot.defaultSpeed
            it[Keys.SHELF_SORT] = snapshot.shelfSort.name
            it[Keys.SHELF_FILTER] = snapshot.shelfFilter.name
            it[Keys.PLAYBACK_ERROR_POLICY] = snapshot.playbackErrorPolicy.name
            it.remove(Keys.DARK_THEME)
        }
    }
}
