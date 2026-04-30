package com.piashmsu.aichat.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.piashmsu.aichat.MainActivity
import com.piashmsu.aichat.R

/**
 * Foreground service whose only job is to anchor the OS process while one or
 * more model downloads are in flight. The actual download coroutine runs in
 * [DownloadController]'s application-scoped scope; this service just keeps the
 * process alive so the OS does not kill it when the app is backgrounded.
 *
 * The service starts itself in the foreground immediately and only stops when
 * the controller signals there is nothing left to do.
 */
class DownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(this)
        // intent action == ACTION_STOP shuts the service down; everything else
        // means "anchor the process while a download runs".
        return when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                // Don't let the OS resurrect us with a null intent — that
                // would cause a phantom "Downloading model 0%" notification.
                START_NOT_STICKY
            }
            else -> {
                startInForeground(this, displayName(intent), pct = 0)
                START_STICKY
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "downloads"
        const val NOTIF_ID = 4242
        const val EXTRA_NAME = "name"
        const val EXTRA_PCT = "pct"
        const val ACTION_STOP = "com.piashmsu.aichat.DOWNLOAD_STOP"

        fun start(context: Context, displayName: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra(EXTRA_NAME, displayName)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun updateNotification(context: Context, displayName: String, pct: Int) {
            ensureChannel(context)
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            runCatching { nm.notify(NOTIF_ID, buildNotif(context, displayName, pct)) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
                .apply { action = ACTION_STOP }
            runCatching { context.startService(intent) }
        }

        private fun displayName(intent: Intent?): String =
            intent?.getStringExtra(EXTRA_NAME) ?: "model"

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_downloads),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = context.getString(R.string.notif_channel_downloads_summary)
                    setShowBadge(false)
                }
            )
        }

        private fun buildNotif(context: Context, name: String, pct: Int): Notification {
            val pi = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(context.getString(R.string.notif_download_title, name))
                .setContentText("$pct%")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(100, pct, pct == 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi)
                .build()
        }

        private fun startInForeground(service: DownloadService, name: String, pct: Int) {
            val notif = buildNotif(service, name, pct)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching {
                    service.startForeground(
                        NOTIF_ID,
                        notif,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                    )
                }.onFailure {
                    // If foreground promotion fails (e.g. notification denied),
                    // we still let the service exist as a started service —
                    // the download continues from the application-scope
                    // coroutine. The OS may eventually kill us if backgrounded.
                    runCatching { service.startForeground(NOTIF_ID, notif) }
                }
            } else {
                runCatching { service.startForeground(NOTIF_ID, notif) }
            }
        }
    }
}
