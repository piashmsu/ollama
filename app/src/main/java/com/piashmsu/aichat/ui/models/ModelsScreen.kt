package com.piashmsu.aichat.ui.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.piashmsu.aichat.R
import com.piashmsu.aichat.data.AppContainer
import com.piashmsu.aichat.data.download.CuratedModel
import com.piashmsu.aichat.data.download.DownloadStatus
import com.piashmsu.aichat.data.download.ModelCatalog
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsRoute(container: AppContainer) {
    val prefs by container.prefs.flow.collectAsState(initial = container.prefs.snapshot())
    val scope = rememberCoroutineScope()
    var local by remember { mutableStateOf(container.downloader.listLocalModels()) }
    var customUrl by remember { mutableStateOf("") }

    fun refreshLocal() { local = container.downloader.listLocalModels() }
    LaunchedEffect(Unit) { refreshLocal() }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text(stringResource(R.string.models_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SectionTitle(stringResource(R.string.models_curated)) }
            item {
                Text(
                    text = stringResource(R.string.download_resume_supported),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(ModelCatalog.curated, key = { it.id }) { m ->
                CuratedRow(
                    container = container,
                    model = m,
                    isActive = prefs.activeModelPath?.endsWith(m.url.substringAfterLast('/')) == true,
                    onDownload = {
                        container.downloads.enqueue(m.url, m.displayName, makeActive = true)
                    },
                    onCancel = { container.downloads.cancel(m.url) },
                    onCompleted = { refreshLocal() },
                )
            }

            item {
                SectionTitle(stringResource(R.string.models_add_url))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.models_url_hint)) },
                        singleLine = true,
                    )
                    Button(onClick = {
                        val url = customUrl.trim()
                        if (url.isNotEmpty()) {
                            val name = url.substringAfterLast('/').substringBeforeLast('.')
                            container.downloads.enqueue(url, name.ifBlank { "model" }, makeActive = true)
                            customUrl = ""
                        }
                    }) { Text(stringResource(R.string.models_download)) }
                }
            }

            item { SectionTitle(stringResource(R.string.models_local)) }
            if (local.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.models_loading),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(local, key = { it.absolutePath }) { f ->
                    LocalRow(
                        file = f,
                        active = prefs.activeModelPath == f.absolutePath,
                        onUse = {
                            scope.launch {
                                container.prefs.update {
                                    it.setActiveModel(f.absolutePath, f.nameWithoutExtension)
                                }
                            }
                        },
                        onDelete = {
                            f.delete()
                            refreshLocal()
                            if (prefs.activeModelPath == f.absolutePath) {
                                scope.launch {
                                    container.prefs.update { it.setActiveModel(null, null) }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun CuratedRow(
    container: AppContainer,
    model: CuratedModel,
    isActive: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onCompleted: () -> Unit,
) {
    val status by remember(model.url) { container.downloads.status(model.url) }
        .collectAsState(initial = DownloadStatus())

    LaunchedEffect(status.state) {
        if (status.state == DownloadStatus.State.Succeeded) onCompleted()
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (model.recommended) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (model.recommended) {
                    AssistChip(
                        onClick = {},
                        label = { Text("★", color = MaterialTheme.colorScheme.tertiary) },
                    )
                }
            }
            Text(
                text = "${"%.1f".format(model.sizeMb / 1024f)} GB · ${model.description}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                status.state == DownloadStatus.State.Running ||
                    status.state == DownloadStatus.State.Pending -> {
                    LinearProgressIndicator(
                        progress = { (status.pct / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (status.total > 0)
                                "${status.pct}% · ${formatSize(status.bytes)} / ${formatSize(status.total)}"
                            else if (status.state == DownloadStatus.State.Pending)
                                "Queued…"
                            else "${status.pct}%",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.models_cancel_download))
                        }
                    }
                }
                isActive -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            stringResource(R.string.models_active),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                status.state == DownloadStatus.State.Failed -> {
                    Text(
                        text = status.error ?: stringResource(R.string.download_failed_retry),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Button(onClick = onDownload, modifier = Modifier.padding(top = 6.dp)) {
                        Icon(Icons.Filled.CloudDownload, null)
                        Text("  ${stringResource(R.string.models_download)}")
                    }
                }
                else -> {
                    Button(onClick = onDownload, modifier = Modifier.padding(top = 6.dp)) {
                        Icon(Icons.Filled.CloudDownload, null)
                        Text("  ${stringResource(R.string.models_download)}")
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalRow(
    file: File,
    active: Boolean,
    onUse: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${"%.1f".format(file.length() / (1024.0 * 1024.0 * 1024.0))} GB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!active) {
                Button(onClick = onUse) { Text(stringResource(R.string.models_use)) }
            } else {
                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.models_delete))
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.2f GB".format(mb / 1024.0) else "%.0f MB".format(mb)
}
