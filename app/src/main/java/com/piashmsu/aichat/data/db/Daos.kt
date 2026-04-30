package com.piashmsu.aichat.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(c: ConversationEntity): Long

    @Update
    suspend fun update(c: ConversationEntity)

    @Query("SELECT * FROM conversations ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: Long): ConversationEntity?

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE conversations SET title = :title, updatedAt = :ts WHERE id = :id")
    suspend fun rename(id: Long, title: String, ts: Long = System.currentTimeMillis())
}

@Dao
interface MessageDao {
    @Insert
    suspend fun insert(m: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE conversation_id = :cid ORDER BY created_at ASC")
    fun observe(cid: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversation_id = :cid ORDER BY created_at ASC")
    suspend fun list(cid: Long): List<MessageEntity>

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM messages WHERE conversation_id = :cid")
    suspend fun deleteAllFor(cid: Long)
}

@Dao
interface MemoryDao {
    @Insert
    suspend fun insert(m: MemoryEntity): Long

    @Query("SELECT * FROM memory ORDER BY created_at DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memory ORDER BY created_at DESC")
    suspend fun all(): List<MemoryEntity>

    @Query("DELETE FROM memory WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM memory")
    suspend fun clear()
}
