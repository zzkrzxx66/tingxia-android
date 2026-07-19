package com.tingxia.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.tingxia.app.data.backup.BackupRepository
import com.tingxia.app.data.repo.UserPreferencesRepository
import com.tingxia.app.data.repo.ThemeMode
import com.tingxia.app.data.repo.PlaybackErrorPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val backupRepository: BackupRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = preferences.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

    val defaultSpeed: StateFlow<Float> = preferences.defaultSpeed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0f)

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

    fun clearMessage() { _message.value = null }
    fun clearError() { _error.value = null }
}
