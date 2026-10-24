package com.iptvwhiz.app.ui.tv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import com.iptvwhiz.app.ui.Routes
import com.iptvwhiz.app.ui.theme.IptvWhizTvTheme

@Composable
fun TvApp(hasPlaylist: Boolean) {
    IptvWhizTvTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RectangleShape,
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        ) {
            val nav = rememberNavController()
            NavHost(navController = nav, startDestination = if (hasPlaylist) Routes.HOME else Routes.WELCOME) {
                composable(Routes.WELCOME) {
                    val isFirstRun = nav.previousBackStackEntry == null
                    TvWelcomeScreen(
                        isFirstRun = isFirstRun,
                        onAddM3u = { nav.navigate(Routes.ADD_M3U) },
                        onAddXtream = { nav.navigate(Routes.ADD_XTREAM) },
                        onSecondary = { if (isFirstRun) nav.goHome() else nav.popBackStack() },
                    )
                }
                composable(Routes.HOME) {
                    TvHomeScreen(
                        onPlay = { channel, group ->
                            nav.navigate(Routes.player(channel.playlistId, group, channel.stableKey))
                        },
                        onAddPlaylist = { nav.navigate(Routes.WELCOME) },
                        onOpenRoute = { nav.navigate(it) },
                    )
                }
                composable(Routes.ADD_M3U) { TvAddM3uScreen(onDone = { nav.goHome() }) }
                composable(Routes.ADD_XTREAM) { TvAddXtreamScreen(onDone = { nav.goHome() }) }
                composable(Routes.PLAYLISTS) {
                    TvPlaylistsScreen(onAddPlaylist = { nav.navigate(Routes.WELCOME) })
                }
                composable(Routes.MANAGE) { TvManageScreen() }
                composable(Routes.BACKUP) { TvBackupScreen() }
                composable(Routes.PLAYER, arguments = Routes.playerArguments) {
                    TvPlayerScreen(onBack = { nav.popBackStack() })
                }
            }
        }
    }
}

/** Home becomes the only screen on the stack (after adding a playlist, or "Explore first"). */
fun NavHostController.goHome() {
    navigate(Routes.HOME) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
