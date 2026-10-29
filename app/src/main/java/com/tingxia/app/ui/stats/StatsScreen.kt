package com.tingxia.app.ui.stats

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tingxia.app.R
import com.tingxia.app.data.db.BookListening
import com.tingxia.app.data.db.DailyListening
import com.tingxia.app.data.repo.ListeningStats
import com.tingxia.app.data.repo.StatsRepository
import com.tingxia.app.ui.components.BookCover
import com.tingxia.app.ui.components.EmptyState
import com.tingxia.app.ui.components.SectionCard
import com.tingxia.app.ui.theme.BookType
import com.tingxia.app.ui.theme.COVER_RATIO_PORTRAIT
import com.tingxia.app.ui.theme.CoverCorner
import com.tingxia.app.ui.theme.rememberCoverAccent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val statsRepository: StatsRepository,
) : ViewModel() {
    private val _stats = MutableStateFlow<ListeningStats?>(null)
    val stats: StateFlow<ListeningStats?> = _stats

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _stats.value = runCatching { statsRepository.stats() }.getOrNull()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit = {},
    showBack: Boolean = false,
    onOpenSettings: () -> Unit = {},
    onOpenBook: (Long) -> Unit = {},
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val topBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(topBarScrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                scrollBehavior = topBarScrollBehavior,
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    }
                },
                title = {
                    if (showBack) {
                        Text(
                            stringResource(R.string.stats_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                stringResource(R.string.stats_title),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                stringResource(R.string.stats_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    if (!showBack) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        val s = stats
        if (s == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { Text(stringResource(R.string.loading)) }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OverviewRow(s)
            WeekChart(s.daily)
            if (s.topBooks.isNotEmpty()) {
                TopBooksCard(s.topBooks, s.totalListenedMs, onOpenBook)
            } else {
                EmptyState(
                    icon = Icons.Default.EmojiEvents,
                    title = stringResource(R.string.stats_empty_title),
                    body = stringResource(R.string.stats_empty_body),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun OverviewRow(s: ListeningStats) {
    val density = LocalDensity.current
    SectionCard(shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.stats_overview),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val layout = resolveStatsOverviewLayout(
                    availableWidthDp = maxWidth.value,
                    fontScale = density.fontScale,
                )
                when (layout) {
                    StatsOverviewLayout.SINGLE_COLUMN -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatItem(
                                modifier = Modifier.fillMaxWidth(),
                                durationMs = s.totalListenedMs,
                                label = stringResource(R.string.stats_total),
                                highlight = true,
                                allowWrap = true,
                            )
                            StatItem(
                                modifier = Modifier.fillMaxWidth(),
                                durationMs = s.weekListenedMs,
                                label = stringResource(R.string.stats_week),
                                allowWrap = true,
                            )
                            StatItem(
                                modifier = Modifier.fillMaxWidth(),
                                durationMs = s.todayListenedMs,
                                label = stringResource(R.string.stats_today),
                                allowWrap = true,
                            )
                        }
                    }
                    StatsOverviewLayout.HERO_PLUS_TWO -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatItem(
                                modifier = Modifier.fillMaxWidth(),
                                durationMs = s.totalListenedMs,
                                label = stringResource(R.string.stats_total),
                                highlight = true,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                StatItem(
                                    modifier = Modifier.weight(1f),
                                    durationMs = s.weekListenedMs,
                                    label = stringResource(R.string.stats_week),
                                )
                                StatItem(
                                    modifier = Modifier.weight(1f),
                                    durationMs = s.todayListenedMs,
                                    label = stringResource(R.string.stats_today),
                                )
                            }
                        }
                    }
                    StatsOverviewLayout.THREE_COLUMNS -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StatItem(
                                modifier = Modifier.weight(1f),
                                durationMs = s.totalListenedMs,
                                label = stringResource(R.string.stats_total),
                                highlight = true,
                            )
                            StatItem(
                                modifier = Modifier.weight(1f),
                                durationMs = s.weekListenedMs,
                                label = stringResource(R.string.stats_week),
                            )
                            StatItem(
                                modifier = Modifier.weight(1f),
                                durationMs = s.todayListenedMs,
                                label = stringResource(R.string.stats_today),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.stats_books_completed, s.completedBooks, s.totalBooks),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (s.totalBooks > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${(s.completedBooks * 100 / s.totalBooks)}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (s.totalBooks > 0) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { (s.completedBooks.toFloat() / s.totalBooks.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    durationMs: Long,
    label: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    allowWrap: Boolean = false,
) {
    val parts = remember(durationMs) { formatStatsDurationParts(durationMs) }
    val unitLabel = stringResource(
        when (parts.unit) {
            StatDurationUnit.HOURS -> R.string.stats_hours_unit
            StatDurationUnit.MINUTES -> R.string.minutes
        },
    )
    val valueColor = if (highlight) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (highlight) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Text(
            text = buildAnnotatedString {
                append(parts.number)
                withStyle(
                    SpanStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = valueColor.copy(alpha = 0.82f),
                    ),
                ) {
                    append(" ")
                    append(unitLabel)
                }
            },
            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = if (allowWrap) 2 else 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (allowWrap) 2 else 1,
        )
    }
}

@Composable
private fun WeekChart(daily: List<DailyListening>) {
    val dayFormat = remember { SimpleDateFormat("E", Locale.getDefault()) }
    val byDay = daily.associateBy { it.dayStartMs }
    val today = StatsRepository.dayStartMs(System.currentTimeMillis())
    val days = (6 downTo 0).map { StatsRepository.shiftDay(today, -it) }
    val maxMs = (daily.maxOfOrNull { it.listenedMs } ?: 1L).coerceAtLeast(1L)
    var animationTriggered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        animationTriggered = true
    }

    SectionCard(shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.stats_last_7_days),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom,
            ) {
                days.forEach { day ->
                    val ms = byDay[day]?.listenedMs ?: 0L
                    val fraction = (ms.toFloat() / maxMs).coerceIn(0f, 1f)
                    val isToday = day == today
                    val animatedFraction by animateFloatAsState(
                        targetValue = if (animationTriggered) fraction else 0f,
                        animationSpec = tween(700, easing = FastOutSlowInEasing),
                        label = "statBarGrowth",
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            formatCompactChartDuration(ms),
                            style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .height(96.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            if (animatedFraction > 0.01f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height((96.dp * animatedFraction).coerceAtLeast(if (ms > 0) 8.dp else 0.dp))
                                        .clip(CircleShape)
                                        .background(
                                            if (isToday) {
                                                Brush.verticalGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.primary,
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                                                    ),
                                                )
                                            } else {
                                                Brush.verticalGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.62f),
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.36f),
                                                    ),
                                                )
                                            },
                                        ),
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            dayFormat.format(Date(day)),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

internal enum class StatsOverviewLayout {
    THREE_COLUMNS,
    HERO_PLUS_TWO,
    SINGLE_COLUMN,
}

internal fun resolveStatsOverviewLayout(
    availableWidthDp: Float,
    fontScale: Float,
): StatsOverviewLayout {
    val effectiveWidthDp = availableWidthDp / fontScale.coerceAtLeast(1f)
    return when {
        effectiveWidthDp >= 268f -> StatsOverviewLayout.THREE_COLUMNS
        effectiveWidthDp >= 190f -> StatsOverviewLayout.HERO_PLUS_TWO
        else -> StatsOverviewLayout.SINGLE_COLUMN
    }
}

internal enum class StatDurationUnit {
    MINUTES,
    HOURS,
}

internal data class CompactStatDuration(
    val number: String,
    val unit: StatDurationUnit,
)

/**
 * Formats cumulative listening durations into compact, human-readable stat parts
 * (`0 分钟`, `<1 分钟`, `42 分钟`, `12.5 小时`, `128 小时`) so overview tiles never
 * overflow into `100:00…` ellipses on narrow 320dp displays or large font scales.
 */
internal fun formatStatsDurationParts(ms: Long): CompactStatDuration {
    if (ms <= 0L) return CompactStatDuration("0", StatDurationUnit.MINUTES)
    if (ms < 60_000L) return CompactStatDuration("<1", StatDurationUnit.MINUTES)
    if (ms < 3_600_000L) return CompactStatDuration((ms / 60_000L).toString(), StatDurationUnit.MINUTES)
    if (ms >= 360_000_000L) {
        return CompactStatDuration((ms / 3_600_000L).toString(), StatDurationUnit.HOURS)
    }
    val tenths = (ms * 10L) / 3_600_000L
    val number = if (tenths % 10L == 0L) {
        (tenths / 10L).toString()
    } else {
        "${tenths / 10L}.${tenths % 10L}"
    }
    return CompactStatDuration(number, StatDurationUnit.HOURS)
}

internal fun formatCompactChartDuration(ms: Long): String {
    if (ms <= 0L) return ""
    if (ms < 60_000L) return "<1m"
    if (ms < 3_600_000L) return "${ms / 60_000L}m"
    if (ms >= 360_000_000L) return "${ms / 3_600_000L}h"
    val tenths = (ms * 10L) / 3_600_000L
    return if (tenths % 10L == 0L) "${tenths / 10L}h" else "${tenths / 10L}.${tenths % 10L}h"
}

@Composable
private fun TopBooksCard(
    topBooks: List<BookListening>,
    totalMs: Long,
    onOpenBook: (Long) -> Unit,
) {
    val maxBookMs = (topBooks.maxOfOrNull { it.listenedMs } ?: totalMs).coerceAtLeast(1L)
    SectionCard(shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.stats_top_books),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            topBooks.forEachIndexed { index, book ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 88.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    )
                }
                val fraction = (book.listenedMs.toFloat() / maxBookMs.toFloat()).coerceIn(0f, 1f)
                val accent = rememberCoverAccent(book.coverPath)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenBook(book.bookId) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold,
                        color = if (index < 3) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.width(20.dp),
                    )
                    BookCover(
                        title = book.title,
                        coverPath = book.coverPath,
                        size = 44.dp,
                        ratio = COVER_RATIO_PORTRAIT,
                        corner = CoverCorner.Grid,
                        framed = true,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            book.title,
                            style = BookType.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape),
                            color = accent,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            strokeCap = StrokeCap.Round,
                            gapSize = 0.dp,
                            drawStopIndicator = {},
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    val bookDuration = remember(book.listenedMs) { formatStatsDurationParts(book.listenedMs) }
                    val bookUnit = stringResource(
                        when (bookDuration.unit) {
                            StatDurationUnit.HOURS -> R.string.stats_hours_unit
                            StatDurationUnit.MINUTES -> R.string.minutes
                        },
                    )
                    Text(
                        "${bookDuration.number} $bookUnit",
                        style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
