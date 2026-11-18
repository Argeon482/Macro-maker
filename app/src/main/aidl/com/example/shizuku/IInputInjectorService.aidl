package com.example.shizuku;

import android.view.InputEvent;

interface IInputInjectorService {
    boolean injectInputEvent(in InputEvent event, int mode);
    boolean ping();
    String probeCapabilities();
    boolean startTouchMonitor();
    void stopTouchMonitor();
    String readLatestFingerState();
    String getLastInjectDiagnostic();
    boolean injectEvdevTap(float screenX, float screenY, int screenW, int screenH, long holdMs);
    boolean injectEvdevSwipe(float sx, float sy, float ex, float ey, int screenW, int screenH, long durationMs, int steps);
    boolean injectUhidTap(float screenX, float screenY, int screenW, int screenH, long holdMs);
    boolean injectUhidSwipe(float sx, float sy, float ex, float ey, int screenW, int screenH, long durationMs, int steps);
    void destroy();
}
