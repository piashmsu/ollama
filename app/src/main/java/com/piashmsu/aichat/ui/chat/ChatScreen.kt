package com.piashmsu.aichat.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.piashmsu.aichat.R
import com.piashmsu.aichat.data.AppContainer
import com.piashmsu.aichat.data.db.MessageEntity
import com.piashmsu.aichat.data.db.Role
import com.piashmsu.aichat.ui.components.MarkdownText

@Composable
fun ChatRoute(container: AppContainer, conversationId: Long? = null) {
    val vm = remember(conversationId) { ChatViewModel(container) }
    LaunchedEffect(conversationId) {
        if (conversationId != null) vm.openConversation(conversationId)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    ChatScreen(
        state = state,
        onSend = vm::send,
        onStop = vm::stop,
        onNew = vm::startNew,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatScreen(
    state: ChatUiState,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onNew: () -> Unit,
) {
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current
    var input by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(state.messages.size, state.streamingText) {
        val total = state.messages.size + (if (state.streamingText.isNotEmpty()) 1 else 0)
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Column {
                        Text(
                            text = state.title.ifBlank { stringResource(R.string.app_name) },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        val subtitle = buildString {
                            append(state.modelName ?: "no model")
                            append(" · ")
                            append(state.backend)
                            state.thermalTempC?.let { append(" · ${"%.1f".format(it)}°C") }
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNew) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.chat_new))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            if (state.thermalPaused) {
                ThermalBanner()
            }
            if (state.error != null) {
                Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            if (state.messages.isEmpty() && state.streamingText.isEmpty()) {
                EmptyChatHero()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 12.dp,
                        vertical = 8.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(state.messages, key = { _, m -> m.id }) { _, m ->
                        MessageBubble(m)
                    }
                    if (state.streamingText.isNotEmpty()) {
                        item {
                            MessageBubble(
                                MessageEntity(
                                    id = -1, conversationId = state.conversationId ?: 0,
                                    role = Role.Assistant, content = state.streamingText,
                                ),
                                streaming = true,
                            )
                        }
                    } else if (state.isStreaming) {
                        item { ThinkingIndicator() }
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ChatInputBar(
                value = input,
                onChange = { input = it },
                onSend = {
                    if (input.isNotBlank()) {
                        onSend(input)
                        input = ""
                        focus.clearFocus()
                    }
                },
                onStop = onStop,
                isStreaming = state.isStreaming,
            )
        }
    }
}

@Composable
private fun MessageBubble(m: MessageEntity, streaming: Boolean = false) {
    val isUser = m.role == Role.User
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 320.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant,
            ),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp,
            ),
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = if (isUser) "You" else "Dolphin",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(modifier = Modifier.padding(top = 4.dp)) {
                    MarkdownText(text = m.content + if (streaming) "▍" else "")
                }
            }
        }
    }
}

@Composable
private fun ThinkingIndicator() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
    ) {
        Text(
            text = stringResource(R.string.chat_thinking),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatInputBar(
    value: String,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    isStreaming: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.chat_hint)) },
            shape = RoundedCornerShape(22.dp),
            maxLines = 6,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        )
        if (isStreaming) {
            FilledIconButton(
                onClick = onStop,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                ),
                modifier = Modifier.size(48.dp),
            ) { Icon(Icons.Filled.Stop, contentDescription = stringResource(R.string.chat_stop)) }
        } else {
            FilledIconButton(
                onClick = onSend,
                modifier = Modifier.size(48.dp),
            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send)) }
        }
    }
}

@Composable
private fun EmptyChatHero() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary,
                                MaterialTheme.colorScheme.secondary,
                            )
                        )
                    ),
            )
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge,
            )
            Text(
                stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ThermalBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.err_thermal_paused),
            color = MaterialTheme.colorScheme.tertiary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
