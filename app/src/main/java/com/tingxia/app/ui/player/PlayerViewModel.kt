package com.tingxia.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tingxia.app.data.repo.BookmarkRepository
import com.tingxia.app.player.PlayerController
import com.tingxia.app.player.PlayerUiState
import com.tingxia.app.player.SleepTimerMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val bookmarkRepository: BookmarkRepository,
) : ViewModel() {

    val state: StateFlow<PlayerUiState> = playerController.state

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    fun connect() = playerController.connect()

    fun playBook(
        bookId: Long,
        chapterId: Long? = null,
        positionMs: Long? = null,
        onResult: (Boolean) -> Unit = {},
    ) {
        viewModelScope.launch {
            val ok = playerController.playBook(bookId, chapterId, positionMs)
            onResult(ok)
        }
    }

    fun refreshAfterRescan(bookId: Long, chapterId: Long?, positionMs: Long, wasPlaying: Boolean) {
        viewModelScope.launch {
            playerController.refreshPlaylistAfterRescan(bookId, chapterId, positionMs, wasPlaying)
        }
    }

    fun togglePlayPause() = playerController.togglePlayPause()
    fun seekTo(ms: Long) = playerController.seekTo(ms)
    fun seekBy(delta: Long) = playerController.seekBy(delta)
    fun nextChapter() = playerController.nextChapter()
    fun previousChapter() = playerController.previousChapter()
    fun setSpeed(speed: Float) = playerController.setSpeed(speed, asBookDefault = true)
    fun setSleepMinutes(minutes: Int) = playerController.setSleepMinutes(minutes)
    fun setSleepEndOfChapter() = playerController.setSleepMode(SleepTimerMode.EndOfChapter)
    fun clearError() = playerController.clearError()

    fun addBookmark() {
        val s = state.value
        val bookId = s.bookId ?: return
        val chapterId = s.chapterId ?: return
        viewModelScope.launch {
            try {
                bookmarkRepository.addBookmark(bookId, chapterId, s.positionMs)
                _toast.value = "已添加书签"
            } catch (e: Exception) {
                _toast.value = e.message ?: "添加书签失败"
            }
        }
    }

    fun clearToast() { _toast.value = null }
}
