package com.piashmsu.aichat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.piashmsu.aichat.ui.AppNav
import com.piashmsu.aichat.ui.theme.DolphinAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as App).container
        setContent {
            val prefs by container.prefs.flow.collectAsState(initial = container.prefs.snapshot())
            DolphinAITheme(
                darkMode = prefs.darkMode,
                dynamicColor = prefs.dynamicColor,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNav(container = container)
                }
            }
        }
    }
}
