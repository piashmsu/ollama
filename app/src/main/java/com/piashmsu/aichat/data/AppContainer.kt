package com.piashmsu.aichat.data

import android.content.Context
import androidx.room.Room
import com.piashmsu.aichat.agent.AgentRuntime
import com.piashmsu.aichat.agent.tools.CalculatorTool
import com.piashmsu.aichat.agent.tools.ClipboardTool
import com.piashmsu.aichat.data.db.AppDatabase
import com.piashmsu.aichat.data.download.ModelDownloader
import com.piashmsu.aichat.data.prefs.AppPrefs
import com.piashmsu.aichat.llm.LlamaCppEngine
import com.piashmsu.aichat.llm.LlmRuntime
import com.piashmsu.aichat.memory.MemoryStore
import com.piashmsu.aichat.thermal.ThermalGuard
import com.piashmsu.aichat.voice.VoiceController
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

interface AppContainer {
    val prefs: AppPrefs
    val db: AppDatabase
    val httpClient: OkHttpClient
    val downloader: ModelDownloader
    val llmRuntime: LlmRuntime
    val memory: MemoryStore
    val thermalGuard: ThermalGuard
    val agentRuntime: AgentRuntime
    val voice: VoiceController
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val prefs: AppPrefs by lazy { AppPrefs(context) }

    override val db: AppDatabase by lazy {
        Room.databaseBuilder(context, AppDatabase::class.java, "dolphin.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    override val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    override val downloader: ModelDownloader by lazy {
        ModelDownloader(context, httpClient)
    }

    override val llmRuntime: LlmRuntime by lazy {
        LlmRuntime(LlamaCppEngine(), prefs)
    }

    override val memory: MemoryStore by lazy { MemoryStore(db.memoryDao()) }

    override val thermalGuard: ThermalGuard by lazy { ThermalGuard(context, prefs) }

    override val agentRuntime: AgentRuntime by lazy {
        AgentRuntime(
            tools = listOf(CalculatorTool(), ClipboardTool(context))
        )
    }

    override val voice: VoiceController by lazy { VoiceController(context) }
}
