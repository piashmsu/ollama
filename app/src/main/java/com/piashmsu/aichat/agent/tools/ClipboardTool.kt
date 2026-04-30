package com.piashmsu.aichat.agent.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.piashmsu.aichat.agent.AgentTool

class ClipboardTool(private val context: Context) : AgentTool {
    override val name = "clipboard"
    override val description = "Read the system clipboard text or set new text. action: 'get' | 'set'."
    override val parametersJsonSchema =
        """{"type":"object","properties":{"action":{"type":"string","enum":["get","set"]},"text":{"type":"string"}},"required":["action"]}"""

    override suspend fun execute(arguments: Map<String, String>): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        return when (arguments["action"]) {
            "get" -> cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
            "set" -> {
                val t = arguments["text"] ?: return "error: missing 'text'"
                cm.setPrimaryClip(ClipData.newPlainText("dolphin", t))
                "ok"
            }
            else -> "error: action must be 'get' or 'set'"
        }
    }
}
