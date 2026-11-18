package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MacroDao {
    @Query("SELECT * FROM macros ORDER BY updatedAt DESC")
    fun observeAllMacros(): Flow<List<MacroEntity>>

    @Query("SELECT * FROM macros WHERE id = :id LIMIT 1")
    fun observeMacroById(id: String): Flow<MacroEntity?>

    @Query("SELECT * FROM macros WHERE id = :id LIMIT 1")
    suspend fun getMacroById(id: String): MacroEntity?

    @Query("SELECT * FROM macros ORDER BY updatedAt DESC")
    suspend fun getAllMacrosOnce(): List<MacroEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMacro(macro: MacroEntity)

    @Query("DELETE FROM macros WHERE id = :id")
    suspend fun deleteMacroById(id: String)

    @Query("SELECT * FROM template_assets ORDER BY createdAt DESC")
    fun observeAllTemplates(): Flow<List<TemplateAssetEntity>>

    @Query("SELECT * FROM template_assets WHERE id = :id LIMIT 1")
    suspend fun getTemplateById(id: String): TemplateAssetEntity?

    @Query("SELECT * FROM template_assets")
    suspend fun getAllTemplatesOnce(): List<TemplateAssetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTemplate(template: TemplateAssetEntity)

    @Query("DELETE FROM template_assets WHERE id = :id")
    suspend fun deleteTemplateById(id: String)

    @Query("SELECT * FROM execution_logs ORDER BY timestamp DESC LIMIT 200")
    fun observeRecentLogs(): Flow<List<ExecutionLogEntity>>

    @Insert
    suspend fun insertLog(log: ExecutionLogEntity)

    @Query("DELETE FROM execution_logs")
    suspend fun clearLogs()
}
