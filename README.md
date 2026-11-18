# ShizuMacro

Shizuku-powered Android touch automation studio using `InputManager.injectInputEvent` (via a Shizuku `UserService`) instead of an Accessibility Service to avoid user touch collisions.

## Building & Installing the Debug APK via GitHub Actions

1. Push this repository to GitHub.
2. Open the **Actions** tab and select **Build Debug APK** (runs automatically on push to `main`/`master` or manually via **Run workflow**).
3. Once the workflow succeeds, download the **ShizuMacro-debug-apk** artifact (`app-debug.apk`).
4. Transfer `app-debug.apk` to your Android phone and install it (enable *Install unknown apps* if prompted).

## Local Build

```bash
base64 -d debug.keystore.base64 > debug.keystore
gradle :app:assembleDebug
```

The output APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Device Setup

1. Install and start **Shizuku** on your phone (Wireless Debugging or ADB).
2. Open **ShizuMacro** and tap the **Shizuku** tile in **System Diagnostics** to grant permission.
3. Tap the **Overlay** tile to grant *Display over other apps* for the floating control dock and target picker.
4. Tap the **Vision** tile when running macros that use pixel color or image template triggers.
