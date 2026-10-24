package com.iptvwhiz.app.ui.common

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Whether the system has a real file picker for [action]. Android TV ships a stub
 * ("frameworkpackagestubs") that accepts the request and returns nothing, so asking the
 * package manager isn't enough on its own.
 */
fun Context.hasDocumentPicker(action: String = Intent.ACTION_OPEN_DOCUMENT): Boolean {
    val intent = Intent(action).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
    val handlers = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return handlers.any { !it.activityInfo.packageName.contains("stub", ignoreCase = true) }
}
