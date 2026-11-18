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
    PICK_TEMPLATE_CROP
}

data class PickerRequest(
    val macroId: String,
    val stepId: String,
    val mode: PickerMode,
    val initialX: Float = 540f,
    val initialY: Float = 960f,
    val initialEndX: Float = 540f,
    val initialEndY: Float = 560f
)

sealed interface PickerResult {
    val macroId: String
    val stepId: String

    data class TapPicked(
        override val macroId: String,
        override val stepId: String,
        val x: Float,
        val y: Float
    ) : PickerResult

    data class SwipePicked(
        override val macroId: String,
        override val stepId: String,
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float
    ) : PickerResult

    data class ColorPicked(
        override val macroId: String,
        override val stepId: String,
        val x: Int,
        val y: Int,
        val hexColor: String
    ) : PickerResult

    data class TemplatePicked(
        override val macroId: String,
        override val stepId: String,
        val templateId: String,
        val templateName: String,
        val region: Rect
    ) : PickerResult
}

data class OverlayUiState(
    val isOverlayShowing: Boolean = false,
    val activeMacroId: String? = null,
    val activeMacroName: String = "No Macro Loaded",
    val pickerMode: PickerMode = PickerMode.NONE,
    val pendingPickerRequest: PickerRequest? = null,
    val isPickerPassThrough: Boolean = false
)

class OverlayCoordinator {
    private val _overlayState = MutableStateFlow(OverlayUiState())
    val overlayState: StateFlow<OverlayUiState> = _overlayState.asStateFlow()

    private val _pickerResults = MutableSharedFlow<PickerResult>(extraBufferCapacity = 4)
    val pickerResults: SharedFlow<PickerResult> = _pickerResults.asSharedFlow()

    fun setOverlayShowing(showing: Boolean, macroId: String? = null, macroName: String? = null) {
        _overlayState.update {
            it.copy(
                isOverlayShowing = showing,
                activeMacroId = macroId ?: it.activeMacroId,
                activeMacroName = macroName ?: it.activeMacroName
            )
        }
    }

    fun setLoadedMacro(macroId: String, macroName: String) {
        _overlayState.update {
            it.copy(activeMacroId = macroId, activeMacroName = macroName)
        }
    }

    fun startPicker(request: PickerRequest) {
        _overlayState.update {
            it.copy(
                pickerMode = request.mode,
                pendingPickerRequest = request,
                isPickerPassThrough = false
            )
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
