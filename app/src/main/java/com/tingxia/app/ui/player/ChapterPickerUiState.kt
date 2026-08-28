package com.tingxia.app.ui.player

import com.tingxia.app.data.model.Chapter
import com.tingxia.app.ui.chapters.ChapterListControls

/** UI state for the player's chapter picker sheet. */
data class ChapterPickerUiState(
    val visible: Boolean = false,
    val chapters: List<Chapter> = emptyList(),
    val controls: ChapterListControls = ChapterListControls(),
    /** Online book: cache actions and the cached counter are only meaningful there. */
    val isRemote: Boolean = false,
)
