package com.tingxia.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.tingxia.app.data.backup.BackupRepository
import com.tingxia.app.data.repo.UserPreferencesRepository
import com.tingxia.app.player.CacheManager
import com.tingxia.app.data.repo.ThemeMode
import com.tingxia.app.data.repo.PlaybackErrorPolicy
import androidx.media3.common.util.UnstableApi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@androidx.annotation.OptIn(UnstableApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val backupRepository: BackupRepository,
    private val cacheManager: CacheManager,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    val defaultSpeed: StateFlow<Float> = preferences.defaultSpeed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0f)

    val dynamicColor: StateFlow<Boolean> = preferences.dynamicColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val playbackErrorPolicy: StateFlow<PlaybackErrorPolicy> = preferences.playbackErrorPolicy
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            PlaybackErrorPolicy.STOP,
        )

    private val _message = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _error = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setDefaultSpeed(speed: Float) {
        viewModelScope.launch { preferences.setDefaultSpeed(speed) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferences.setDynamicColor(enabled) }
    }

    fun setPlaybackErrorPolicy(policy: PlaybackErrorPolicy) {
        viewModelScope.launch { preferences.setPlaybackErrorPolicy(policy) }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                backupRepository.exportTo(uri)
                _message.value = "备份已导出"
            } catch (e: Exception) {
                _error.value = e.message ?: "导出备份失败"
            }
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val result = backupRepository.importFrom(uri)
                _message.value = "已恢复 ${result.restoredBooks + result.createdReauthBooks} 本书、${result.restoredBookmarks} 个书签"
            } catch (e: Exception) {
                _error.value = e.message ?: "导入备份失败"
            }
        }
    }

    private val _cacheBytes = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val cacheBytes: StateFlow<Long> = _cacheBytes

    fun refreshCacheUsage() {
        viewModelScope.launch {
            _cacheBytes.value = runCatching { cacheManager.cachedBytes() }.getOrDefault(0L)
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            cacheManager.clearAll()
            _cacheBytes.value = 0L
            _message.value = "已清空全部缓存"
        }
    }

    fun clearMessage() { _message.value = null }
    fun clearError() { _error.value = null }
}
