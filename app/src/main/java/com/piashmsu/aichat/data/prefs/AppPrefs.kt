package com.piashmsu.aichat.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.dataStore by preferencesDataStore("dolphin_prefs")

enum class DarkMode { System, Dark, Light }
enum class Engine { LlamaCpp, OllamaDaemon }

data class PrefsSnapshot(
    val darkMode: DarkMode = DarkMode.System,
    val dynamicColor: Boolean = false,
    val engine: Engine = Engine.LlamaCpp,
    val activeModelPath: String? = null,
    val activeModelName: String? = null,
    val systemPrompt: String =
        "You are Dolphin, a helpful, concise, friendly offline AI assistant. " +
            "Answer in the user's language. When showing code, use fenced code blocks with the language tag.",
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val maxTokens: Int = 512,
    val threads: Int = 6,
    val contextSize: Int = 2048,
    val thermalGuardEnabled: Boolean = true,
    val thermalPauseTemp: Float = 42f,
    val thermalResumeTemp: Float = 38f,
    val tokenRateCap: Int = 0,
    val batteryAware: Boolean = true,
    val agentMode: Boolean = false,
    val avatarEnabled: Boolean = false,
    val onboardingDone: Boolean = false,
)

class AppPrefs(context: Context) {
    private val ds = context.dataStore

    val flow: Flow<PrefsSnapshot> = ds.data.map { p ->
        PrefsSnapshot(
            darkMode = DarkMode.valueOf(p[K_DARK] ?: DarkMode.System.name),
            dynamicColor = p[K_DYNAMIC] ?: false,
            engine = Engine.valueOf(p[K_ENGINE] ?: Engine.LlamaCpp.name),
            activeModelPath = p[K_ACTIVE_PATH],
            activeModelName = p[K_ACTIVE_NAME],
            systemPrompt = p[K_SYSTEM] ?: PrefsSnapshot().systemPrompt,
            temperature = p[K_TEMP] ?: 0.7f,
            topP = p[K_TOP_P] ?: 0.9f,
            topK = p[K_TOP_K] ?: 40,
            maxTokens = p[K_MAX_TOK] ?: 512,
            threads = p[K_THREADS] ?: 6,
            contextSize = p[K_CTX] ?: 2048,
            thermalGuardEnabled = p[K_THERM_ON] ?: true,
            thermalPauseTemp = p[K_THERM_PAUSE] ?: 42f,
            thermalResumeTemp = p[K_THERM_RESUME] ?: 38f,
            tokenRateCap = p[K_RATE_CAP] ?: 0,
            batteryAware = p[K_BATTERY] ?: true,
            agentMode = p[K_AGENT] ?: false,
            avatarEnabled = p[K_AVATAR] ?: false,
            onboardingDone = p[K_ONBOARDED] ?: false,
        )
    }

    fun snapshot(): PrefsSnapshot = runBlocking { flow.first() }

    suspend fun update(block: (MutableUpdater) -> Unit) {
        ds.edit { prefs -> block(MutableUpdater(prefs)) }
    }

    inner class MutableUpdater(val p: androidx.datastore.preferences.core.MutablePreferences) {
        fun setDarkMode(v: DarkMode) { p[K_DARK] = v.name }
        fun setDynamicColor(v: Boolean) { p[K_DYNAMIC] = v }
        fun setEngine(v: Engine) { p[K_ENGINE] = v.name }
        fun setActiveModel(path: String?, name: String?) {
            if (path == null) p.remove(K_ACTIVE_PATH) else p[K_ACTIVE_PATH] = path
            if (name == null) p.remove(K_ACTIVE_NAME) else p[K_ACTIVE_NAME] = name
        }
        fun setSystemPrompt(v: String) { p[K_SYSTEM] = v }
        fun setTemperature(v: Float) { p[K_TEMP] = v }
        fun setTopP(v: Float) { p[K_TOP_P] = v }
        fun setTopK(v: Int) { p[K_TOP_K] = v }
        fun setMaxTokens(v: Int) { p[K_MAX_TOK] = v }
        fun setThreads(v: Int) { p[K_THREADS] = v }
        fun setContextSize(v: Int) { p[K_CTX] = v }
        fun setThermalGuardEnabled(v: Boolean) { p[K_THERM_ON] = v }
        fun setThermalPauseTemp(v: Float) { p[K_THERM_PAUSE] = v }
        fun setThermalResumeTemp(v: Float) { p[K_THERM_RESUME] = v }
        fun setTokenRateCap(v: Int) { p[K_RATE_CAP] = v }
        fun setBatteryAware(v: Boolean) { p[K_BATTERY] = v }
        fun setAgentMode(v: Boolean) { p[K_AGENT] = v }
        fun setAvatarEnabled(v: Boolean) { p[K_AVATAR] = v }
        fun setOnboardingDone(v: Boolean) { p[K_ONBOARDED] = v }
    }

    private companion object {
        val K_DARK = stringPreferencesKey("dark_mode")
        val K_DYNAMIC = booleanPreferencesKey("dynamic_color")
        val K_ENGINE = stringPreferencesKey("engine")
        val K_ACTIVE_PATH = stringPreferencesKey("active_model_path")
        val K_ACTIVE_NAME = stringPreferencesKey("active_model_name")
        val K_SYSTEM = stringPreferencesKey("system_prompt")
        val K_TEMP = floatPreferencesKey("temperature")
        val K_TOP_P = floatPreferencesKey("top_p")
        val K_TOP_K = intPreferencesKey("top_k")
        val K_MAX_TOK = intPreferencesKey("max_tokens")
        val K_THREADS = intPreferencesKey("threads")
        val K_CTX = intPreferencesKey("context_size")
        val K_THERM_ON = booleanPreferencesKey("thermal_guard")
        val K_THERM_PAUSE = floatPreferencesKey("thermal_pause")
        val K_THERM_RESUME = floatPreferencesKey("thermal_resume")
        val K_RATE_CAP = intPreferencesKey("token_rate_cap")
        val K_BATTERY = booleanPreferencesKey("battery_aware")
        val K_AGENT = booleanPreferencesKey("agent_mode")
        val K_AVATAR = booleanPreferencesKey("avatar_enabled")
        val K_ONBOARDED = booleanPreferencesKey("onboarded")
    }
}
