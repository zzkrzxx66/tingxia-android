package com.tingxia.app.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tingxia.app.R
import com.tingxia.app.player.PlaybackSpeeds
import com.tingxia.app.player.SleepTimerMode
import com.tingxia.app.ui.components.formatDuration
import kotlin.math.abs

/**
 * Modern modal bottom sheet for adjusting playback speed.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

            // Speed chips
            val speedOptions = listOf(0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                speedOptions.forEach { speed ->
                    val isSelected = abs(speed - currentSpeed) < 0.01f
                    Surface(
                        onClick = {
                            onSpeedSelected(speed)
                            onDismiss()
                        },
                        shape = MaterialTheme.shapes.medium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        border = if (!isSelected) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        } else null,
                        modifier = Modifier
                            .weight(1f, fill = true)
                            .height(48.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
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
            }
        }
    }
}

/**
 * Modern modal bottom sheet for setting a sleep timer, with prominent 10-minute quick option.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
        ) {
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
                    text = stringResource(R.string.sleep),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel))
                }
            }

            // If timer is running, show rich countdown banner
            if (isTimerActive) {
                Spacer(Modifier.height(14.dp))
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.HourglassTop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (sleepRemainingMs != null) {
                                    stringResource(
                                        R.string.sleep_timer_status_counting,
                                        formatDuration(sleepRemainingMs),
                                    )
                                } else {
                                    stringResource(R.string.sleep_timer_status_end_of_chapter)
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (sleepRemainingMs != null) {
                                FilledTonalButton(
                                    onClick = onExtendSleep,
                                    modifier = Modifier.weight(1f),
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
                            ) {
                                Icon(Icons.Default.TimerOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.sleep_timer_turn_off))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Options Grid
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 10 分钟 (User's special favorite)
                SleepOptionCard(
                    title = "10 分钟",
                    badge = stringResource(R.string.sleep_timer_popular),
                    isHighlight = true,
                    onClick = {
                        onSleepMinutes(10)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )

                // 15 分钟
                SleepOptionCard(
                    title = "15 分钟",
                    onClick = {
                        onSleepMinutes(15)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )

                // 30 分钟
                SleepOptionCard(
                    title = "30 分钟",
                    onClick = {
                        onSleepMinutes(30)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )

                // 45 分钟
                SleepOptionCard(
                    title = "45 分钟",
                    onClick = {
                        onSleepMinutes(45)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )

                // 60 分钟
                SleepOptionCard(
                    title = "60 分钟",
                    onClick = {
                        onSleepMinutes(60)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )

                // 90 分钟
                SleepOptionCard(
                    title = "90 分钟",
                    onClick = {
                        onSleepMinutes(90)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )

                // 播完当前章
                SleepOptionCard(
                    title = stringResource(R.string.end_of_chapter),
                    onClick = {
                        onSleepEndOfChapter()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                )

                // 自定义时长
                SleepOptionCard(
                    title = stringResource(R.string.custom_duration),
                    onClick = {
                        onDismiss()
                        onCustomDuration()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun SleepOptionCard(
    title: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    isHighlight: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (isHighlight) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        border = if (isHighlight) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        },
        modifier = modifier
            .height(52.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                if (isHighlight) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Medium,
                    color = if (isHighlight) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            if (badge != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 3.dp, end = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}
