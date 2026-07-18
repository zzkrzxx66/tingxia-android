package com.tingxia.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setDefaultSpeed(speed: Float) {
        viewModelScope.launch { preferences.setDefaultSpeed(speed) }
    }

    fun setPlaybackErrorPolicy(policy: PlaybackErrorPolicy) {
        viewModelScope.launch { preferences.setPlaybackErrorPolicy(policy) }
    }
}
