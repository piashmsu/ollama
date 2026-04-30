package com.piashmsu.aichat.data.download

import android.content.Context
import androidx.lifecycle.asFlow
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** UI-friendly status of a background download. */
data class DownloadStatus(
    val pct: Int = 0,
    val bytes: Long = 0L,
    val total: Long = 0L,
    val state: State = State.Pending,
    val error: String? = null,
) {
    enum class State { Pending, Running, Succeeded, Failed, Cancelled }
}

class DownloadManager(private val context: Context) {

    fun enqueue(url: String, displayName: String, makeActive: Boolean = true): String {
        val tag = workName(url)
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                Data.Builder()
                    .putString(DownloadWorker.KEY_URL, url)
                    .putString(DownloadWorker.KEY_NAME, displayName)
                    .putBoolean(DownloadWorker.KEY_MAKE_ACTIVE, makeActive)
                    .build()
            )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .addTag(tag)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(tag, ExistingWorkPolicy.KEEP, req)
        return tag
    }

    fun cancel(url: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(url))
    }

    fun status(url: String): Flow<DownloadStatus> {
        val name = workName(url)
        return WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkLiveData(name)
            .asFlow()
            .map { infos ->
                val info = infos.firstOrNull() ?: return@map DownloadStatus()
                toStatus(info)
            }
    }

    private fun toStatus(info: WorkInfo): DownloadStatus {
        val data = if (info.state == WorkInfo.State.SUCCEEDED) info.outputData else info.progress
        val pct = data.getInt(DownloadWorker.KEY_PCT, 0)
        val bytes = data.getLong(DownloadWorker.KEY_BYTES, 0L)
        val total = data.getLong(DownloadWorker.KEY_TOTAL, 0L)
        val err = if (info.state == WorkInfo.State.FAILED) info.outputData.getString(DownloadWorker.KEY_ERROR) else null
        val st = when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> DownloadStatus.State.Pending
            WorkInfo.State.RUNNING -> DownloadStatus.State.Running
            WorkInfo.State.SUCCEEDED -> DownloadStatus.State.Succeeded
            WorkInfo.State.FAILED -> DownloadStatus.State.Failed
            WorkInfo.State.CANCELLED -> DownloadStatus.State.Cancelled
        }
        return DownloadStatus(
            pct = pct,
            bytes = bytes,
            total = total,
            state = st,
            error = err,
        )
    }

    private fun workName(url: String): String =
        DownloadWorker.WORK_NAME_PREFIX + url.hashCode().toString()
}
