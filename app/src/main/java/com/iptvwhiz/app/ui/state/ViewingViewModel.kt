package com.iptvwhiz.app.ui.state

import androidx.lifecycle.ViewModel
import com.iptvwhiz.app.AppContainer
import kotlinx.coroutines.flow.StateFlow

/** Settings → Viewing options. */
class ViewingViewModel(private val container: AppContainer) : ViewModel() {
    val showQuality: StateFlow<Boolean> = container.prefs.showQuality
    val hideBlocked: StateFlow<Boolean> = container.prefs.hideBlocked

    fun setShowQuality(on: Boolean) = container.prefs.setShowQuality(on)

    fun setHideBlocked(on: Boolean) = container.prefs.setHideBlocked(on)
}
