package com.iptvwhiz.app.ui

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/** True on Android TV / Google TV / Fire TV, where the 10-foot remote UI is used. */
fun Context.isTelevision(): Boolean {
    val uiMode = getSystemService(UiModeManager::class.java)
    return uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}
