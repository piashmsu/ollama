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

    init {
        viewModelScope.launch {
            container.prefs.flow.collectLatest { snap ->
                _state.value = _state.value.copy(modelName = snap.activeModelName)
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
                _state.value = _state.value.copy(
                    thermalPaused = snap.paused,
                    thermalTempC = snap.temperatureC,
                )
                kotlinx.coroutines.delay(5_000)
            }
        }
    }

    fun openConversation(id: Long) {
        viewModelScope.launch {
            val conv = container.db.conversationDao().get(id) ?: return@launch
            _state.value = _state.value.copy(conversationId = conv.id, title = conv.title)
            container.db.messageDao().observe(conv.id).collectLatest { msgs ->
                _state.value = _state.value.copy(messages = msgs)
            }
        }
    }

    fun startNew() {
        streamJob?.cancel()
        _state.value = ChatUiState(backend = container.llmRuntime.backend, modelName = _state.value.modelName)
    }

    fun stop() {
        streamJob?.cancel()
        container.llmRuntime.cancel()
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _state.value.isStreaming) return
        if (_state.value.thermalPaused) {
            _state.value = _state.value.copy(error = "Phone is hot — generation paused")
            return
        }
        streamJob = viewModelScope.launch {
            try {
                val prefs = container.prefs.flow.first()
                val cid = ensureConversation(prefs, trimmed)
                val userMsg = MessageEntity(conversationId = cid, role = Role.User, content = trimmed)
                container.db.messageDao().insert(userMsg)
                val history = container.db.messageDao().list(cid)
                _state.value = _state.value.copy(streamingText = "", isStreaming = true, error = null)

                val sb = StringBuilder()
                withContext(Dispatchers.IO) {
                    container.llmRuntime.generate(prefs, history.dropLast(1), trimmed).collect { chunk ->
                        sb.append(chunk)
                        _state.value = _state.value.copy(streamingText = sb.toString())
                    }
                }
                if (sb.isNotEmpty()) {
                    container.db.messageDao().insert(
                        MessageEntity(conversationId = cid, role = Role.Assistant, content = sb.toString())
                    )
                }
            } catch (t: Throwable) {
                _state.value = _state.value.copy(error = t.message ?: "Generation failed")
            } finally {
                _state.value = _state.value.copy(streamingText = "", isStreaming = false)
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
        _state.value = _state.value.copy(conversationId = newId, title = title)
        return newId
    }
}
