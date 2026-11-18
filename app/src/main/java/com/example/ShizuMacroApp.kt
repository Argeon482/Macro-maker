package com.example

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.MacroRepository
import com.example.engine.MacroInterpreter
import com.example.service.OverlayCoordinator
import com.example.shizuku.ShizukuInputBridge
import com.example.vision.ScreenDetector

class ShizuMacroApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var repository: MacroRepository
        private set
    lateinit var shizukuBridge: ShizukuInputBridge
        private set
    lateinit var screenDetector: ScreenDetector
        private set
    lateinit var interpreter: MacroInterpreter
        private set
    lateinit var overlayCoordinator: OverlayCoordinator
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        repository = MacroRepository(this, database.macroDao())
        shizukuBridge = ShizukuInputBridge(this)
        screenDetector = ScreenDetector(this)
        interpreter = MacroInterpreter(repository, shizukuBridge, screenDetector)
        overlayCoordinator = OverlayCoordinator()

        shizukuBridge.registerListeners()
    }
}
