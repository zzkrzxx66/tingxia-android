package com.tingxia.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tingxia.app.data.repo.UserPreferencesRepository
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

    val darkTheme: StateFlow<Boolean?> = preferences.darkTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    val defaultSpeed: StateFlow<Float> = preferences.defaultSpeed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0f)

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { preferences.setDarkTheme(enabled) }
    }

    fun setDefaultSpeed(speed: Float) {
        viewModelScope.launch { preferences.setDefaultSpeed(speed) }
    }
}
