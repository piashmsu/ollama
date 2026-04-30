package com.piashmsu.aichat.memory

import com.piashmsu.aichat.data.db.MemoryDao
import com.piashmsu.aichat.data.db.MemoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Long-term memory: facts the assistant remembers across conversations.
 *
 * Phase 1 ships a simple key/value store backed by Room. Phase 2 layers on top
 * of this:
 *   - automatic fact extraction (after each conversation, ask the model to
 *     produce a JSON list of `{key, value}` pairs)
 *   - retrieval by embedding similarity (BERT-tiny sentence-transformers via
 *     ONNX Runtime Mobile) before each new prompt
 *
 * The schema is intentionally minimal so we can extend it without migrating.
 */
class MemoryStore(private val dao: MemoryDao) {
    fun observeAll(): Flow<List<MemoryEntity>> = dao.observeAll()
    suspend fun all(): List<MemoryEntity> = dao.all()
    suspend fun remember(key: String, value: String, source: String? = null): Long =
        dao.insert(MemoryEntity(key = key, value = value, source = source))
    suspend fun forget(id: Long) = dao.delete(id)
    suspend fun clearAll() = dao.clear()

    /** Render the memory as a system-prompt prefix; empty string when nothing remembered. */
    suspend fun asPromptPrefix(): String {
        val items = all()
        if (items.isEmpty()) return ""
        return buildString {
            appendLine("Things you remember about the user:")
            items.take(20).forEach { appendLine("- ${it.key}: ${it.value}") }
        }
    }
}
