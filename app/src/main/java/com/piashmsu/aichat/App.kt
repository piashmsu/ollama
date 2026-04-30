package com.piashmsu.aichat

import android.app.Application
import com.piashmsu.aichat.data.AppContainer
import com.piashmsu.aichat.data.DefaultAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {
    lateinit var container: AppContainer
        private set

    /** Scope tied to the Application lifetime. Long-running work (e.g. model
     *  downloads) lives here so it survives Activity destruction and tab
     *  switches. The process is anchored by a foreground service while
     *  any download is active. */
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        container = DefaultAppContainer(this)
    }

    companion object {
        @Volatile lateinit var instance: App
            private set
    }
}
