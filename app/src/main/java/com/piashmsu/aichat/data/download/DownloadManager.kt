package com.piashmsu.aichat.data.download

import android.content.Context
import android.util.Log
import com.piashmsu.aichat.App
import com.piashmsu.aichat.data.prefs.AppPrefs
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/** UI-friendly status of a background download. */
data class DownloadStatus(
    val pct: Int = 0,
    val bytes: Long = 0L,
    val total: Long = 0L,
    val state: State = State.Idle,
    val error: String? = null,
) {
    enum class State { Idle, Pending, Running, Succeeded, Failed, Cancelled }
}

/**
 * Direct, deterministic background downloader. Bypasses WorkManager because
 * aggressive vendor schedulers (RedMagic / ColorOS / MIUI / OneUI) routinely
 * leave WorkManager jobs in `ENQUEUED` state for minutes.
 *
 * Design:
 *   - The download runs in the [App.applicationScope] so it survives the
 *     Activity / Composable lifetime. Tab switches and screen rotations do
 *     not interrupt it.
 *   - A [DownloadService] is started in the foreground while at least one
 *     download is active so the OS keeps the process alive when the user
 *     locks the phone or switches apps.
 *   - HTTP `Range` requests resume from the existing `.part` file.
 */
class DownloadManager(
    private val context: Context,
    private val httpClient: OkHttpClient,
    private val downloader: ModelDownloader,
    private val prefs: AppPrefs,
) {
    private val statuses: MutableMap<String, MutableStateFlow<DownloadStatus>> = mutableMapOf()
    private val jobs: MutableMap<String, Job> = mutableMapOf()
    private val activeNames: MutableMap<String, String> = mutableMapOf()

    @Synchronized
    fun status(url: String): Flow<DownloadStatus> {
        return getOrCreate(url).asStateFlow()
    }

    @Synchronized
    fun enqueue(url: String, displayName: String, makeActive: Boolean = true): String {
        val flow = getOrCreate(url)
        // De-dupe: if already running / pending, ignore.
        if (jobs[url]?.isActive == true) return url
        flow.value = DownloadStatus(state = DownloadStatus.State.Pending)
        activeNames[url] = displayName

        val app = context.applicationContext as App
        // Anchor process with the foreground service so the OS does not kill us.
        DownloadService.start(app, displayName)

        val job = app.applicationScope.launch {
            runDownload(url, displayName, makeActive, flow)
        }
        jobs[url] = job
        job.invokeOnCompletion { onJobFinished(url) }
        return url
    }

    fun cancel(url: String) {
        jobs[url]?.cancel()
        synchronized(this) {
            statuses[url]?.value = statuses[url]?.value?.copy(
                state = DownloadStatus.State.Cancelled,
            ) ?: DownloadStatus(state = DownloadStatus.State.Cancelled)
        }
    }

    private fun getOrCreate(url: String): MutableStateFlow<DownloadStatus> =
        statuses.getOrPut(url) { MutableStateFlow(DownloadStatus()) }

    @Synchronized
    private fun onJobFinished(url: String) {
        jobs.remove(url)
        activeNames.remove(url)
        if (jobs.isEmpty()) {
            DownloadService.stop(context)
        }
    }

    private suspend fun runDownload(
        url: String,
        displayName: String,
        makeActive: Boolean,
        flow: MutableStateFlow<DownloadStatus>,
    ) {
        val out = downloader.targetFileFor(url)
        val tmp = File(out.parentFile, out.name + ".part")
        val resumeFrom = if (tmp.exists()) tmp.length() else 0L
        Log.i(TAG, "start url=$url resumeFrom=$resumeFrom")

        flow.value = DownloadStatus(
            state = DownloadStatus.State.Running,
            bytes = resumeFrom,
        )

        try {
            val req = Request.Builder().url(url).apply {
                if (resumeFrom > 0L) addHeader("Range", "bytes=$resumeFrom-")
            }.build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful && resp.code != 206) {
                    flow.value = flow.value.copy(
                        state = DownloadStatus.State.Failed,
                        error = "HTTP ${resp.code}",
                    )
                    return
                }
                val body = resp.body ?: run {
                    flow.value = flow.value.copy(
                        state = DownloadStatus.State.Failed, error = "empty body",
                    )
                    return
                }
                val total = (body.contentLength().takeIf { it > 0 } ?: 0L) + resumeFrom

                FileOutputStream(tmp, resumeFrom > 0L).use { sink ->
                    body.byteStream().use { source ->
                        val buf = ByteArray(64 * 1024)
                        var read = resumeFrom
                        var lastEmit = 0L
                        while (true) {
                            val n = source.read(buf)
                            if (n <= 0) break
                            sink.write(buf, 0, n)
                            read += n
                            if (read - lastEmit > 256 * 1024) {
                                val pct = if (total > 0) ((read * 100) / total).toInt().coerceAtMost(100) else 0
                                flow.value = flow.value.copy(
                                    bytes = read, total = total, pct = pct,
                                    state = DownloadStatus.State.Running,
                                )
                                DownloadService.updateNotification(context, displayName, pct)
                                lastEmit = read
                            }
                        }
                    }
                }
            }

            if (!tmp.renameTo(out)) {
                tmp.copyTo(out, overwrite = true); tmp.delete()
            }
            if (makeActive) {
                prefs.update { it.setActiveModel(out.absolutePath, displayName) }
            }
            flow.value = flow.value.copy(
                state = DownloadStatus.State.Succeeded,
                pct = 100,
                bytes = out.length(),
                total = out.length(),
            )
            Log.i(TAG, "done url=$url -> $out")
        } catch (ce: kotlinx.coroutines.CancellationException) {
            flow.value = flow.value.copy(state = DownloadStatus.State.Cancelled)
            throw ce
        } catch (t: Throwable) {
            Log.w(TAG, "fail url=$url: ${t.message}")
            flow.value = flow.value.copy(
                state = DownloadStatus.State.Failed,
                error = t.message ?: "download failed",
            )
        }
    }

    companion object {
        const val TAG = "DownloadManager"
    }
}
