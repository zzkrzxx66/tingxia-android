package com.tingxia.app.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tingxia.app.R
import com.tingxia.app.player.PlaybackSpeeds
import com.tingxia.app.player.SleepTimerMode
import com.tingxia.app.ui.components.formatDuration
import kotlin.math.abs

/**
 * Modern modal bottom sheet for adjusting playback speed with balanced 4x2 grid.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSpeedSheet(
    visible: Boolean,
    currentSpeed: Float,
    usesBookSpeedOverride: Boolean,
    onSpeedSelected: (Float) -> Unit,
    onUseGlobalSpeed: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.playback_speed_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                }
            }

            Spacer(Modifier.height(16.dp))

            // Speed Scope selector
            val scopeOptions = listOf(
                stringResource(R.string.speed_for_this_book),
                stringResource(R.string.speed_for_global),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                scopeOptions.forEachIndexed { index, label ->
                    val selected = if (index == 0) usesBookSpeedOverride else !usesBookSpeedOverride
                    SegmentedButton(
                        selected = selected,
                        onClick = {
                            if (index == 1) onUseGlobalSpeed()
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, scopeOptions.size),
                    ) {
                        Text(label, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // 4x2 balanced grid
            val speedOptionsRow1 = listOf(0.75f, 1.0f, 1.25f, 1.5f)
            val speedOptionsRow2 = listOf(1.75f, 2.0f, 2.5f, 3.0f)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                speedOptionsRow1.forEach { speed ->
                    SpeedChip(
                        speed = speed,
                        isSelected = abs(speed - currentSpeed) < 0.01f,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onSpeedSelected(speed)
                            onDismiss()
                        },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                speedOptionsRow2.forEach { speed ->
                    SpeedChip(
                        speed = speed,
                        isSelected = abs(speed - currentSpeed) < 0.01f,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onSpeedSelected(speed)
                            onDismiss()
                        },
                    )
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
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = if (!isSelected) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        } else null,
        modifier = modifier.height(46.dp),
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
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(3.dp))
            }
            Text(
                text = PlaybackSpeeds.label(speed),
                style = MaterialTheme.typography.titleMedium,
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
 * Elegant modal bottom sheet for setting a sleep timer, designed with a clean vertical list
 * and prominent 10-minute favorite option.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isTimerActive = sleepRemainingMs != null || sleepMode is SleepTimerMode.EndOfChapter

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Bedtime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.sleep_timer_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                }
            }

            // If timer is running, show active countdown card
            if (isTimerActive) {
                Spacer(Modifier.height(14.dp))
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(38.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.HourglassTop,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (sleepRemainingMs != null) "倒计时中" else "播完当前章停止",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                )
                                Text(
                                    text = if (sleepRemainingMs != null) {
                                        formatDuration(sleepRemainingMs)
                                    } else {
                                        stringResource(R.string.sleep_timer_status_end_of_chapter)
                                    },
                                    style = if (sleepRemainingMs != null) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (sleepRemainingMs != null) {
                                FilledTonalButton(
                                    onClick = onExtendSleep,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ),
                                ) {
                                    Icon(Icons.Default.MoreTime, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.extend_15_minutes))
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    onSleepMinutes(0)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Icon(Icons.Default.TimerOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.sleep_timer_turn_off))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (isTimerActive) {
                Text(
                    text = "修改定时",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
            }

            // Options List Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    // 10 分钟 (User's special favorite)
                    SleepOptionRow(
                        title = "10 分钟",
                        badge = stringResource(R.string.sleep_timer_popular),
                        isPopular = true,
                        onClick = {
                            onSleepMinutes(10)
                            onDismiss()
                        },
                    )
                    OptionDivider()

                    // 15 分钟
                    SleepOptionRow(
                        title = "15 分钟",
                        onClick = {
                            onSleepMinutes(15)
                            onDismiss()
                        },
                    )
                    OptionDivider()

                    // 30 分钟
                    SleepOptionRow(
                        title = "30 分钟",
                        onClick = {
                            onSleepMinutes(30)
                            onDismiss()
                        },
                    )
                    OptionDivider()

                    // 45 分钟
                    SleepOptionRow(
                        title = "45 分钟",
                        onClick = {
                            onSleepMinutes(45)
                            onDismiss()
                        },
                    )
                    OptionDivider()

                    // 60 分钟
                    SleepOptionRow(
                        title = "60 分钟",
                        onClick = {
                            onSleepMinutes(60)
                            onDismiss()
                        },
                    )
                    OptionDivider()

                    // 90 分钟
                    SleepOptionRow(
                        title = "90 分钟",
                        onClick = {
                            onSleepMinutes(90)
                            onDismiss()
                        },
                    )
                    OptionDivider()

                    // 播完当前章
                    SleepOptionRow(
                        title = stringResource(R.string.end_of_chapter),
                        isSelected = sleepMode is SleepTimerMode.EndOfChapter,
                        onClick = {
                            onSleepEndOfChapter()
                            onDismiss()
                        },
                    )
                    OptionDivider()

                    // 自定义时长
                    SleepOptionRow(
                        title = stringResource(R.string.custom_duration),
                        hasChevron = true,
                        onClick = {
                            onDismiss()
                            onCustomDuration()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SleepOptionRow(
    title: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    isPopular: Boolean = false,
    isSelected: Boolean = false,
    hasChevron: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isPopular || isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )

        if (badge != null) {
            Spacer(Modifier.width(10.dp))
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        } else if (hasChevron) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun OptionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
        thickness = 0.8.dp,
    )
}
