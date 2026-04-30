package com.piashmsu.aichat.llm

import com.piashmsu.aichat.data.db.MessageEntity
import com.piashmsu.aichat.data.db.Role
import com.piashmsu.aichat.data.prefs.AppPrefs
import com.piashmsu.aichat.data.prefs.PrefsSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.io.File

data class GenerationParams(
    val systemPrompt: String,
    val temperature: Float,
    val topP: Float,
    val topK: Int,
    val maxTokens: Int,
    val tokenRateCap: Int,
    val threads: Int,
    val contextSize: Int,
)

interface LlamaEngine {
    val backend: String
    fun open(modelPath: String, contextSize: Int, threads: Int): Long
    fun close(handle: Long)
    fun cancel(handle: Long)
    fun generate(handle: Long, prompt: String, params: GenerationParams): Flow<String>
}

sealed interface RuntimeState {
    data object Idle : RuntimeState
    data class Loading(val name: String) : RuntimeState
    data class Ready(val name: String, val path: String) : RuntimeState
    data class Failed(val reason: String) : RuntimeState
}

class LlmRuntime(
    private val engine: LlamaEngine,
    private val prefs: AppPrefs,
) {
    private val _state = MutableStateFlow<RuntimeState>(RuntimeState.Idle)
    val state: StateFlow<RuntimeState> = _state.asStateFlow()

    @Volatile private var handle: Long = 0L
    @Volatile private var loadedPath: String? = null

    val backend: String get() = engine.backend

    fun ensureLoaded(snapshot: PrefsSnapshot) {
        val path = snapshot.activeModelPath ?: return
        if (path == loadedPath && handle != 0L) return
        val name = snapshot.activeModelName ?: File(path).nameWithoutExtension
        _state.value = RuntimeState.Loading(name)
        try {
            if (handle != 0L) engine.close(handle)
            handle = engine.open(path, snapshot.contextSize, snapshot.threads)
            loadedPath = path
            _state.value = RuntimeState.Ready(name = name, path = path)
        } catch (t: Throwable) {
            _state.value = RuntimeState.Failed(t.message ?: "load error")
        }
    }

    fun cancel() {
        if (handle != 0L) engine.cancel(handle)
    }

    fun shutdown() {
        if (handle != 0L) engine.close(handle)
        handle = 0
        loadedPath = null
        _state.value = RuntimeState.Idle
    }

    /** Build a chat prompt from history + new user turn using a generic ChatML-ish format. */
    private fun buildPrompt(snapshot: PrefsSnapshot, history: List<MessageEntity>, userMessage: String): String {
        val sb = StringBuilder()
        sb.append("<|system|>\n").append(snapshot.systemPrompt.trim()).append("\n<|end|>\n")
        for (m in history) {
            val tag = when (m.role) {
                Role.User -> "<|user|>"
                Role.Assistant -> "<|assistant|>"
                Role.System -> "<|system|>"
                Role.Tool -> "<|tool|>"
            }
            sb.append(tag).append('\n').append(m.content).append("\n<|end|>\n")
        }
        sb.append("<|user|>\n").append(userMessage).append("\n<|end|>\n")
        sb.append("<|assistant|>\n")
        return sb.toString()
    }

    fun generate(
        snapshot: PrefsSnapshot,
        history: List<MessageEntity>,
        userMessage: String,
    ): Flow<String> {
        ensureLoaded(snapshot)
        val h = handle
        if (h == 0L) {
            return kotlinx.coroutines.flow.flow {
                throw IllegalStateException("No model loaded")
            }
        }
        val prompt = buildPrompt(snapshot, history, userMessage)
        val params = GenerationParams(
            systemPrompt = snapshot.systemPrompt,
            temperature = snapshot.temperature,
            topP = snapshot.topP,
            topK = snapshot.topK,
            maxTokens = snapshot.maxTokens,
            tokenRateCap = snapshot.tokenRateCap,
            threads = snapshot.threads,
            contextSize = snapshot.contextSize,
        )
        return engine.generate(h, prompt, params).flowOn(Dispatchers.IO)
    }
}

/** Wraps the JNI bridge in `LlamaNative` and exposes it via the LlamaEngine interface. */
class LlamaCppEngine : LlamaEngine {
    override val backend: String get() = LlamaNative.backendInfo()

    override fun open(modelPath: String, contextSize: Int, threads: Int): Long =
        LlamaNative.open(modelPath, contextSize, threads)

    override fun close(handle: Long) = LlamaNative.close(handle)
    override fun cancel(handle: Long) = LlamaNative.cancel(handle)

    override fun generate(handle: Long, prompt: String, params: GenerationParams): Flow<String> =
        callbackFlow {
            val cb = object : LlamaNative.Callback {
                override fun onToken(token: String) { trySend(token) }
                override fun onDone(cancelled: Boolean) { close() }
            }
            LlamaNative.generate(
                handle = handle,
                prompt = prompt,
                temperature = params.temperature,
                topP = params.topP,
                topK = params.topK,
                maxTokens = params.maxTokens,
                tokenRateCap = params.tokenRateCap,
                callback = cb,
            )
            awaitClose { LlamaNative.cancel(handle) }
        }
}
