package com.example.shizuku;

import android.view.InputEvent;

interface IInputInjectorService {
    boolean injectInputEvent(in InputEvent event, int mode);
    boolean ping();
    String probeCapabilities();
    boolean startTouchMonitor();
    void stopTouchMonitor();
    String readLatestFingerState();
    void destroy();
}
