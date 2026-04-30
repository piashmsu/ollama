package com.piashmsu.aichat.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.piashmsu.aichat.App
import com.piashmsu.aichat.MainActivity
import com.piashmsu.aichat.R
import okhttp3.Request
import java.io.File

/**
 * Foreground-promoted download worker.
 *
 * - Survives the app being backgrounded, the screen being locked, and the
 *   user navigating to a different tab.
 * - Posts a sticky notification with live progress so the OS keeps the worker
 *   alive even on aggressive vendors (RedMagic, MIUI, ColorOS, OneUI).
 * - Falls back to whatever was already on disk if the connection drops; on
 *   the next start the worker continues with an HTTP `Range` request, so
 *   multi-GB downloads can survive transient interruptions.
 */
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    /**
     * Returned to WorkManager when this worker is run as expedited.
     * WorkManager will own the foreground-service promotion if quota
     * is available, otherwise the worker still runs as normal but without
     * the sticky notification.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        ensureChannel()
        val displayName = inputData.getString(KEY_NAME) ?: "model"
        return buildForegroundInfo(displayName, 0, 100)
    }

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val displayName = inputData.getString(KEY_NAME) ?: url.substringAfterLast('/')
        val makeActive = inputData.getBoolean(KEY_MAKE_ACTIVE, true)

        val app = applicationContext as App
        val downloader = app.container.downloader
        val out = downloader.targetFileFor(url)
        val tmp = File(out.parentFile, out.name + ".part")
        val resumeFrom = if (tmp.exists()) tmp.length() else 0L

        ensureChannel()
        Log.i(TAG, "doWork start url=$url resumeFrom=$resumeFrom")

        try {
            val req = Request.Builder().url(url).apply {
                if (resumeFrom > 0L) addHeader("Range", "bytes=$resumeFrom-")
            }.build()

            app.container.httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 206) {
                    return Result.retry()
                }
                val body = resp.body ?: return Result.retry()
                val total = (body.contentLength().takeIf { it > 0 } ?: 0L) + resumeFrom

                // append=true so a Range-resumed download keeps existing
                // bytes instead of truncating the .part file from byte 0.
                java.io.FileOutputStream(tmp, resumeFrom > 0L).use { sink ->
                    body.byteStream().use { source ->
                        val buf = ByteArray(64 * 1024)
                        var read = resumeFrom
                        var lastNotify = 0L
                        while (true) {
                            val n = source.read(buf)
                            if (n <= 0) break
                            sink.write(buf, 0, n)
                            read += n
                            if (read - lastNotify > 1024 * 1024) {
                                val pct = if (total > 0) ((read * 100) / total).toInt().coerceAtMost(100) else 0
                                setProgress(workDataOf(KEY_BYTES to read, KEY_TOTAL to total, KEY_PCT to pct))
                                postProgressNotification(displayName, pct)
                                lastNotify = read
                            }
                            if (isStopped) {
                                // graceful: keep .part so next enqueue resumes
                                return Result.retry()
                            }
                        }
                    }
                }
            }
            if (!tmp.renameTo(out)) {
                tmp.copyTo(out, overwrite = true); tmp.delete()
            }
            if (makeActive) {
                app.container.prefs.update {
                    it.setActiveModel(out.absolutePath, displayName)
                }
            }
            clearProgressNotification()
            Log.i(TAG, "doWork success $out")
            return Result.success(workDataOf(KEY_RESULT_PATH to out.absolutePath))
        } catch (t: Throwable) {
            Log.w(TAG, "doWork error: ${t.message}")
            clearProgressNotification()
            return if (runAttemptCount < 3) Result.retry()
            else Result.failure(workDataOf(KEY_ERROR to (t.message ?: "download failed")))
        }
    }

    private fun postProgressNotification(name: String, pct: Int) {
        val nm = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.notif_download_title, name))
            .setContentText("$pct%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, pct, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching { nm.notify(notifId(), notif) }
    }

    private fun clearProgressNotification() {
        val nm = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.cancel(notifId()) }
    }

    private fun notifId(): Int = NOTIF_BASE + (id.hashCode() and 0xFFFF)

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = applicationContext.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        applicationContext.getString(R.string.notif_channel_downloads),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = applicationContext.getString(R.string.notif_channel_downloads_summary)
                    }
                )
            }
        }
    }

    private fun buildForegroundInfo(name: String, pct: Int, max: Int): ForegroundInfo {
        val pendingIntent = android.app.PendingIntent.getActivity(
            applicationContext,
            0,
            android.content.Intent(applicationContext, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val notif: Notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.notif_download_title, name))
            .setContentText("$pct%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(max, pct, pct == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()
        // Each download needs its own notification id so simultaneous
        // downloads don't overwrite each other.
        val nid = notifId()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(nid, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(nid, notif)
        }
    }

    companion object {
        const val TAG = "DownloadWorker"
        const val WORK_NAME_PREFIX = "model_download_"
        const val CHANNEL_ID = "downloads"
        const val NOTIF_BASE = 4242
        const val KEY_URL = "url"
        const val KEY_NAME = "name"
        const val KEY_MAKE_ACTIVE = "make_active"
        const val KEY_BYTES = "bytes"
        const val KEY_TOTAL = "total"
        const val KEY_PCT = "pct"
        const val KEY_RESULT_PATH = "result_path"
        const val KEY_ERROR = "error"
    }
}

private fun workDataOf(vararg pairs: Pair<String, Any>): Data {
    val b = Data.Builder()
    for ((k, v) in pairs) {
        when (v) {
            is Int -> b.putInt(k, v)
            is Long -> b.putLong(k, v)
            is Float -> b.putFloat(k, v)
            is Double -> b.putDouble(k, v)
            is Boolean -> b.putBoolean(k, v)
            is String -> b.putString(k, v)
            else -> b.putString(k, v.toString())
        }
    }
    return b.build()
}
