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
import com.example.model.MacroFunction
import com.example.model.MacroStep
import com.example.model.TouchGuardPolicy
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

    suspend fun cropTemplate(
        templateId: String,
        trimLeft: Int,
        trimTop: Int,
        trimRight: Int,
        trimBottom: Int,
        saveAsNew: Boolean = false,
        newName: String? = null
    ): TemplateAssetEntity? = withContext(Dispatchers.IO) {
        val asset = macroDao.getTemplateById(templateId) ?: return@withContext null
        val sourceFile = File(asset.filePath)
        if (!sourceFile.exists()) return@withContext null
        val source = BitmapFactory.decodeFile(sourceFile.absolutePath) ?: return@withContext null

        try {
            val minW = minOf(4, source.width).coerceAtLeast(1)
            val minH = minOf(4, source.height).coerceAtLeast(1)
            val safeLeft = trimLeft.coerceIn(0, (source.width - minW).coerceAtLeast(0))
            val safeRight = trimRight.coerceIn(0, (source.width - safeLeft - minW).coerceAtLeast(0))
            val safeTop = trimTop.coerceIn(0, (source.height - minH).coerceAtLeast(0))
            val safeBottom = trimBottom.coerceIn(0, (source.height - safeTop - minH).coerceAtLeast(0))

            val cropW = (source.width - safeLeft - safeRight).coerceAtLeast(1)
            val cropH = (source.height - safeTop - safeBottom).coerceAtLeast(1)

            if (!saveAsNew && safeLeft == 0 && safeTop == 0 && safeRight == 0 && safeBottom == 0) {
                return@withContext asset
            }

            val cropped = Bitmap.createBitmap(source, safeLeft, safeTop, cropW, cropH)
            try {
                if (saveAsNew) {
                    val newId = UUID.randomUUID().toString()
                    val outFile = File(templateDir, "$newId.png")
                    FileOutputStream(outFile).use { out ->
                        cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    val resolvedName = newName?.trim()?.takeIf { it.isNotBlank() } ?: run {
                        if (asset.name.endsWith(" (Crop)")) asset.name else "${asset.name} (Crop)"
                    }
                    val newEntity = TemplateAssetEntity(
                        id = newId,
                        name = resolvedName,
                        filePath = outFile.absolutePath,
                        width = cropped.width,
                        height = cropped.height,
                        createdAt = System.currentTimeMillis()
                    )
                    macroDao.upsertTemplate(newEntity)
                    newEntity
                } else {
                    val tempFile = File(templateDir, "${asset.id}.tmp.png")
                    FileOutputStream(tempFile).use { out ->
                        cropped.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    if (!tempFile.renameTo(sourceFile)) {
                        tempFile.copyTo(sourceFile, overwrite = true)
                        tempFile.delete()
                    }
                    val updatedEntity = asset.copy(
                        width = cropped.width,
                        height = cropped.height,
                        createdAt = System.currentTimeMillis()
                    )
                    macroDao.upsertTemplate(updatedEntity)
                    updatedEntity
                }
            } finally {
                if (cropped !== source) {
                    cropped.recycle()
                }
            }
        } finally {
            source.recycle()
        }
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
                val allSteps = macro.steps + macro.functions.flatMap { it.steps }
                allSteps.filterIsInstance<IfImageStep>().map { it.templateId }
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
                fun remapStepTemplates(steps: List<MacroStep>): List<MacroStep> = steps.map { step ->
                    if (step is IfImageStep && step.templateId in templateIdRemap) {
                        step.copy(templateId = templateIdRemap[step.templateId]!!)
                    } else {
                        step
                    }
                }
                val remappedSteps = remapStepTemplates(macro.steps)
                val remappedFunctions = macro.functions.map { fn ->
                    fn.copy(steps = remapStepTemplates(fn.steps))
                }
                val newMacro = macro.copy(
                    id = UUID.randomUUID().toString(),
                    steps = remappedSteps,
                    functions = remappedFunctions,
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
        val decodedFunctions = runCatching {
            json.decodeFromString<List<MacroFunction>>(functionsJson)
        }.getOrDefault(emptyList())
        val resolvedMode = runCatching {
            InputInjectionMode.valueOf(injectionMode)
        }.getOrDefault(InputInjectionMode.TOUCHSCREEN)
        val resolvedGuard = runCatching {
            TouchGuardPolicy.valueOf(touchGuardPolicy)
        }.getOrDefault(TouchGuardPolicy.OFF)
        return MacroDefinition(
            id = id,
            name = name,
            description = description,
            targetAppLabel = targetAppLabel,
            loopCount = loopCount,
            pollingIntervalMs = pollingIntervalMs,
            injectionMode = resolvedMode,
            touchGuardPolicy = resolvedGuard,
            steps = decodedSteps,
            functions = decodedFunctions,
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
            touchGuardPolicy = touchGuardPolicy.name,
            stepsJson = json.encodeToString(steps),
            functionsJson = json.encodeToString(functions),
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
