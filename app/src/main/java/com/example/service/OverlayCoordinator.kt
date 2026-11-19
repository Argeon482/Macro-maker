package com.example.service

import android.graphics.Rect
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class PickerMode {
    NONE,
    PICK_TAP,
    PICK_SWIPE,
    PICK_COLOR,
    PICK_TEMPLATE_CROP,
    PICK_SEARCH_ROI
}

data class PickerRequest(
    val macroId: String,
    val stepId: String,
    val mode: PickerMode,
    val functionId: String? = null,
    val initialX: Float = 540f,
    val initialY: Float = 960f,
    val initialEndX: Float = 540f,
    val initialEndY: Float = 560f,
    val initialLeft: Int = 180,
    val initialTop: Int = 420,
    val initialRight: Int = 900,
    val initialBottom: Int = 1140,
    val returnToAppOnComplete: Boolean = false
)

sealed interface PickerResult {
    val macroId: String
    val stepId: String
    val functionId: String?

    data class TapPicked(
        override val macroId: String,
        override val stepId: String,
        val x: Float,
        val y: Float,
        override val functionId: String? = null
    ) : PickerResult

    data class SwipePicked(
        override val macroId: String,
        override val stepId: String,
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        override val functionId: String? = null
    ) : PickerResult

    data class ColorPicked(
        override val macroId: String,
        override val stepId: String,
        val x: Int,
        val y: Int,
        val hexColor: String,
        override val functionId: String? = null
    ) : PickerResult

    data class TemplatePicked(
        override val macroId: String,
        override val stepId: String,
        val templateId: String,
        val templateName: String,
        val region: Rect,
        override val functionId: String? = null
    ) : PickerResult

    data class RoiPicked(
        override val macroId: String,
        override val stepId: String,
        val region: Rect,
        override val functionId: String? = null
    ) : PickerResult
}

data class OverlayUiState(
    val isOverlayShowing: Boolean = false,
    val activeMacroId: String? = null,
    val activeMacroName: String = "No Macro Loaded",
    val pickerMode: PickerMode = PickerMode.NONE,
    val pendingPickerRequest: PickerRequest? = null,
    val isPickerPassThrough: Boolean = false,
    val showOverlayStudio: Boolean = false,
    val studioEditingStepId: String? = null,
    val studioActiveFunctionId: String? = null,
    val showPinsOverlay: Boolean = false
)

class OverlayCoordinator {
    private val _overlayState = MutableStateFlow(OverlayUiState())
    val overlayState: StateFlow<OverlayUiState> = _overlayState.asStateFlow()

    private val _pickerResults = MutableSharedFlow<PickerResult>(extraBufferCapacity = 8)
    val pickerResults: SharedFlow<PickerResult> = _pickerResults.asSharedFlow()

    @Volatile
    private var lastExternalUserTouchMs: Long = 0L

    fun recordUserTouchActivity(timestampMs: Long = System.currentTimeMillis()) {
        lastExternalUserTouchMs = timestampMs
    }

    fun msSinceLastUserTouch(nowMs: Long = System.currentTimeMillis()): Long {
        val last = lastExternalUserTouchMs
        return if (last <= 0L) Long.MAX_VALUE else (nowMs - last).coerceAtLeast(0L)
    }

    fun setOverlayShowing(showing: Boolean, macroId: String? = null, macroName: String? = null) {
        _overlayState.update {
            it.copy(
                isOverlayShowing = showing,
                activeMacroId = macroId ?: it.activeMacroId,
                activeMacroName = macroName ?: it.activeMacroName,
                showOverlayStudio = if (!showing) false else it.showOverlayStudio,
                studioEditingStepId = if (!showing) null else it.studioEditingStepId,
                studioActiveFunctionId = if (!showing) null else it.studioActiveFunctionId,
                showPinsOverlay = if (!showing) false else it.showPinsOverlay
            )
        }
    }

    fun setLoadedMacro(macroId: String, macroName: String) {
        _overlayState.update {
            it.copy(activeMacroId = macroId, activeMacroName = macroName)
        }
    }

    fun setOverlayStudioOpen(
        open: Boolean,
        editingStepId: String? = null,
        activeFunctionId: String? = null
    ) {
        _overlayState.update {
            it.copy(
                showOverlayStudio = open,
                studioEditingStepId = if (open) editingStepId else null,
                studioActiveFunctionId = if (open) activeFunctionId else it.studioActiveFunctionId,
                showPinsOverlay = if (open) false else it.showPinsOverlay
            )
        }
    }

    fun setPinsOverlayOpen(open: Boolean) {
        _overlayState.update {
            it.copy(
                showPinsOverlay = open,
                showOverlayStudio = if (open) false else it.showOverlayStudio,
                studioEditingStepId = if (open) null else it.studioEditingStepId
            )
        }
    }

    fun startPicker(request: PickerRequest) {
        _overlayState.update {
            it.copy(
                pickerMode = request.mode,
                pendingPickerRequest = request,
                isPickerPassThrough = false,
                showOverlayStudio = false,
                showPinsOverlay = false
            )
        }
    }

    fun updatePendingPickerRequest(request: PickerRequest) {
        _overlayState.update {
            it.copy(pendingPickerRequest = request)
        }
    }

    fun setPickerPassThrough(passThrough: Boolean) {
        _overlayState.update {
            it.copy(isPickerPassThrough = passThrough)
        }
    }

    fun cancelPicker() {
        _overlayState.update {
            it.copy(
                pickerMode = PickerMode.NONE,
                pendingPickerRequest = null,
                isPickerPassThrough = false
            )
        }
    }

    fun publishPickerResult(result: PickerResult) {
        _pickerResults.tryEmit(result)
        _overlayState.update {
            it.copy(
                pickerMode = PickerMode.NONE,
                pendingPickerRequest = null,
                isPickerPassThrough = false
            )
        }
    }
}
