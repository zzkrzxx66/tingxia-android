package com.tingxia.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tingxia.app.player.PlayerController
import com.tingxia.app.player.PlayerUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
) : ViewModel() {

    val state: StateFlow<PlayerUiState> = playerController.state

    fun connect() = playerController.connect()

    fun playBook(bookId: Long, chapterId: Long? = null) {
        viewModelScope.launch {
            playerController.playBook(bookId, chapterId)
        }
    }

    fun togglePlayPause() = playerController.togglePlayPause()
    fun seekTo(ms: Long) = playerController.seekTo(ms)
    fun seekBy(delta: Long) = playerController.seekBy(delta)
    fun nextChapter() = playerController.nextChapter()
    fun previousChapter() = playerController.previousChapter()
    fun setSpeed(speed: Float) = playerController.setSpeed(speed)
    fun setSleepMinutes(minutes: Int) = playerController.setSleepMinutes(minutes)

    override fun onCleared() {
        playerController.flushProgress()
        super.onCleared()
    }
}
