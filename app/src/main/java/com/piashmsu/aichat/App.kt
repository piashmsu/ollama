package com.piashmsu.aichat

import android.app.Application
import com.piashmsu.aichat.data.AppContainer
import com.piashmsu.aichat.data.DefaultAppContainer

class App : Application() {
    lateinit var container: AppContainer
        private set

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
