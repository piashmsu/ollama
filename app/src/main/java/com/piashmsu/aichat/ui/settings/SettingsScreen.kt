package com.piashmsu.aichat.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.piashmsu.aichat.BuildConfig
import com.piashmsu.aichat.R
import com.piashmsu.aichat.data.AppContainer
import com.piashmsu.aichat.data.prefs.DarkMode
import com.piashmsu.aichat.data.prefs.Engine
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(container: AppContainer) {
    val prefs by container.prefs.flow.collectAsState(initial = container.prefs.snapshot())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Section(stringResource(R.string.settings_section_inference)) {
                    SliderRow(
                        label = stringResource(R.string.settings_temperature),
                        value = prefs.temperature, range = 0f..1.5f, steps = 14,
                        format = { "%.2f".format(it) },
                        onChange = { v -> scope.launch { container.prefs.update { it.setTemperature(v) } } },
                    )
                    SliderRow(
                        label = stringResource(R.string.settings_top_p),
                        value = prefs.topP, range = 0f..1f, steps = 19,
                        format = { "%.2f".format(it) },
                        onChange = { v -> scope.launch { container.prefs.update { it.setTopP(v) } } },
                    )
                    SliderRow(
                        label = stringResource(R.string.settings_top_k),
                        value = prefs.topK.toFloat(), range = 0f..200f, steps = 19,
                        format = { "${it.toInt()}" },
                        onChange = { v -> scope.launch { container.prefs.update { it.setTopK(v.toInt()) } } },
                    )
                    SliderRow(
                        label = stringResource(R.string.settings_max_tokens),
                        value = prefs.maxTokens.toFloat(), range = 64f..4096f, steps = 62,
                        format = { "${it.toInt()}" },
                        onChange = { v -> scope.launch { container.prefs.update { it.setMaxTokens(v.toInt()) } } },
                    )
                    SliderRow(
                        label = stringResource(R.string.settings_threads),
                        value = prefs.threads.toFloat(), range = 1f..8f, steps = 6,
                        format = { "${it.toInt()}" },
                        onChange = { v -> scope.launch { container.prefs.update { it.setThreads(v.toInt()) } } },
                    )
                    SliderRow(
                        label = stringResource(R.string.settings_context),
                        value = prefs.contextSize.toFloat(), range = 1024f..16_384f, steps = 14,
                        format = { "${it.toInt()}" },
                        onChange = { v -> scope.launch { container.prefs.update { it.setContextSize(v.toInt()) } } },
                    )
                    Text(
                        text = stringResource(R.string.settings_engine),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = { scope.launch { container.prefs.update { it.setEngine(Engine.LlamaCpp) } } },
                            label = { Text(stringResource(R.string.settings_engine_llamacpp)) },
                            leadingIcon = if (prefs.engine == Engine.LlamaCpp) {
                                { Icon(Icons.Filled.Check, null) }
                            } else null,
                        )
                        AssistChip(
                            onClick = { scope.launch { container.prefs.update { it.setEngine(Engine.OllamaDaemon) } } },
                            label = { Text(stringResource(R.string.settings_engine_ollama)) },
                            leadingIcon = if (prefs.engine == Engine.OllamaDaemon) {
                                { Icon(Icons.Filled.Check, null) }
                            } else null,
                        )
                    }
                }
            }
            item {
                Section(stringResource(R.string.settings_section_persona)) {
                    Text(stringResource(R.string.settings_system_prompt), style = MaterialTheme.typography.labelLarge)
                    OutlinedTextField(
                        value = prefs.systemPrompt,
                        onValueChange = { v -> scope.launch { container.prefs.update { it.setSystemPrompt(v) } } },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.settings_system_prompt_hint)) },
                        minLines = 3, maxLines = 8,
                    )
                    SwitchRow(
                        title = stringResource(R.string.agent_mode),
                        subtitle = stringResource(R.string.settings_thermal_guard_summary),
                        checked = prefs.agentMode,
                        onChange = { v -> scope.launch { container.prefs.update { it.setAgentMode(v) } } },
                    )
                    SwitchRow(
                        title = stringResource(R.string.avatar_title),
                        subtitle = stringResource(R.string.avatar_robot_girl),
                        checked = prefs.avatarEnabled,
                        onChange = { v -> scope.launch { container.prefs.update { it.setAvatarEnabled(v) } } },
                    )
                }
            }
            item {
                Section(stringResource(R.string.settings_section_thermal)) {
                    SwitchRow(
                        title = stringResource(R.string.settings_thermal_guard),
                        subtitle = stringResource(R.string.settings_thermal_guard_summary),
                        checked = prefs.thermalGuardEnabled,
                        onChange = { v -> scope.launch { container.prefs.update { it.setThermalGuardEnabled(v) } } },
                    )
                    SliderRow(
                        label = stringResource(R.string.settings_thermal_pause_temp),
                        value = prefs.thermalPauseTemp, range = 35f..50f, steps = 14,
                        format = { "%.0f °C".format(it) },
                        onChange = { v -> scope.launch { container.prefs.update { it.setThermalPauseTemp(v) } } },
                    )
                    SliderRow(
                        label = stringResource(R.string.settings_thermal_resume_temp),
                        value = prefs.thermalResumeTemp, range = 30f..45f, steps = 14,
                        format = { "%.0f °C".format(it) },
                        onChange = { v -> scope.launch { container.prefs.update { it.setThermalResumeTemp(v) } } },
                    )
                    SliderRow(
                        label = stringResource(R.string.settings_token_rate_cap),
                        value = prefs.tokenRateCap.toFloat(), range = 0f..120f, steps = 11,
                        format = { if (it < 1f) "off" else "${it.toInt()} tok/s" },
                        onChange = { v -> scope.launch { container.prefs.update { it.setTokenRateCap(v.toInt()) } } },
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_battery_aware),
                        subtitle = stringResource(R.string.settings_battery_aware_summary),
                        checked = prefs.batteryAware,
                        onChange = { v -> scope.launch { container.prefs.update { it.setBatteryAware(v) } } },
                    )
                }
            }
            item {
                Section(stringResource(R.string.settings_section_appearance)) {
                    SwitchRow(
                        title = stringResource(R.string.settings_dynamic_color),
                        checked = prefs.dynamicColor,
                        onChange = { v -> scope.launch { container.prefs.update { it.setDynamicColor(v) } } },
                    )
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DarkMode.entries.forEach { mode ->
                            AssistChip(
                                onClick = { scope.launch { container.prefs.update { it.setDarkMode(mode) } } },
                                label = {
                                    Text(when (mode) {
                                        DarkMode.System -> stringResource(R.string.settings_dark_mode_system)
                                        DarkMode.Dark -> stringResource(R.string.settings_dark_mode_dark)
                                        DarkMode.Light -> stringResource(R.string.settings_dark_mode_light)
                                    })
                                },
                                leadingIcon = if (prefs.darkMode == mode) {
                                    { Icon(Icons.Filled.Check, null) }
                                } else null,
                            )
                        }
                    }
                }
            }
            item {
                Section(stringResource(R.string.settings_section_about)) {
                    InfoRow(stringResource(R.string.settings_about_version), BuildConfig.VERSION_NAME)
                    InfoRow(stringResource(R.string.settings_about_repo), "github.com/piashmsu/ollama")
                    InfoRow(stringResource(R.string.settings_about_license), "Apache-2.0")
                    InfoRow("Backend", container.llmRuntime.backend)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            content()
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    format: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(format(value), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
