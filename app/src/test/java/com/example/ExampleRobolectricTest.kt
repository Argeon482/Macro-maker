package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.InputDevice
import android.view.MotionEvent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.MacroRepository
import com.example.engine.ExecutionStatus
import com.example.engine.MacroInterpreter
import com.example.model.BranchAction
import com.example.model.IfImageStep
import com.example.model.IfVariableStep
import com.example.model.InputInjectionMode
import com.example.model.LoopEndStep
import com.example.model.LoopStartStep
import com.example.model.MacroDefinition
import com.example.model.SetVariableStep
import com.example.model.TouchGuardPolicy
import com.example.model.VarComparator
import com.example.model.VarOperation
import com.example.service.OverlayCoordinator
import com.example.service.PickerMode
import com.example.service.PickerRequest
import com.example.service.PickerResult
import com.example.shizuku.ShizukuInputBridge
import com.example.ui.screens.SandboxMultiPointerRouter
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
        val coordinator = OverlayCoordinator()
        val interpreter = MacroInterpreter(repo, bridge, detector, coordinator)

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

    @Test
    fun macro_injectionMode_persistsAndDefaultsCleanly() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = MacroRepository(context, db.macroDao())

        val stylusMacro = MacroDefinition(
            id = "stylus-1",
            name = "Game Stylus Flow",
            injectionMode = InputInjectionMode.CO_TOUCH_MULTIPLEX,
            touchGuardPolicy = TouchGuardPolicy.YIELD_350MS
        )
        repo.saveMacro(stylusMacro)

        val loaded = repo.getMacro("stylus-1")!!
        assertEquals(InputInjectionMode.CO_TOUCH_MULTIPLEX, loaded.injectionMode)
        assertEquals(TouchGuardPolicy.YIELD_350MS, loaded.touchGuardPolicy)

        val legacyBundleJson = """
            {
              "schemaVersion": 1,
              "exportedAt": 1700000000000,
              "macros": [
                {
                  "id": "legacy-1",
                  "name": "Legacy Flow",
                  "steps": []
                }
              ]
            }
        """.trimIndent()
        val count = repo.importMacrosFromJson(legacyBundleJson).getOrThrow()
        assertEquals(1, count)
        val importedLegacy = repo.macros.first().first { it.name == "Legacy Flow" }
        assertEquals(InputInjectionMode.MOUSE, importedLegacy.injectionMode)
        assertEquals(TouchGuardPolicy.OFF, importedLegacy.touchGuardPolicy)
        db.close()
    }

    @Test
    fun bridgeDiagnosticsAndTouchGuard_trackCooperativeFingerAndOutsideTouch() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bridge = ShizukuInputBridge(context)
        bridge.updateCooperativeFingerState(
            isDown = true,
            screenX = 240f,
            screenY = 1600f,
            downTime = 12345L,
            deviceId = 4
        )
        val diag = bridge.diagnostics.value
        assertTrue(diag.cooperativeFingerDown)
        assertEquals(4, diag.physicalTouchDeviceId)
        assertEquals(240f, diag.cooperativeFingerX, 0.01f)

        val coordinator = OverlayCoordinator()
        assertTrue(coordinator.msSinceLastUserTouch(5000L) > 10_000L)
        coordinator.recordUserTouchActivity(4900L)
        assertEquals(100L, coordinator.msSinceLastUserTouch(5000L))
    }

    @Test
    fun sandboxRouter_routesPointerDownToTargetWhileJoystickDrags() {
        var dragging = false
        var cancelCount = 0
        var targetHits = 0
        var lastTool = ""
        var coopDown = false
        var coopPointerId = -1

        val router = SandboxMultiPointerRouter(
            onJoystickState = { isDragging, _, _, incCancel, _, _ ->
                dragging = isDragging
                if (incCancel) cancelCount++
            },
            onCooperativeFingerUpdate = { isDown, _, _, _, _, pointerId ->
                coopDown = isDown
                coopPointerId = pointerId
            },
            onTargetEvent = { _, _, toolLabel, _, countHit, _ ->
                lastTool = toolLabel
                if (countHit) targetHits++
            }
        )

        val targetRect = RectF(0f, 0f, 400f, 400f)
        val joystickRect = RectF(0f, 420f, 400f, 820f)
        val props0 = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER })
        val coords0 = arrayOf(MotionEvent.PointerCoords().apply { x = 200f; y = 620f; pressure = 1f; size = 1f })

        val down = MotionEvent.obtain(100L, 100L, MotionEvent.ACTION_DOWN, 1, props0, coords0, 0, 0, 1f, 1f, 4, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        router.handleTouchEvent(down, 0f, 0f, 0f, 0f, joystickRect, targetRect, "Co-Touch")
        down.recycle()
        assertTrue(dragging)
        assertTrue(coopDown)
        assertEquals(0, coopPointerId)
        assertEquals(0, targetHits)

        val propsDual = arrayOf(
            MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER },
            MotionEvent.PointerProperties().apply { id = 1; toolType = MotionEvent.TOOL_TYPE_FINGER }
        )
        val coordsDual = arrayOf(
            MotionEvent.PointerCoords().apply { x = 210f; y = 630f; pressure = 1f; size = 1f },
            MotionEvent.PointerCoords().apply { x = 200f; y = 200f; pressure = 1f; size = 1f }
        )
        val pDown = MotionEvent.obtain(
            100L,
            120L,
            MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2,
            propsDual,
            coordsDual,
            0,
            0,
            1f,
            1f,
            4,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0
        )
        router.handleTouchEvent(pDown, 0f, 0f, 0f, 0f, joystickRect, targetRect, "Co-Touch")
        pDown.recycle()

        assertTrue(dragging)
        assertEquals(0, cancelCount)
        assertEquals(1, targetHits)
        assertEquals("FINGER(P1)", lastTool)

        val pUp = MotionEvent.obtain(
            100L,
            165L,
            MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2,
            propsDual,
            coordsDual,
            0,
            0,
            1f,
            1f,
            4,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0
        )
        router.handleTouchEvent(pUp, 0f, 0f, 0f, 0f, joystickRect, targetRect, "Co-Touch")
        pUp.recycle()

        assertTrue(dragging)
        assertEquals(0, cancelCount)
        assertEquals(1, targetHits)
    }
}
