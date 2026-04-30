package com.piashmsu.aichat.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.piashmsu.aichat.data.AppContainer
import com.piashmsu.aichat.data.db.ConversationEntity
import com.piashmsu.aichat.data.db.MessageEntity
import com.piashmsu.aichat.data.db.Role
import com.piashmsu.aichat.data.prefs.PrefsSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatUiState(
    val conversationId: Long? = null,
    val title: String = "New chat",
    val messages: List<MessageEntity> = emptyList(),
    val streamingText: String = "",
    val isStreaming: Boolean = false,
    val backend: String = "",
    val modelName: String? = null,
    val error: String? = null,
    val thermalPaused: Boolean = false,
    val thermalTempC: Float? = null,
)

class ChatViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState(backend = container.llmRuntime.backend))
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var streamJob: Job? = null
    private var observeJob: Job? = null

    init {
        viewModelScope.launch {
            container.prefs.flow.collectLatest { snap ->
                // Use atomic update because the streaming loop below also
                // mutates _state from Dispatchers.IO — a non-atomic
                // read/copy/write would race and lose updates.
                _state.update { it.copy(modelName = snap.activeModelName) }
                // Loading a multi-GB GGUF blocks for several seconds. Move it
                // off the UI dispatcher so we don't trigger an ANR (which on
                // RedMagic / Android 13+ kicks the user back to the home
                // screen).
                withContext(Dispatchers.IO) {
                    container.llmRuntime.ensureLoaded(snap)
                }
            }
        }
        viewModelScope.launch {
            // Lightweight 5-second poll for thermal state. Replaced by sensor
            // listener in Phase 4.
            while (true) {
                val snap = container.thermalGuard.snapshot()
                _state.update {
                    it.copy(
                        thermalPaused = snap.paused,
                        thermalTempC = snap.temperatureC,
                    )
                }
                kotlinx.coroutines.delay(5_000)
            }
        }
    }

    fun openConversation(id: Long) {
        viewModelScope.launch {
            val conv = container.db.conversationDao().get(id) ?: return@launch
            _state.update { it.copy(conversationId = conv.id, title = conv.title) }
            startObservingMessages(conv.id)
        }
    }

    /**
     * Subscribe to the messages flow for [cid] and mirror it into ui state.
     * We cancel any previous observer first so a brand-new conversation
     * does not keep showing the old conversation's history.
     */
    private fun startObservingMessages(cid: Long) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            container.db.messageDao().observe(cid).collectLatest { msgs ->
                _state.update { it.copy(messages = msgs) }
            }
        }
    }

    fun startNew() {
        streamJob?.cancel()
        observeJob?.cancel()
        _state.update {
            ChatUiState(
                backend = container.llmRuntime.backend,
                modelName = it.modelName,
            )
        }
    }

    fun stop() {
        streamJob?.cancel()
        container.llmRuntime.cancel()
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.isStreaming) return
        if (_state.value.thermalPaused) {
            _state.update { it.copy(error = "Phone is hot — generation paused") }
            return
        }
        streamJob = viewModelScope.launch {
            try {
                val prefs = container.prefs.flow.first()
                val cid = ensureConversation(prefs, trimmed)
                val userMsg = MessageEntity(conversationId = cid, role = Role.User, content = trimmed)
                container.db.messageDao().insert(userMsg)
                val history = container.db.messageDao().list(cid)
                _state.update { it.copy(streamingText = "", isStreaming = true, error = null) }

                val sb = StringBuilder()
                // Throttle UI updates: accumulate tokens for ~70 ms before
                // pushing a fresh streamingText to the StateFlow. Without
                // this, every token (200+ per reply) triggers a full Compose
                // recomposition + markdown re-parse + syntax-highlight pass,
                // which freezes the main thread on long replies and the OS
                // ANR-kills the app back to the home screen.
                var lastEmit = 0L
                withContext(Dispatchers.IO) {
                    container.llmRuntime.generate(prefs, history.dropLast(1), trimmed).collect { chunk ->
                        sb.append(chunk)
                        val now = android.os.SystemClock.uptimeMillis()
                        if (now - lastEmit >= 70L) {
                            lastEmit = now
                            // _state.update is the atomic read-modify-write
                            // that prevents the IO streaming and Main
                            // thermal-poll coroutines from clobbering each
                            // other.
                            _state.update { it.copy(streamingText = sb.toString()) }
                        }
                    }
                    // Final flush so the user always sees the complete reply
                    // even if the last batch was within the throttle window.
                    if (sb.isNotEmpty()) {
                        _state.update { it.copy(streamingText = sb.toString()) }
                    }
                }
                if (sb.isNotEmpty()) {
                    container.db.messageDao().insert(
                        MessageEntity(conversationId = cid, role = Role.Assistant, content = sb.toString())
                    )
                }
            } catch (t: Throwable) {
                _state.update { it.copy(error = t.message ?: "Generation failed") }
            } finally {
                _state.update { it.copy(streamingText = "", isStreaming = false) }
            }
        }
    }

    private suspend fun ensureConversation(prefs: PrefsSnapshot, firstUserText: String): Long {
        val cid = _state.value.conversationId
        if (cid != null) return cid
        val title = firstUserText.take(40)
        val newId = container.db.conversationDao().insert(
            ConversationEntity(
                title = title,
                modelName = prefs.activeModelName,
                systemPrompt = prefs.systemPrompt,
            )
        )
        _state.update { it.copy(conversationId = newId, title = title) }
        // Crucial: subscribe to the messages flow for the freshly-created
        // conversation. Without this the user message and assistant reply
        // are saved to the DB but never re-emitted into the UI, so the
        // chat "disappears" once streamingText is cleared in finally.
        startObservingMessages(newId)
        return newId
    }
}
