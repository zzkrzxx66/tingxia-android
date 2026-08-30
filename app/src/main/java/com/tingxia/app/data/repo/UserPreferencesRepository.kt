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
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val DEFAULT_SPEED = floatPreferencesKey("default_speed")
        val SHELF_SORT = stringPreferencesKey("shelf_sort")
        val SHELF_FILTER = stringPreferencesKey("shelf_filter")
        val PLAYBACK_ERROR_POLICY = stringPreferencesKey("playback_error_policy")
        val ONLINE_SEARCH_HISTORY = stringPreferencesKey("online_search_history")
        val UPDATE_CHECK_ENABLED = booleanPreferencesKey("update_check_enabled")
        val LAST_UPDATE_SWEEP_AT = androidx.datastore.preferences.core.longPreferencesKey("last_update_sweep_at")
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

    /**
     * Deliberately kept out of [PreferencesSnapshot]: wallpaper-derived colour is a property of
     * the device it was enabled on, so restoring it onto another phone carries no meaning.
     */
    val dynamicColor: Flow<Boolean> = preferencesFlow.map { prefs ->
        prefs[Keys.DYNAMIC_COLOR] ?: false
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

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
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

    /** Most recent online-search terms, newest first. */
    val onlineSearchHistory: Flow<List<String>> = preferencesFlow.map { prefs ->
        prefs[Keys.ONLINE_SEARCH_HISTORY]
            ?.split('\n')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
    }

    suspend fun rememberOnlineSearch(keyword: String, limit: Int = 12) {
        val normalized = keyword.trim()
        if (normalized.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.ONLINE_SEARCH_HISTORY]
                ?.split('\n')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
            val merged = (listOf(normalized) + current.filter { !it.equals(normalized, ignoreCase = true) })
                .take(limit)
            prefs[Keys.ONLINE_SEARCH_HISTORY] = merged.joinToString("\n")
        }
    }

    suspend fun clearOnlineSearchHistory() {
        context.dataStore.edit { it.remove(Keys.ONLINE_SEARCH_HISTORY) }
    }

    /** Whether online books are checked for new chapters in the background. */
    val updateCheckEnabled: Flow<Boolean> = preferencesFlow.map { prefs ->
        prefs[Keys.UPDATE_CHECK_ENABLED] ?: true
    }

    suspend fun setUpdateCheckEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.UPDATE_CHECK_ENABLED] = enabled }
    }

    val lastUpdateSweepAt: Flow<Long> = preferencesFlow.map { prefs ->
        prefs[Keys.LAST_UPDATE_SWEEP_AT] ?: 0L
    }

    suspend fun setLastUpdateSweepAt(value: Long) {
        context.dataStore.edit { it[Keys.LAST_UPDATE_SWEEP_AT] = value }
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
