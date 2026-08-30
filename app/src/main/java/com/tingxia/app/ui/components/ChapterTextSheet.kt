package com.tingxia.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tingxia.app.R

/**
 * Read-along drawer: the chapter's text next to the audio that is playing.
 *
 * Font size is deliberately local state — it is a reading comfort knob, not a
 * setting worth persisting across books.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterTextSheet(
    chapterTitle: String,
    text: String,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
) {
    var fontSize by remember { mutableFloatStateOf(16f) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.chapter_text_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        chapterTitle,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                IconButton(
                    onClick = { fontSize = (fontSize - 1f).coerceAtLeast(12f) },
                    enabled = fontSize > 12f,
                ) {
                    Icon(
                        Icons.Default.TextDecrease,
                        contentDescription = stringResource(R.string.chapter_text_font_smaller),
                    )
                }
                IconButton(
                    onClick = { fontSize = (fontSize + 1f).coerceAtMost(26f) },
                    enabled = fontSize < 26f,
                ) {
                    Icon(
                        Icons.Default.TextIncrease,
                        contentDescription = stringResource(R.string.chapter_text_font_larger),
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            when {
                loading -> Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(12.dp))
                    Text(
                        stringResource(R.string.chapter_text_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                error != null -> Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                text.isBlank() -> Text(
                    stringResource(R.string.chapter_text_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                else -> Text(
                    text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.75f).sp,
                    ),
                    modifier = Modifier
                        .heightIn(max = 560.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}
