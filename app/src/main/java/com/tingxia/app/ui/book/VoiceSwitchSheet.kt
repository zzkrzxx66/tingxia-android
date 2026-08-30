package com.tingxia.app.ui.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tingxia.app.R
import com.tingxia.app.data.remote.FqTtsTone

/**
 * Voice picker for a TTS book already on the shelf.
 *
 * The chapters of a TTS book are the novel's own, so only the recording changes:
 * progress, bookmarks and chapter titles all stay where they are. The cached audio
 * belongs to the old voice, which is why the caller drops it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSwitchSheet(
    state: BookDetailViewModel.VoiceSwitchUiState,
    onPick: (FqTtsTone) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Text(
                stringResource(R.string.book_switch_voice),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                stringResource(R.string.online_tts_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))
            when {
                state.loading -> Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                state.voices?.ttsTones.isNullOrEmpty() -> Text(
                    stringResource(R.string.online_voices_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
                else -> LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(state.voices!!.ttsTones, key = { it.toneId }) { tone ->
                        val selected = tone.toneId.toString() == state.currentToneId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected, onClick = { if (!selected) onPick(tone) })
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tone.title, style = MaterialTheme.typography.bodyLarge)
                                val subtitle = listOfNotNull(
                                    tone.description,
                                    if (tone.toneId == state.voices.recommendToneId) {
                                        stringResource(R.string.online_tts_recommended)
                                    } else {
                                        null
                                    },
                                ).joinToString(" · ")
                                if (subtitle.isNotBlank()) {
                                    Text(
                                        subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
