package com.tingxia.app.ui.player

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tingxia.app.R
import com.tingxia.app.player.PlaybackSpeeds
import com.tingxia.app.player.SleepTimerMode
import com.tingxia.app.ui.components.formatDuration
import com.tingxia.app.ui.theme.TingXiaTheme
import kotlin.math.abs

/**
 * Shared dark immersive floating card shell for popups anchored above the full-player toolbar.
 *
 * The full player is always rendered over darkened artwork regardless of whether the app is in
 * light or dark mode, so wrapping player popovers in `TingXiaTheme(darkTheme = true)` keeps them
 * harmonious with the player surface and gentle on the eyes during night listening.
 */
@Composable
private fun PlayerPopupCard(
    icon: ImageVector,
    title: String,
    bottomOffsetDp: Float,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    TingXiaTheme(darkTheme = true, dynamicColor = false) {
        val maxPanelHeight =
            (LocalConfiguration.current.screenHeightDp - bottomOffsetDp - 100f).coerceAtLeast(140f).dp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .background(Color.Black.copy(alpha = 0.42f)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.97f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                shadowElevation = 16.dp,
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
                        .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 18.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.cancel),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    content()
                }
            }
        }
    }
}

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

    PlayerPopupCard(
        icon = Icons.Default.Speed,
        title = stringResource(R.string.playback_speed_title),
        bottomOffsetDp = bottomOffsetDp,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 42.dp),
        ) {
            val scopeOptions = listOf(
                stringResource(R.string.speed_for_this_book),
                stringResource(R.string.speed_for_global),
            )
            scopeOptions.forEachIndexed { index, label ->
                val selected = if (index == 0) forBook else !forBook
                SegmentedButton(
                    selected = selected,
                    onClick = { forBook = index == 0 },
                    shape = SegmentedButtonDefaults.itemShape(index, scopeOptions.size),
                ) {
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        val columns = if (
            LocalConfiguration.current.screenWidthDp < 360 ||
            LocalDensity.current.fontScale > 1.3f
        ) {
            2
        } else {
            4
        }
        val speedRows = PlaybackSpeeds.ALL.chunked(columns)
        speedRows.forEachIndexed { index, speeds ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                speeds.forEach { speed ->
                    PopupOptionChip(
                        label = PlaybackSpeeds.label(speed),
                        isSelected = abs(speed - selectedSpeed) < 0.01f,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onSpeedSelected(speed, forBook)
                            onDismiss()
                        },
                    )
                }
            }
        }

        if (forBook && usesBookSpeedOverride) {
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { onUseGlobalSpeed(); onDismiss() }) {
                Text(stringResource(R.string.speed_inherit_global))
            }
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
    val isTimerActive = sleepRemainingMs != null || sleepMode is SleepTimerMode.EndOfChapter
    val activeDurationMs = (sleepMode as? SleepTimerMode.AfterDuration)?.durationMs
    val columns = if (configuration.screenWidthDp < 360 || LocalDensity.current.fontScale > 1.3f) 2 else 3

    PlayerPopupCard(
        icon = Icons.Default.Timer,
        title = stringResource(R.string.sleep_timer_title),
        bottomOffsetDp = bottomOffsetDp,
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        if (isTimerActive) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                Text(
                    text = if (sleepRemainingMs != null) {
                        stringResource(R.string.sleep_timer_status_counting, formatDuration(sleepRemainingMs))
                    } else {
                        stringResource(R.string.sleep_timer_status_end_of_chapter)
                    },
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }

        listOf(10, 15, 30, 45, 60, 90).chunked(columns).forEachIndexed { index, minutes ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                minutes.forEach { duration ->
                    PopupOptionChip(
                        label = stringResource(R.string.sleep_timer_minutes, duration),
                        isSelected = activeDurationMs == duration * 60_000L,
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
            PopupOptionChip(
                label = stringResource(R.string.end_of_chapter),
                isSelected = sleepMode is SleepTimerMode.EndOfChapter,
                modifier = Modifier.weight(1f),
                onClick = {
                    onSleepEndOfChapter()
                    onDismiss()
                },
            )
            PopupOptionChip(
                label = stringResource(R.string.custom_duration),
                isSelected = false,
                modifier = Modifier.weight(1f),
                onClick = {
                    onDismiss()
                    onCustomDuration()
                },
            )
        }

        if (isTimerActive) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (sleepRemainingMs != null) {
                    TextButton(
                        onClick = onExtendSleep,
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                    ) {
                        Text(stringResource(R.string.extend_15_minutes), textAlign = TextAlign.Center)
                    }
                }
                TextButton(
                    onClick = {
                        onSleepMinutes(0)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                ) {
                    Text(
                        stringResource(R.string.sleep_timer_turn_off),
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun PopupOptionChip(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        border = if (!isSelected) {
            BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        } else {
            null
        },
        modifier = modifier
            .heightIn(min = 46.dp)
            .semantics { selected = isSelected },
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
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
