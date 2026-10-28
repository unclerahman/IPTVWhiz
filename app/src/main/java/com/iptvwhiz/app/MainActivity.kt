package com.iptvwhiz.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.iptvwhiz.app.data.AppPrefs
import com.iptvwhiz.app.ui.isTelevision
import com.iptvwhiz.app.ui.mobile.MobileApp
import com.iptvwhiz.app.ui.tv.TvApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isTv = isTelevision()
        if (!isTv) {
            // The phone UI is dark-only, so ask for light system-bar icons even when the phone
            // itself is in light mode (auto() would pick dark icons and hide them).
            enableEdgeToEdge(
                statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            )
        }
        // Read synchronously so the first frame is already the right screen (no welcome flash).
        val hasPlaylist = (application as IptvWhizApp).container.prefs.activePlaylistId.value != AppPrefs.NONE
        setContent {
            if (isTv) TvApp(hasPlaylist) else MobileApp(hasPlaylist)
        }
    }
}
