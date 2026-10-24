package com.iptvwhiz.app.data.epg

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.iptvwhiz.app.IptvWhizApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/** Updates the guide for one playlist, or for all of them when no id is given. */
class EpgWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as IptvWhizApp).container
        val requested = inputData.getLong(KEY_PLAYLIST, -1)
        val ids = if (requested >= 0) listOf(requested) else container.db.playlists().getAll().map { it.id }
        ids.forEach { id ->
            container.epg.update(id) { done, total ->
                setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
            }
        }
        return Result.success()
    }

    companion object {
        internal const val KEY_PLAYLIST = "playlist"
        internal const val KEY_DONE = "done"
        internal const val KEY_TOTAL = "total"
    }
}

/** Guide update progress for the settings screen. */
sealed interface EpgWork {
    data object Idle : EpgWork
    data class Running(val done: Int, val total: Int) : EpgWork
}

object EpgScheduler {
    private const val PERIODIC = "epg-periodic"
    private fun oneOff(playlistId: Long) = "epg-$playlistId"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Once a day, for every playlist. Replaces an older schedule with a different interval. */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<EpgWorker>(24, TimeUnit.HOURS)
            .setConstraints(network)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Right away, e.g. after adding or refreshing a playlist, or from "Update now". */
    fun updateNow(context: Context, playlistId: Long) {
        val request = OneTimeWorkRequestBuilder<EpgWorker>()
            .setConstraints(network)
            .setInputData(workDataOf(EpgWorker.KEY_PLAYLIST to playlistId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(oneOff(playlistId), ExistingWorkPolicy.REPLACE, request)
    }

    fun observe(context: Context, playlistId: Long): Flow<EpgWork> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(oneOff(playlistId)).map { infos ->
            val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
            if (running == null) {
                EpgWork.Idle
            } else {
                EpgWork.Running(running.progress.getInt(EpgWorker.KEY_DONE, 0), running.progress.getInt(EpgWorker.KEY_TOTAL, 0))
            }
        }
}
