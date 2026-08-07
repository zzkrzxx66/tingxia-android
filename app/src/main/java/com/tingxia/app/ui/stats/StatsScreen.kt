package com.tingxia.app.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.tingxia.app.ui.components.formatDuration
import com.tingxia.app.ui.theme.COVER_RATIO_PORTRAIT
import com.tingxia.app.ui.theme.CoverCorner
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
    onBack: () -> Unit,
    onOpenBook: (Long) -> Unit = {},
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
    SectionCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.stats_overview),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem(
                    modifier = Modifier.weight(1f),
                    value = formatDuration(s.totalListenedMs),
                    label = stringResource(R.string.stats_total),
                )
                StatItem(
                    modifier = Modifier.weight(1f),
                    value = formatDuration(s.weekListenedMs),
                    label = stringResource(R.string.stats_week),
                )
                StatItem(
                    modifier = Modifier.weight(1f),
                    value = formatDuration(s.todayListenedMs),
                    label = stringResource(R.string.stats_today),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.stats_books_completed, s.completedBooks, s.totalBooks),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatItem(modifier: Modifier = Modifier, value: String, label: String) {
    Column(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeekChart(daily: List<DailyListening>) {
    val dayFormat = remember { SimpleDateFormat("E", Locale.getDefault()) }
    val byDay = daily.associateBy { it.dayStartMs }
    val today = StatsRepository.dayStartMs(System.currentTimeMillis())
    val days = (6 downTo 0).map { today - it * StatsRepository.DAY_MS }
    val maxMs = (daily.maxOfOrNull { it.listenedMs } ?: 1L).coerceAtLeast(1L)

    SectionCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.stats_last_7_days),
                style = MaterialTheme.typography.titleMedium,
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(40.dp),
                    ) {
                        Text(
                            formatDuration(ms),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(96.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((96.dp * fraction).coerceAtLeast(2.dp)),
                            ) {
                                androidx.compose.material3.Surface(
                                    color = if (isToday) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                    },
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxSize(),
                                ) {}
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            dayFormat.format(Date(day)),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isToday) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBooksCard(topBooks: List<BookListening>, totalMs: Long, onOpenBook: (Long) -> Unit) {
    SectionCard {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.stats_top_books),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            topBooks.forEachIndexed { index, book ->
                val fraction = if (totalMs > 0) {
                    (book.listenedMs.toFloat() / totalMs).coerceIn(0f, 1f)
                } else 0f
                androidx.compose.material3.Surface(
                    onClick = { onOpenBook(book.bookId) },
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BookCover(
                            title = book.title,
                            coverPath = book.coverPath,
                            size = 44.dp,
                            ratio = COVER_RATIO_PORTRAIT,
                            corner = CoverCorner.Grid,
                        )
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                book.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth(),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                        Spacer(Modifier.size(12.dp))
                        Text(
                            formatDuration(book.listenedMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
