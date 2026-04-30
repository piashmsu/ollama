package com.piashmsu.aichat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.piashmsu.aichat.R
import com.piashmsu.aichat.data.AppContainer
import com.piashmsu.aichat.ui.chat.ChatRoute
import com.piashmsu.aichat.ui.history.HistoryRoute
import com.piashmsu.aichat.ui.models.ModelsRoute
import com.piashmsu.aichat.ui.settings.SettingsRoute

private sealed class Screen(val route: String, val labelRes: Int) {
    object Chat : Screen("chat", R.string.nav_chat)
    object History : Screen("history", R.string.nav_history)
    object Models : Screen("models", R.string.nav_models)
    object Settings : Screen("settings", R.string.nav_settings)
}

@Composable
fun AppNav(container: AppContainer) {
    val nav = rememberNavController()
    val backstack by nav.currentBackStackEntryAsState()
    val current = backstack?.destination?.route ?: Screen.Chat.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                listOf(Screen.Chat, Screen.History, Screen.Models, Screen.Settings).forEach { s ->
                    NavigationBarItem(
                        selected = current == s.route,
                        onClick = {
                            if (current != s.route) {
                                nav.navigate(s.route) {
                                    popUpTo(Screen.Chat.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            val icon = when (s) {
                                Screen.Chat -> Icons.AutoMirrored.Filled.Chat
                                Screen.History -> Icons.Filled.History
                                Screen.Models -> Icons.Filled.Memory
                                Screen.Settings -> Icons.Filled.Settings
                            }
                            Icon(icon, contentDescription = null)
                        },
                        label = { Text(stringResource(s.labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(navController = nav, startDestination = Screen.Chat.route) {
                composable(Screen.Chat.route) { ChatRoute(container) }
                composable(Screen.History.route) {
                    HistoryRoute(container, openConversation = { id ->
                        nav.navigate("${Screen.Chat.route}?cid=$id") {
                            popUpTo(Screen.Chat.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    })
                }
                composable(Screen.Models.route) { ModelsRoute(container) }
                composable(Screen.Settings.route) { SettingsRoute(container) }
                composable("${Screen.Chat.route}?cid={cid}") { entry ->
                    val cid = entry.arguments?.getString("cid")?.toLongOrNull()
                    ChatRoute(container, conversationId = cid)
                }
            }
        }
    }
}
