package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.MacroRepository
import com.example.engine.ExecutionStatus
import com.example.engine.MacroInterpreter
import com.example.model.BranchAction
import com.example.model.IfImageStep
import com.example.model.IfVariableStep
import com.example.model.LoopEndStep
import com.example.model.LoopStartStep
import com.example.model.MacroDefinition
import com.example.model.SetVariableStep
import com.example.model.VarComparator
import com.example.model.VarOperation
import com.example.service.OverlayCoordinator
import com.example.service.PickerMode
import com.example.service.PickerRequest
import com.example.service.PickerResult
import com.example.shizuku.ShizukuInputBridge
import com.example.vision.ScreenDetector
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun appName_isShizuMacro() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ShizuMacro", appName)
    }

    @Test
    fun repository_saveExportAndImportRoundTrip() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = MacroRepository(context, db.macroDao())

        val loopId = "loop-1"
        val original = MacroDefinition(
            id = "macro-1",
            name = "Test Macro",
            targetAppLabel = "Sandbox",
            loopCount = 2,
            steps = listOf(
                SetVariableStep(variableName = "count", operation = VarOperation.SET, valueExpression = "1"),
                LoopStartStep(id = loopId, iterations = 3),
                SetVariableStep(variableName = "count", operation = VarOperation.INCREMENT, valueExpression = "2"),
                LoopEndStep(loopStartId = loopId)
            )
        )
        repo.saveMacro(original)

        val jsonPayload = repo.exportMacrosToJson(setOf(original.id))
        repo.deleteMacro(original.id)
        assertTrue(repo.macros.first().isEmpty())

        val imported = repo.importMacrosFromJson(jsonPayload)
        assertEquals(1, imported.getOrThrow())

        val saved = repo.macros.first()
        assertEquals(1, saved.size)
        assertEquals("Test Macro", saved.first().name)
        assertEquals(4, saved.first().steps.size)
        db.close()
    }

    @Test
    fun screenDetector_matchesColorAndTemplate() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val detector = ScreenDetector(context)

        val screen = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(screen)
        canvas.drawColor(Color.BLACK)
        val paint = Paint().apply { color = Color.rgb(16, 185, 129) }
        canvas.drawRect(Rect(40, 50, 80, 90), paint)

        val colorMatch = detector.matchColor(screen, 60, 70, "#10B981", tolerancePct = 5)
        assertTrue(colorMatch.matched)
        assertEquals("#10B981", colorMatch.sampledHex)

        val template = Bitmap.createBitmap(screen, 44, 54, 24, 24)
        val imgMatch = detector.matchTemplate(screen, template, null, minConfidencePct = 90)
        assertTrue(imgMatch.matched)
        assertTrue(imgMatch.confidencePct >= 95)

        template.recycle()
        screen.recycle()
    }

    @Test
    fun interpreter_executesLoopVariablesAndBranching() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = MacroRepository(context, db.macroDao())
        val bridge = ShizukuInputBridge(context)
        val detector = ScreenDetector(context)
        val interpreter = MacroInterpreter(repo, bridge, detector)

        val loopId = "l1"
        val macro = MacroDefinition(
            id = "logic-test",
            name = "Logic Engine Test",
            loopCount = 1,
            steps = listOf(
                SetVariableStep(variableName = "score", operation = VarOperation.SET, valueExpression = "0"),
                LoopStartStep(id = loopId, iterations = 4),
                SetVariableStep(variableName = "score", operation = VarOperation.INCREMENT, valueExpression = "5"),
                LoopEndStep(loopStartId = loopId),
                IfVariableStep(
                    variableName = "score",
                    comparator = VarComparator.EQUALS,
                    compareValue = "20",
                    thenAction = BranchAction.CONTINUE_NEXT,
                    elseAction = BranchAction.STOP_MACRO
                ),
                SetVariableStep(variableName = "verified", operation = VarOperation.SET, valueExpression = "true")
            )
        )

        interpreter.start(macro)
        var attempts = 0
        while (interpreter.state.value.status == ExecutionStatus.RUNNING && attempts < 60) {
            delay(50)
            attempts++
        }

        val finalState = interpreter.state.value
        assertEquals(ExecutionStatus.IDLE, finalState.status)
        assertEquals("20", finalState.variables["score"])
        assertEquals("true", finalState.variables["verified"])
        db.close()
    }

    @Test
    fun overlayCoordinator_preservesPickerDraftAndRoiState() = runBlocking {
        val coordinator = OverlayCoordinator()
        coordinator.setOverlayShowing(true, "m1", "Macro 1")
        coordinator.setOverlayStudioOpen(true, editingStepId = "step-img")
        assertTrue(coordinator.overlayState.value.showOverlayStudio)
        assertEquals("step-img", coordinator.overlayState.value.studioEditingStepId)

        val initialReq = PickerRequest(
            macroId = "m1",
            stepId = "step-img",
            mode = PickerMode.PICK_SEARCH_ROI,
            initialLeft = 100,
            initialTop = 200,
            initialRight = 500,
            initialBottom = 700
        )
        coordinator.startPicker(initialReq)
        assertEquals(PickerMode.PICK_SEARCH_ROI, coordinator.overlayState.value.pickerMode)
        assertEquals(false, coordinator.overlayState.value.showOverlayStudio)

        // Simulate user dragging handles then tapping Switch App
        val draggedReq = initialReq.copy(initialLeft = 140, initialTop = 260, initialRight = 620, initialBottom = 880)
        coordinator.updatePendingPickerRequest(draggedReq)
        coordinator.setPickerPassThrough(true)
        assertTrue(coordinator.overlayState.value.isPickerPassThrough)
        assertEquals(620, coordinator.overlayState.value.pendingPickerRequest?.initialRight)

        // Returning to Place Target preserves dragged bounds
        coordinator.setPickerPassThrough(false)
        assertEquals(140, coordinator.overlayState.value.pendingPickerRequest?.initialLeft)

        coordinator.publishPickerResult(PickerResult.RoiPicked("m1", "step-img", Rect(140, 260, 620, 880)))
        assertEquals(PickerMode.NONE, coordinator.overlayState.value.pickerMode)

        // Verify IfImageStep ROI survives repository save/load
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = MacroRepository(context, db.macroDao())
        repo.saveMacro(
            MacroDefinition(
                id = "m1",
                name = "Vision Macro",
                steps = listOf(
                    IfImageStep(
                        id = "step-img",
                        templateId = "tpl-1",
                        templateName = "Button",
                        searchLeft = 140,
                        searchTop = 260,
                        searchRight = 620,
                        searchBottom = 880
                    )
                )
            )
        )
        val loaded = repo.getMacro("m1")!!.steps.first() as IfImageStep
        assertEquals(140, loaded.searchLeft)
        assertEquals(880, loaded.searchBottom)
        db.close()
    }
}
