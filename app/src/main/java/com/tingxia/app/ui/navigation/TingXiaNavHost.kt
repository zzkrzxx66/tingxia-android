package com.tingxia.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tingxia.app.ui.book.BookDetailScreen
import com.tingxia.app.ui.player.FullPlayerScreen
import com.tingxia.app.ui.player.MiniPlayerBar
import com.tingxia.app.ui.player.PlayerViewModel
import com.tingxia.app.ui.settings.SettingsScreen
import com.tingxia.app.ui.shelf.ShelfScreen

object Routes {
    const val SHELF = "shelf"
    const val BOOK = "book/{bookId}"
    const val PLAYER = "player"
    const val SETTINGS = "settings"

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
    val snackbar = remember { SnackbarHostState() }

    DisposableEffect(Unit) {
        playerViewModel.connect()
        onDispose { /* keep session alive for background play */ }
    }

    LaunchedEffect(playerState.lastError) {
        playerState.lastError?.let {
            snackbar.showSnackbar(it)
            playerViewModel.clearError()
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
            if (showMini) {
                MiniPlayerBar(
                    state = playerState,
                    onToggle = { playerViewModel.togglePlayPause() },
                    onOpen = { navController.navigate(Routes.PLAYER) },
                )
            }
        },
        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Only lift content above the mini player; never re-apply status bars.
                    .padding(bottom = if (showMini) innerPadding.calculateBottomPadding() else 0.dp),
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Routes.SHELF,
                ) {
                    composable(Routes.SHELF) {
                        ShelfScreen(
                            onOpenBook = { id -> navController.navigate(Routes.book(id)) },
                            onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                            onContinue = { bookId ->
                                playerViewModel.playBook(bookId) { ok ->
                                    if (ok) navController.navigate(Routes.PLAYER)
                                }
                            },
                        )
                    }
                    composable(
                        route = Routes.BOOK,
                        arguments = listOf(navArgument("bookId") { type = NavType.LongType }),
                    ) { entry ->
                        val bookId = entry.arguments?.getLong("bookId") ?: return@composable
                        BookDetailScreen(
                            bookId = bookId,
                            onBack = { navController.popBackStack() },
                            onPlayChapter = { chapterId ->
                                playerViewModel.playBook(bookId, chapterId) { ok ->
                                    if (ok) navController.navigate(Routes.PLAYER)
                                }
                            },
                            onContinue = {
                                playerViewModel.playBook(bookId) { ok ->
                                    if (ok) navController.navigate(Routes.PLAYER)
                                }
                            },
                            onPlayBookmark = { chapterId, positionMs ->
                                playerViewModel.playBook(bookId, chapterId, positionMs) { ok ->
                                    if (ok) navController.navigate(Routes.PLAYER)
                                }
                            },
                            onRescanApplied = { id, chapterId, positionMs ->
                                playerViewModel.refreshAfterRescan(
                                    bookId = id,
                                    chapterId = chapterId,
                                    positionMs = positionMs,
                                    wasPlaying = playerState.isPlaying && playerState.bookId == id,
                                )
                            },
                        )
                    }
                    composable(Routes.PLAYER) {
                        FullPlayerScreen(
                            state = playerState,
                            onBack = { navController.popBackStack() },
                            onToggle = { playerViewModel.togglePlayPause() },
                            onSeek = { playerViewModel.seekTo(it) },
                            onSeekBy = { playerViewModel.seekBy(it) },
                            onPrev = { playerViewModel.previousChapter() },
                            onNext = { playerViewModel.nextChapter() },
                            onSpeed = { playerViewModel.setSpeed(it) },
                            onSleep = { playerViewModel.setSleepMinutes(it) },
                            onSleepEndOfChapter = { playerViewModel.setSleepEndOfChapter() },
                            onAddBookmark = { playerViewModel.addBookmark() },
                        )
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        },
    )
}
