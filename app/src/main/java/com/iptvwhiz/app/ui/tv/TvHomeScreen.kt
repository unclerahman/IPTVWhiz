package com.iptvwhiz.app.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text
import com.iptvwhiz.app.BuildConfig
import com.iptvwhiz.app.R
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.ui.TopLevel
import com.iptvwhiz.app.ui.common.AdSlot
import com.iptvwhiz.app.ui.common.BackToExit
import com.iptvwhiz.app.ui.common.emptyState
import com.iptvwhiz.app.ui.common.settingsEntries
import com.iptvwhiz.app.ui.common.showComingSoon
import com.iptvwhiz.app.ui.theme.TvSafeArea

@Composable
fun TvHomeScreen(
    onPlay: (Channel, group: String) -> Unit,
    onAddPlaylist: () -> Unit,
    onOpenRoute: (String) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(TopLevel.Live) }

    // Back: other section → Live TV → "press again to exit". The later handler wins.
    BackToExit()
    BackHandler(enabled = selected != TopLevel.Live) { selected = TopLevel.Live }

    NavigationDrawer(
        drawerContent = {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = TvSafeArea.Vertical, horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                TopLevel.entries.forEach { destination ->
                    NavigationDrawerItem(
                        selected = selected == destination,
                        onClick = { selected = destination },
                        leadingContent = { Icon(destination.icon, contentDescription = null) },
                    ) {
                        Text(stringResource(destination.label))
                    }
                }
            }
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = TvSafeArea.Horizontal, top = TvSafeArea.Vertical, bottom = TvSafeArea.Vertical),
        ) {
            when (selected) {
                TopLevel.Live -> TvLiveContent(onPlay = onPlay, onAddPlaylist = onAddPlaylist)
                TopLevel.Favorites -> TvFavoritesContent(onPlay = onPlay)
                TopLevel.Settings -> TvSettings(onOpenRoute)
                else -> selected.emptyState()?.let { TvEmptyState(it) }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvSettings(onOpenRoute: (String) -> Unit) {
    val context = LocalContext.current
    val firstItem = remember { FocusRequester() }
    Column {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        // Entering from the drawer lands on the last focused row, or the first row, never a random middle one.
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .focusRestorer { firstItem },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(settingsEntries) { index, entry ->
                ListItem(
                    selected = false,
                    onClick = { entry.route?.let(onOpenRoute) ?: context.showComingSoon() },
                    modifier = if (index == 0) Modifier.focusRequester(firstItem) else Modifier,
                    headlineContent = { Text(stringResource(entry.title)) },
                    supportingContent = {
                        Text(stringResource(entry.description, BuildConfig.VERSION_NAME))
                    },
                )
            }
            item { AdSlot() }
        }
    }
}
