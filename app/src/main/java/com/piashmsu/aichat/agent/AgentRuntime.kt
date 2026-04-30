package com.piashmsu.aichat.agent

/**
 * Tool-use scaffold.
 *
 * Phase 1 defines the contract and ships two tiny tools (calculator, clipboard)
 * so the chat loop can be wired up. Phase 3 will:
 *   - add a tool-call protocol parser (JSON between <tool_call> tags, or
 *     OpenAI-style function calling)
 *   - support streaming tool execution
 *   - register web-search, calendar, notes, file I/O, screen-capture-and-explain
 *   - add a JSON skill descriptor format so users can drop in their own tools
 */
interface AgentTool {
    val name: String
    val description: String
    val parametersJsonSchema: String
    suspend fun execute(arguments: Map<String, String>): String
}

class AgentRuntime(val tools: List<AgentTool>) {
    fun renderToolList(): String = buildString {
        appendLine("Available tools:")
        for (t in tools) {
            appendLine("- ${t.name}: ${t.description}")
        }
    }

    fun findTool(name: String): AgentTool? = tools.firstOrNull { it.name == name }
}
