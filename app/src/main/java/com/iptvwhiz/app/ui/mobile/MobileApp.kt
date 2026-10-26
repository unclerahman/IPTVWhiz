package com.iptvwhiz.app.ui.mobile

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.iptvwhiz.app.ui.Routes
import com.iptvwhiz.app.ui.theme.IptvWhizMobileTheme
import com.iptvwhiz.app.ui.tv.goHome

@Composable
fun MobileApp(hasPlaylist: Boolean) {
    IptvWhizMobileTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val nav = rememberNavController()
            val back: () -> Unit = { nav.popBackStack() }
            NavHost(navController = nav, startDestination = if (hasPlaylist) Routes.HOME else Routes.WELCOME) {
                composable(Routes.WELCOME) {
                    val isFirstRun = nav.previousBackStackEntry == null
                    MobileWelcomeScreen(
                        isFirstRun = isFirstRun,
                        onAddM3u = { nav.navigate(Routes.ADD_M3U) },
                        onAddXtream = { nav.navigate(Routes.ADD_XTREAM) },
                        onSecondary = { if (isFirstRun) nav.goHome() else nav.popBackStack() },
                    )
                }
                composable(Routes.HOME) {
                    MobileHomeScreen(
                        onPlay = { channel, browseKey -> nav.navigate(Routes.player(channel, browseKey)) },
                        onPlayQueue = { nav.navigate(Routes.playerQueue(it)) },
                        onAddPlaylist = { nav.navigate(Routes.WELCOME) },
                        onOpenRoute = { nav.navigate(it) },
                    )
                }
                composable(Routes.ADD_M3U) { MobileAddM3uScreen(onDone = { nav.goHome() }, onBack = back) }
                composable(Routes.ADD_XTREAM) { MobileAddXtreamScreen(onDone = { nav.goHome() }, onBack = back) }
                composable(Routes.PLAYLISTS) {
                    MobilePlaylistsScreen(onAddPlaylist = { nav.navigate(Routes.WELCOME) }, onBack = back)
                }
                composable(Routes.BACKUP) { MobileBackupScreen(onBack = back) }
                composable(Routes.EPG) { MobileEpgSettingsScreen(onBack = back) }
                composable(Routes.WATCHLISTS) {
                    MobileWatchlistsScreen(onOpenList = { nav.navigate(Routes.watchlist(it)) }, onBack = back)
                }
                composable(Routes.WATCHLIST, arguments = Routes.manageGroupArguments) {
                    MobileWatchlistScreen(onBack = back)
                }
                composable(Routes.MANAGE) {
                    MobileManageScreen(onOpenGroup = { nav.navigate(Routes.manageGroup(it)) }, onBack = back)
                }
                composable(Routes.MANAGE_GROUP, arguments = Routes.manageGroupArguments) {
                    MobileManageGroupScreen(onBack = back)
                }
                composable(Routes.PLAYER, arguments = Routes.playerArguments) {
                    MobilePlayerScreen(onBack = back)
                }
            }
        }
    }
}
