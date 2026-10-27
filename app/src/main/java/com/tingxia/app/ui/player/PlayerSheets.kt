package com.tingxia.app.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.material3.TextButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tingxia.app.R
import com.tingxia.app.player.PlaybackSpeeds
import com.tingxia.app.player.SleepTimerMode
import com.tingxia.app.ui.components.formatDuration
import kotlin.math.abs

/**
 * Lightweight floating bubble popover for playback speed adjustment.
 * Anchored above the bottom tool bar, dismisses on outside tap or chip selection.
 */
@Composable
fun PlayerSpeedPopup(
    visible: Boolean,
    currentSpeed: Float,
    globalSpeed: Float = 1f,
    bottomOffsetDp: Float = 88f,
    usesBookSpeedOverride: Boolean,
    onSpeedSelected: (Float, Boolean) -> Unit,
    onUseGlobalSpeed: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    BackHandler(onBack = onDismiss)
    var forBook by remember { mutableStateOf(true) }
    val selectedSpeed = if (forBook) currentSpeed else globalSpeed
    val maxPanelHeight = (LocalConfiguration.current.screenHeightDp - bottomOffsetDp - 100f).coerceAtLeast(120f).dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .background(Color.Black.copy(alpha = 0.28f)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.96f),
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = bottomOffsetDp.dp)
                .navigationBarsPadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* prevent dismiss */ },
                ),
        ) {
            Column(
                modifier = Modifier.heightIn(max = maxPanelHeight).verticalScroll(rememberScrollState()).padding(18.dp),
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.playback_speed_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.cancel),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Scope Selector
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    val scopeOptions = listOf(
                        stringResource(R.string.speed_for_this_book),
                        stringResource(R.string.speed_for_global),
                    )
                    scopeOptions.forEachIndexed { index, label ->
                        val selected = if (index == 0) forBook else !forBook
                        SegmentedButton(
                            selected = selected,
                            onClick = {
                                forBook = index == 0
                            },
                            shape = SegmentedButtonDefaults.itemShape(index, scopeOptions.size),
                        ) {
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                val columns = if (LocalConfiguration.current.screenWidthDp < 360 ||
                    androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) 2 else 4
                val speedRows = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f).chunked(columns)
                speedRows.forEachIndexed { index, speeds ->
                    if (index > 0) Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        speeds.forEach { speed ->
                            SpeedChip(speed, abs(speed - selectedSpeed) < 0.01f, Modifier.weight(1f)) {
                                onSpeedSelected(speed, forBook)
                                onDismiss()
                            }
                        }
                    }
                }

                if (forBook && usesBookSpeedOverride) {
                    TextButton(onClick = { onUseGlobalSpeed(); onDismiss() }) {
                        Text(stringResource(R.string.speed_inherit_global))
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedChip(
    speed: Float,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = if (!isSelected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        } else null,
        modifier = modifier.heightIn(min = 48.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp),
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(2.dp))
            }
            Text(
                text = PlaybackSpeeds.label(speed),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

/**
 * Timer choices anchored above the player toolbar.
 */
@Composable
fun PlayerSleepPopup(
    visible: Boolean,
    sleepRemainingMs: Long?,
    sleepMode: SleepTimerMode,
    bottomOffsetDp: Float = 88f,
    onSleepMinutes: (Int) -> Unit,
    onSleepEndOfChapter: () -> Unit,
    onExtendSleep: () -> Unit,
    onCustomDuration: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    BackHandler(onBack = onDismiss)
    val configuration = LocalConfiguration.current
    val maxPanelHeight = (configuration.screenHeightDp - bottomOffsetDp - 100f).coerceAtLeast(120f).dp
    val isTimerActive = sleepRemainingMs != null || sleepMode is SleepTimerMode.EndOfChapter
    val columns = if (configuration.screenWidthDp < 360 || LocalDensity.current.fontScale > 1.3f) 2 else 3

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .background(Color.Black.copy(alpha = 0.28f)),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = bottomOffsetDp.dp)
                .navigationBarsPadding()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { /* Keep taps inside the panel from dismissing it. */ },
                ),
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = maxPanelHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.sleep_timer_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.cancel),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                if (isTimerActive) {
                    Text(
                        text = if (sleepRemainingMs != null) {
                            stringResource(R.string.sleep_timer_status_counting, formatDuration(sleepRemainingMs))
                        } else {
                            stringResource(R.string.sleep_timer_status_end_of_chapter)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(8.dp))

                listOf(10, 15, 30, 45, 60, 90).chunked(columns).forEachIndexed { index, minutes ->
                    if (index > 0) Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        minutes.forEach { duration ->
                            SleepOptionButton(
                                title = stringResource(R.string.sleep_timer_minutes, duration),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    onSleepMinutes(duration)
                                    onDismiss()
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SleepOptionButton(
                        title = stringResource(R.string.end_of_chapter),
                        isSelected = sleepMode is SleepTimerMode.EndOfChapter,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onSleepEndOfChapter()
                            onDismiss()
                        },
                    )
                    SleepOptionButton(
                        title = stringResource(R.string.custom_duration),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onDismiss()
                            onCustomDuration()
                        },
                    )
                }

                if (isTimerActive) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        if (sleepRemainingMs != null) {
                            TextButton(
                                onClick = onExtendSleep,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            ) {
                                Text(stringResource(R.string.extend_15_minutes), textAlign = TextAlign.Center)
                            }
                        }
                        TextButton(
                            onClick = {
                                onSleepMinutes(0)
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) {
                            Text(stringResource(R.string.sleep_timer_turn_off), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepOptionButton(
    title: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = if (isSelected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        modifier = modifier.heightIn(min = 48.dp).semantics { selected = isSelected },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Backward compatibility aliases */
@Composable
fun PlayerSpeedSheet(
    visible: Boolean,
    currentSpeed: Float,
    usesBookSpeedOverride: Boolean,
    onSpeedSelected: (Float, Boolean) -> Unit,
    onUseGlobalSpeed: () -> Unit,
    onDismiss: () -> Unit,
) = PlayerSpeedPopup(
    visible = visible,
    currentSpeed = currentSpeed,
    usesBookSpeedOverride = usesBookSpeedOverride,
    onSpeedSelected = onSpeedSelected,
    onUseGlobalSpeed = onUseGlobalSpeed,
    onDismiss = onDismiss,
)

@Composable
fun PlayerSleepSheet(
    visible: Boolean,
    sleepRemainingMs: Long?,
    sleepMode: SleepTimerMode,
    onSleepMinutes: (Int) -> Unit,
    onSleepEndOfChapter: () -> Unit,
    onExtendSleep: () -> Unit,
    onCustomDuration: () -> Unit,
    onDismiss: () -> Unit,
) = PlayerSleepPopup(
    visible = visible,
    sleepRemainingMs = sleepRemainingMs,
    sleepMode = sleepMode,
    onSleepMinutes = onSleepMinutes,
    onSleepEndOfChapter = onSleepEndOfChapter,
    onExtendSleep = onExtendSleep,
    onCustomDuration = onCustomDuration,
    onDismiss = onDismiss,
)
