package com.iptvwhiz.app.ui.state

import com.iptvwhiz.app.AppContainer
import com.iptvwhiz.app.data.HealthSummary
import com.iptvwhiz.app.data.db.Channel
import com.iptvwhiz.app.data.db.HealthStatus
import com.iptvwhiz.app.data.ref
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed interface HealthCheckState {
    data object Idle : HealthCheckState
    data class Running(val done: Int, val total: Int) : HealthCheckState
    /** [offline]/[blocked] are the channels to offer hiding. */
    data class Done(val summary: HealthSummary, val offline: List<Channel>, val blocked: List<Channel>) : HealthCheckState
}

/** "Check channels" for any list, then "Hide N offline channels" (PLAN.md §18). */
class HealthCheck(private val container: AppContainer, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<HealthCheckState>(HealthCheckState.Idle)
    val state: StateFlow<HealthCheckState> = _state.asStateFlow()
    private var job: Job? = null

    fun start(channels: List<Channel>) {
        if (channels.isEmpty() || _state.value is HealthCheckState.Running) return
        job = scope.launch {
            val summary = container.health.check(channels) { done, total -> _state.value = HealthCheckState.Running(done, total) }
            val statuses = container.health.statuses().first()
            _state.value = HealthCheckState.Done(
                summary,
                offline = channels.filter { statuses[it.ref] == HealthStatus.Offline },
                blocked = channels.filter { statuses[it.ref] == HealthStatus.Blocked },
            )
        }
    }

    fun stop() {
        job?.cancel()
        _state.value = HealthCheckState.Idle
    }

    /** Hides the offline channels (and the blocked ones if asked), then clears the result. */
    fun hide(includeBlocked: Boolean) {
        val done = _state.value as? HealthCheckState.Done ?: return
        val channels = done.offline + if (includeBlocked) done.blocked else emptyList()
        scope.launch { container.overlay.setHiddenAll(channels, hidden = true) }
        _state.value = HealthCheckState.Idle
    }

    fun dismiss() {
        _state.value = HealthCheckState.Idle
    }
}
