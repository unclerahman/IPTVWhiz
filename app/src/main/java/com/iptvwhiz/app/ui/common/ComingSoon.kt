package com.iptvwhiz.app.ui.common

import android.content.Context
import android.widget.Toast
import com.iptvwhiz.app.R

fun Context.showComingSoon() {
    Toast.makeText(this, R.string.coming_soon, Toast.LENGTH_SHORT).show()
}
