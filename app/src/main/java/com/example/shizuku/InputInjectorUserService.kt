package com.example.shizuku

import android.annotation.SuppressLint
import android.hardware.input.InputManager
import android.util.Log
import android.view.InputEvent
import java.lang.reflect.Method
import kotlin.system.exitProcess

class InputInjectorUserService : IInputInjectorService.Stub() {

    private var inputManagerInstance: Any? = null
    private var injectInputEventMethod: Method? = null

    init {
        resolveInputManager()
    }

    @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
    private fun resolveInputManager() {
        try {
            val imClass = InputManager::class.java
            val getInstance = imClass.getDeclaredMethod("getInstance")
            getInstance.isAccessible = true
            inputManagerInstance = getInstance.invoke(null)

            injectInputEventMethod = imClass.getDeclaredMethod(
                "injectInputEvent",
                InputEvent::class.java,
                Int::class.javaPrimitiveType
            )
            injectInputEventMethod?.isAccessible = true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to reflect InputManager.injectInputEvent", t)
        }
    }

    override fun injectInputEvent(event: InputEvent?, mode: Int): Boolean {
        if (event == null) return false
        val im = inputManagerInstance ?: run {
            resolveInputManager()
            inputManagerInstance
        } ?: return false
        val method = injectInputEventMethod ?: return false
        return try {
            (method.invoke(im, event, mode) as? Boolean) ?: false
        } catch (t: Throwable) {
            Log.e(TAG, "injectInputEvent failed", t)
            false
        }
    }

    override fun ping(): Boolean = inputManagerInstance != null && injectInputEventMethod != null

    override fun destroy() {
        exitProcess(0)
    }

    companion object {
        private const val TAG = "ShizuMacroUserService"
    }
}
