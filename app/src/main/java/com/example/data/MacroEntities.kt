package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "macros")
data class MacroEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val targetAppLabel: String,
    val loopCount: Int,
    val pollingIntervalMs: Long,
    val injectionMode: String = "TOUCHSCREEN",
    val touchGuardPolicy: String = "OFF",
    val stepsJson: String,
    val functionsJson: String = "[]",
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "template_assets")
data class TemplateAssetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val filePath: String,
    val width: Int,
    val height: Int,
    val createdAt: Long
)

enum class LogLevel {
    INFO,
    SUCCESS,
    WARN,
    ERROR
}

@Entity(tableName = "execution_logs")
data class ExecutionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val macroId: String,
    val macroName: String,
    val stepLabel: String,
    val message: String,
    val level: String,
    val timestamp: Long = System.currentTimeMillis()
)
