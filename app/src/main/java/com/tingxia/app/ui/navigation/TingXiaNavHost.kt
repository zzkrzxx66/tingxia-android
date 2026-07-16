package com.tingxia.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showMini = playerState.bookId != null && currentRoute != Routes.PLAYER

    DisposableEffect(Unit) {
        playerViewModel.connect()
        onDispose { /* keep session alive for background play */ }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
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
                    .padding(innerPadding),
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
                                playerViewModel.playBook(bookId)
                                navController.navigate(Routes.PLAYER)
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
                                playerViewModel.playBook(bookId, chapterId)
                                navController.navigate(Routes.PLAYER)
                            },
                            onContinue = {
                                playerViewModel.playBook(bookId)
                                navController.navigate(Routes.PLAYER)
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
