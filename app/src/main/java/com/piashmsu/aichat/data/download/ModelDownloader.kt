package com.piashmsu.aichat.data.download

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

data class CuratedModel(
    val id: String,
    val displayName: String,
    val sizeMb: Int,
    val url: String,
    val description: String,
    val recommended: Boolean = false,
)

object ModelCatalog {
    val curated: List<CuratedModel> = listOf(
        CuratedModel(
            id = "phi3-mini-4k-q4",
            displayName = "Phi-3 Mini 4K (Q4_K_M)",
            sizeMb = 2400,
            url = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf",
            description = "Microsoft Phi-3 Mini, 3.8B params. Fast and low-heat. Great default.",
            recommended = true,
        ),
        CuratedModel(
            id = "qwen25-3b-q4",
            displayName = "Qwen2.5 3B Instruct (Q4_K_M)",
            sizeMb = 2000,
            url = "https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf",
            description = "Multilingual, including Bangla. Light on the phone.",
        ),
        CuratedModel(
            id = "llama32-3b-q4",
            displayName = "Llama 3.2 3B Instruct (Q4_K_M)",
            sizeMb = 2000,
            url = "https://huggingface.co/bartowski/Llama-3.2-3B-Instruct-GGUF/resolve/main/Llama-3.2-3B-Instruct-Q4_K_M.gguf",
            description = "Solid all-rounder for chat and code.",
        ),
        CuratedModel(
            id = "dolphin-llama3-8b-q4",
            displayName = "Dolphin 2.9 Llama3 8B (Q4_K_M)",
            sizeMb = 4700,
            url = "https://huggingface.co/cognitivecomputations/dolphin-2.9-llama3-8b-gguf/resolve/main/dolphin-2.9-llama3-8b-q4_K_M.gguf",
            description = "Smarter, less censored. Heavier on RAM and heat — best with active fan.",
        ),
        CuratedModel(
            id = "gemma2-2b-q4",
            displayName = "Gemma 2 2B IT (Q4_K_M)",
            sizeMb = 1700,
            url = "https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf",
            description = "Tiniest of the bunch. Coolest running.",
        ),
    )
}

data class DownloadProgress(
    val bytesRead: Long,
    val totalBytes: Long,
    val done: Boolean = false,
    val error: String? = null,
    val outFile: File? = null,
)

class ModelDownloader(
    private val context: Context,
    private val client: OkHttpClient,
) {
    fun modelsDir(): File = File(context.filesDir, "models").apply { mkdirs() }

    fun listLocalModels(): List<File> =
        modelsDir().listFiles { f -> f.isFile && f.name.endsWith(".gguf") }?.toList().orEmpty()

    fun targetFileFor(url: String): File {
        val name = url.substringAfterLast('/').ifBlank { "model.gguf" }
        return File(modelsDir(), name)
    }

    fun download(url: String): Flow<DownloadProgress> = flow {
        val out = targetFileFor(url)
        if (out.exists() && out.length() > 0) {
            emit(DownloadProgress(out.length(), out.length(), done = true, outFile = out))
            return@flow
        }
        val tmp = File(out.parentFile, out.name + ".part")
        tmp.delete()

        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                emit(DownloadProgress(0, 0, done = true, error = "HTTP ${resp.code}"))
                return@flow
            }
            val body = resp.body ?: run {
                emit(DownloadProgress(0, 0, done = true, error = "Empty body"))
                return@flow
            }
            val total = body.contentLength()
            val source = body.byteStream()
            tmp.outputStream().use { sink ->
                val buf = ByteArray(64 * 1024)
                var read = 0L
                var lastEmit = 0L
                while (true) {
                    val n = source.read(buf)
                    if (n <= 0) break
                    sink.write(buf, 0, n)
                    read += n
                    if (read - lastEmit > 256 * 1024) {
                        emit(DownloadProgress(read, total))
                        lastEmit = read
                    }
                }
            }
            tmp.renameTo(out)
            emit(DownloadProgress(out.length(), total.coerceAtLeast(out.length()), done = true, outFile = out))
        }
    }.flowOn(Dispatchers.IO)
}
