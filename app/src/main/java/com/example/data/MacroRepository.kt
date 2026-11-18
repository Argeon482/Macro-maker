package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.model.EmbeddedTemplate
import com.example.model.IfImageStep
import com.example.model.InputInjectionMode
import com.example.model.MacroDefinition
import com.example.model.MacroExportBundle
import com.example.model.MacroStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class MacroRepository(
    private val context: Context,
    private val macroDao: MacroDao
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        classDiscriminator = "type"
    }

    private val templateDir: File
        get() = File(context.filesDir, "templates").apply { mkdirs() }

    val macros: Flow<List<MacroDefinition>> = macroDao.observeAllMacros().map { entities ->
        entities.map { it.toDomain() }
    }

    val templates: Flow<List<TemplateAssetEntity>> = macroDao.observeAllTemplates()

    val recentLogs: Flow<List<ExecutionLogEntity>> = macroDao.observeRecentLogs()

    fun observeMacro(id: String): Flow<MacroDefinition?> =
        macroDao.observeMacroById(id).map { it?.toDomain() }

    suspend fun getMacro(id: String): MacroDefinition? =
        macroDao.getMacroById(id)?.toDomain()

    suspend fun saveMacro(macro: MacroDefinition) {
        val updated = macro.copy(updatedAt = System.currentTimeMillis())
        macroDao.upsertMacro(updated.toEntity())
    }

    suspend fun duplicateMacro(id: String): MacroDefinition? {
        val existing = getMacro(id) ?: return null
        val now = System.currentTimeMillis()
        val copy = existing.copy(
            id = UUID.randomUUID().toString(),
            name = "${existing.name} (Copy)",
            createdAt = now,
            updatedAt = now
        )
        saveMacro(copy)
        return copy
    }

    suspend fun deleteMacro(id: String) {
        macroDao.deleteMacroById(id)
    }

    suspend fun saveTemplateBitmap(name: String, bitmap: Bitmap): TemplateAssetEntity =
        withContext(Dispatchers.IO) {
            val id = UUID.randomUUID().toString()
            val file = File(templateDir, "$id.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val entity = TemplateAssetEntity(
                id = id,
                name = name.ifBlank { "Template ${id.take(4)}" },
                filePath = file.absolutePath,
                width = bitmap.width,
                height = bitmap.height,
                createdAt = System.currentTimeMillis()
            )
            macroDao.upsertTemplate(entity)
            entity
        }

    suspend fun loadTemplateBitmap(templateId: String): Bitmap? = withContext(Dispatchers.IO) {
        val asset = macroDao.getTemplateById(templateId) ?: return@withContext null
        val file = File(asset.filePath)
        if (!file.exists()) return@withContext null
        BitmapFactory.decodeFile(file.absolutePath)
    }

    suspend fun deleteTemplate(id: String) = withContext(Dispatchers.IO) {
        macroDao.getTemplateById(id)?.let { asset ->
            File(asset.filePath).delete()
        }
        macroDao.deleteTemplateById(id)
    }

    suspend fun appendLog(
        macroId: String,
        macroName: String,
        stepLabel: String,
        message: String,
        level: LogLevel = LogLevel.INFO
    ) {
        macroDao.insertLog(
            ExecutionLogEntity(
                macroId = macroId,
                macroName = macroName,
                stepLabel = stepLabel,
                message = message,
                level = level.name
            )
        )
    }

    suspend fun clearLogs() {
        macroDao.clearLogs()
    }

    suspend fun exportMacrosToJson(macroIds: Set<String>? = null): String =
        withContext(Dispatchers.IO) {
            val all = macroDao.getAllMacrosOnce().map { it.toDomain() }
            val selected = if (macroIds.isNullOrEmpty()) all else all.filter { it.id in macroIds }

            val neededTemplateIds = selected.flatMap { macro ->
                macro.steps.filterIsInstance<IfImageStep>().map { it.templateId }
            }.filter { it.isNotBlank() }.toSet()

            val embeddedTemplates = macroDao.getAllTemplatesOnce()
                .filter { it.id in neededTemplateIds }
                .mapNotNull { asset ->
                    val file = File(asset.filePath)
                    if (!file.exists()) return@mapNotNull null
                    val bytes = file.readBytes()
                    EmbeddedTemplate(
                        id = asset.id,
                        name = asset.name,
                        width = asset.width,
                        height = asset.height,
                        pngBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    )
                }

            val bundle = MacroExportBundle(
                schemaVersion = 1,
                exportedAt = System.currentTimeMillis(),
                macros = selected,
                embeddedTemplates = embeddedTemplates
            )
            json.encodeToString(bundle)
        }

    suspend fun importMacrosFromJson(rawJson: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val bundle = runCatching {
                json.decodeFromString<MacroExportBundle>(rawJson)
            }.getOrElse {
                val single = json.decodeFromString<MacroDefinition>(rawJson)
                MacroExportBundle(macros = listOf(single))
            }

            val templateIdRemap = mutableMapOf<String, String>()
            for (tpl in bundle.embeddedTemplates) {
                val bytes = Base64.decode(tpl.pngBase64, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: continue
                val newId = UUID.randomUUID().toString()
                val file = File(templateDir, "$newId.png")
                FileOutputStream(file).use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                bmp.recycle()
                macroDao.upsertTemplate(
                    TemplateAssetEntity(
                        id = newId,
                        name = tpl.name,
                        filePath = file.absolutePath,
                        width = tpl.width,
                        height = tpl.height,
                        createdAt = System.currentTimeMillis()
                    )
                )
                templateIdRemap[tpl.id] = newId
            }

            val now = System.currentTimeMillis()
            var importedCount = 0
            for (macro in bundle.macros) {
                val remappedSteps = macro.steps.map { step ->
                    if (step is IfImageStep && step.templateId in templateIdRemap) {
                        step.copy(templateId = templateIdRemap[step.templateId]!!)
                    } else {
                        step
                    }
                }
                val newMacro = macro.copy(
                    id = UUID.randomUUID().toString(),
                    steps = remappedSteps,
                    createdAt = now,
                    updatedAt = now
                )
                saveMacro(newMacro)
                importedCount++
            }
            importedCount
        }
    }

    private fun MacroEntity.toDomain(): MacroDefinition {
        val decodedSteps = runCatching {
            json.decodeFromString<List<MacroStep>>(stepsJson)
        }.getOrDefault(emptyList())
        val resolvedMode = runCatching {
            InputInjectionMode.valueOf(injectionMode)
        }.getOrDefault(InputInjectionMode.MOUSE)
        return MacroDefinition(
            id = id,
            name = name,
            description = description,
            targetAppLabel = targetAppLabel,
            loopCount = loopCount,
            pollingIntervalMs = pollingIntervalMs,
            injectionMode = resolvedMode,
            steps = decodedSteps,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun MacroDefinition.toEntity(): MacroEntity {
        return MacroEntity(
            id = id,
            name = name,
            description = description,
            targetAppLabel = targetAppLabel,
            loopCount = loopCount,
            pollingIntervalMs = pollingIntervalMs,
            injectionMode = injectionMode.name,
            stepsJson = json.encodeToString(steps),
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    companion object {
        fun bitmapToPngBytes(bitmap: Bitmap): ByteArray {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            return out.toByteArray()
        }
    }
}
