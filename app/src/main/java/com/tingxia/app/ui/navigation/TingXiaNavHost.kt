package com.tingxia.app.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tingxia.app.ui.book.BookDetailScreen
import com.tingxia.app.R
import com.tingxia.app.ui.player.FullPlayerScreen
import com.tingxia.app.ui.player.ChapterPickerSheet
import com.tingxia.app.ui.player.MiniPlayerBar
import com.tingxia.app.ui.player.PlayerViewModel
import com.tingxia.app.ui.settings.SettingsScreen
import com.tingxia.app.ui.shelf.FqNovelCatalogScreen
import com.tingxia.app.ui.shelf.ShelfScreen
import com.tingxia.app.ui.stats.StatsScreen

object Routes {
    const val SHELF = "shelf"
    const val ONLINE = "online"
    const val BOOK = "book/{bookId}"
    const val PLAYER = "player"
    const val SETTINGS = "settings"
    const val STATS = "stats"

    fun book(bookId: Long) = "book/$bookId"
}

@Composable
fun TingXiaNavHost(
    playerViewModel: PlayerViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val playerState by playerViewModel.state.collectAsStateWithLifecycle()
    val toast by playerViewModel.toast.collectAsStateWithLifecycle()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showMini = playerState.bookId != null && currentRoute != Routes.PLAYER
    val atTopLevel = currentRoute == Routes.SHELF || currentRoute == Routes.ONLINE
    val snackbar = remember { SnackbarHostState() }
    val notificationDeniedMessage = stringResource(R.string.notification_permission_denied)
    val skipChapterLabel = stringResource(R.string.skip_chapter)
    val context = LocalContext.current
    var pendingPlayback by remember { mutableStateOf<(() -> Unit)?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingPlayback
        pendingPlayback = null
        action?.invoke()
        if (!granted) {
            // Playback is still allowed, but Android may hide the media notification.
            playerViewModel.showMessage(notificationDeniedMessage)
        }
    }
    val startPlayback: ((() -> Unit) -> Unit) = { action ->
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingPlayback = action
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    DisposableEffect(Unit) {
        playerViewModel.connect()
        onDispose { /* keep session alive for background play */ }
    }

    LaunchedEffect(playerState.lastError) {
        playerState.lastError?.let {
            val result = snackbar.showSnackbar(
                message = it,
                actionLabel = if (playerState.errorCanSkip) skipChapterLabel else null,
            )
            playerViewModel.clearError()
            if (result == SnackbarResult.ActionPerformed) playerViewModel.nextChapter()
        }
    }
    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar(it)
            playerViewModel.clearToast()
        }
    }

    // Outer Scaffold only hosts the mini player / snackbar.
    // contentWindowInsets = 0 so status-bar padding is applied once by each
    // destination's own TopAppBar (avoids a large blank strip under the status bar).
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showMini || atTopLevel) {
                Column {
                    AnimatedVisibility(
                        visible = showMini,
                        enter = slideInVertically(tween(280, easing = FastOutSlowInEasing)) { it } +
                            fadeIn(tween(280)),
                        exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(180)),
                    ) {
                        MiniPlayerBar(
                            state = playerState,
                            onToggle = { playerViewModel.togglePlayPause() },
                            onOpen = { navController.navigate(Routes.PLAYER) },
                            onNext = { playerViewModel.nextChapter() },
                        )
                    }
                    if (atTopLevel) {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentRoute == Routes.SHELF,
                                onClick = {
                                    if (currentRoute != Routes.SHELF) {
                                        navController.navigate(Routes.SHELF) {
                                            popUpTo(Routes.SHELF) { inclusive = false }
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                icon = {
                                    // Filled marks the destination you are on, outlined the rest:
                                    // the old bar mixed both styles at once.
                                    Icon(
                                        if (currentRoute == Routes.SHELF) {
                                            Icons.AutoMirrored.Filled.LibraryBooks
                                        } else {
                                            Icons.AutoMirrored.Outlined.LibraryBooks
                                        },
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_shelf)) },
                            )
                            NavigationBarItem(
                                selected = currentRoute == Routes.ONLINE,
                                onClick = {
                                    if (currentRoute != Routes.ONLINE) {
                                        navController.navigate(Routes.ONLINE) {
                                            popUpTo(Routes.SHELF)
                                            launchSingleTop = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        if (currentRoute == Routes.ONLINE) {
                                            Icons.Filled.TravelExplore
                                        } else {
                                            Icons.Outlined.TravelExplore
                                        },
                                        contentDescription = null,
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_online)) },
                            )
                        }
                    }
                }
            }
        },
        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Only lift content above the bottom bar; never re-apply status bars.
                    .padding(bottom = innerPadding.calculateBottomPadding()),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.SHELF,
                    enterTransition = { fadeIn(tween(250)) },
                    exitTransition = { fadeOut(tween(200)) },
                    popEnterTransition = { fadeIn(tween(250)) },
                    popExitTransition = { fadeOut(tween(200)) },
                ) {
                    composable(Routes.SHELF) {
                        ShelfScreen(
                            onOpenBook = { id -> navController.navigate(Routes.book(id)) },
                            onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                            onGoOnline = {
                                navController.navigate(Routes.ONLINE) {
                                    popUpTo(Routes.SHELF)
                                    launchSingleTop = true
                                }
                            },
                            playingBookId = playerState.bookId,
                            isPlaying = playerState.isPlaying,
                            onPlayBook = { id ->
                                // Same book already loaded: the shelf button is a play/pause
                                // toggle, so tapping it twice does not restart the chapter.
                                if (playerState.bookId == id && playerState.isPlaying) {
                                    playerViewModel.togglePlayPause()
                                } else {
                                    startPlayback {
                                        playerViewModel.playBook(id) { ok ->
                                            if (ok) navController.navigate(Routes.PLAYER)
                                        }
                                    }
                                }
                            },
                        )
                    }
                    composable(Routes.ONLINE) {
                        FqNovelCatalogScreen(
                            onOpenBook = { id -> navController.navigate(Routes.book(id)) },
                        )
                    }
                    // Drilling into a book slides laterally (push/pop) instead of the
                    // generic cross-fade, matching the navigation hierarchy.
                    composable(
                        route = Routes.BOOK,
                        arguments = listOf(navArgument("bookId") { type = NavType.LongType }),
                        enterTransition = {
                            slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 4 } +
                                fadeIn(tween(300))
                        },
                        exitTransition = { fadeOut(tween(200)) },
                        popEnterTransition = { fadeIn(tween(250)) },
                        popExitTransition = {
                            slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 4 } +
                                fadeOut(tween(220))
                        },
                    ) { entry ->
                        val bookId = entry.arguments?.getLong("bookId") ?: return@composable
                        BookDetailScreen(
                            bookId = bookId,
                            onBack = { navController.popBackStack() },
                            onPlayChapter = { chapterId ->
                                startPlayback {
                                    playerViewModel.playBook(bookId, chapterId) { ok ->
                                        if (ok) navController.navigate(Routes.PLAYER)
                                    }
                                }
                            },
                            onContinue = {
                                startPlayback {
                                    playerViewModel.playBook(bookId) { ok ->
                                        if (ok) navController.navigate(Routes.PLAYER)
                                    }
                                }
                            },
                            onPlayBookmark = { chapterId, positionMs ->
                                startPlayback {
                                    playerViewModel.playBook(bookId, chapterId, positionMs) { ok ->
                                        if (ok) navController.navigate(Routes.PLAYER)
                                    }
                                }
                            },
                        )
                    }
                    // The player is a modal surface: it rises from the mini bar and
                    // sinks back, unlike the lateral fades used between destinations.
                    composable(
                        route = Routes.PLAYER,
                        enterTransition = {
                            slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it } +
                                fadeIn(tween(320))
                        },
                        exitTransition = {
                            slideOutVertically(tween(260)) { it } + fadeOut(tween(200))
                        },
                        popEnterTransition = {
                            slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it } +
                                fadeIn(tween(320))
                        },
                        popExitTransition = {
                            slideOutVertically(tween(260)) { it } + fadeOut(tween(200))
                        },
                    ) {
                        val chapterPicker by playerViewModel.picker.collectAsStateWithLifecycle()
                        val cachingIds by playerViewModel.cachingChapterIds.collectAsStateWithLifecycle()
                        val textAvailable by playerViewModel.textAvailable.collectAsStateWithLifecycle()
                        val playerChapterText by playerViewModel.chapterText.collectAsStateWithLifecycle()
                        FullPlayerScreen(
                            state = playerState,
                            onBack = { navController.popBackStack() },
                            onToggle = { playerViewModel.togglePlayPause() },
                            onSeek = { playerViewModel.seekTo(it) },
                            onSeekBy = { playerViewModel.seekBy(it) },
                            onPrev = { playerViewModel.previousChapter() },
                            onNext = { playerViewModel.nextChapter() },
                            onSpeed = { playerViewModel.setSpeed(it) },
                            onUseGlobalSpeed = { playerViewModel.useGlobalSpeed() },
                            onSleep = { playerViewModel.setSleepMinutes(it) },
                            onSleepEndOfChapter = { playerViewModel.setSleepEndOfChapter() },
                            onExtendSleep = { playerViewModel.extendSleep() },
                            onAddBookmark = { playerViewModel.addBookmark() },
                            onSaveSkipOffsets = { intro, outro ->
                                playerViewModel.setSkipOffsets(intro, outro)
                            },
                            onOpenChapters = { playerViewModel.openChapterPicker() },
                            onOpenText = if (textAvailable) {
                                { playerViewModel.openChapterText() }
                            } else {
                                null
                            },
                        )
                        if (playerChapterText.visible) {
                            // Reload when the player moves to another chapter, otherwise the
                            // drawer would highlight sentences of the previous one.
                            androidx.compose.runtime.LaunchedEffect(playerState.chapterId) {
                                playerViewModel.refreshChapterTextIfOpen()
                            }
                            com.tingxia.app.ui.components.ChapterReadAlongSheet(
                                chapterTitle = playerChapterText.chapterTitle,
                                timeline = playerChapterText.timeline,
                                loading = playerChapterText.loading,
                                error = playerChapterText.error,
                                positionMs = playerState.positionMs,
                                isPlaying = playerState.isPlaying,
                                onSeek = { positionMs -> playerViewModel.seekTo(positionMs) },
                                onDismiss = { playerViewModel.closeChapterText() },
                            )
                        }
                        ChapterPickerSheet(
                            state = chapterPicker,
                            currentChapterId = playerState.chapterId,
                            currentProgressFraction = playerState.durationMs
                                .takeIf { it > 0L }
                                ?.let { playerState.positionMs.toFloat() / it.toFloat() },
                            currentIsLoading = playerState.isPreparing || playerState.isBuffering,
                            cachingChapterIds = cachingIds,
                            onDismiss = { playerViewModel.closeChapterPicker() },
                            onQueryChange = { playerViewModel.setPickerQuery(it) },
                            onToggleSearch = { playerViewModel.togglePickerSearch() },
                            onToggleOrder = { playerViewModel.togglePickerOrder() },
                            onFilterChange = { playerViewModel.setPickerFilter(it) },
                            onPlayChapter = { playerViewModel.playChapter(it) },
                            onStartSelection = { playerViewModel.startSelection(it) },
                            onToggleSelection = { playerViewModel.toggleSelection(it) },
                            onClearSelection = { playerViewModel.clearSelection() },
                            onSelectAllVisible = { playerViewModel.selectAllVisible(it) },
                            onCacheSelection = { playerViewModel.cacheSelection() },
                            onClearCacheSelection = { playerViewModel.clearCacheForSelection() },
                            onMarkSelection = { playerViewModel.markSelection(it) },
                            onCacheChapter = { playerViewModel.cacheChapter(it) },
                            onClearChapterCache = { playerViewModel.clearChapterCache(it) },
                        )
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onOpenStats = { navController.navigate(Routes.STATS) },
                        )
                    }
                    composable(Routes.STATS) {
                        StatsScreen(
                            onBack = { navController.popBackStack() },
                            onOpenBook = { id -> navController.navigate(Routes.book(id)) },
                        )
                    }
                }
            }
        },
    )
}
