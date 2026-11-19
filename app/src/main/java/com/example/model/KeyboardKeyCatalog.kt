package com.example.model

import android.view.KeyEvent

enum class KeyCategory(val title: String) {
    GAME_ACTION("Game"),
    HOTBAR("1–0"),
    LETTERS("A–Z"),
    ARROWS_FN("Arrows/Fn"),
    GAMEPAD("Gamepad"),
    SYSTEM("System")
}

data class KeyBindingOption(
    val keyCode: Int,
    val label: String,
    val category: KeyCategory
)

object KeyboardKeyCatalog {

    val allOptions: List<KeyBindingOption> = buildList {
        // Common game action keys
        add(KeyBindingOption(KeyEvent.KEYCODE_SPACE, "Space", KeyCategory.GAME_ACTION))
        add(KeyBindingOption(KeyEvent.KEYCODE_Q, "Q", KeyCategory.GAME_ACTION))
        add(KeyBindingOption(KeyEvent.KEYCODE_E, "E", KeyCategory.GAME_ACTION))
        add(KeyBindingOption(KeyEvent.KEYCODE_R, "R", KeyCategory.GAME_ACTION))
        add(KeyBindingOption(KeyEvent.KEYCODE_F, "F", KeyCategory.GAME_ACTION))
        add(KeyBindingOption(KeyEvent.KEYCODE_G, "G", KeyCategory.GAME_ACTION))
        add(KeyBindingOption(KeyEvent.KEYCODE_C, "C", KeyCategory.GAME_ACTION))
        add(KeyBindingOption(KeyEvent.KEYCODE_V, "V", KeyCategory.GAME_ACTION))
        add(KeyBindingOption(KeyEvent.KEYCODE_X, "X", KeyCategory.GAME_ACTION))
        add(KeyBindingOption(KeyEvent.KEYCODE_Z, "Z", KeyCategory.GAME_ACTION))
        add(KeyBindingOption(KeyEvent.KEYCODE_TAB, "Tab", KeyCategory.GAME_ACTION))
        add(KeyBindingOption(KeyEvent.KEYCODE_SHIFT_LEFT, "Shift", KeyCategory.GAME_ACTION))
        add(KeyBindingOption(KeyEvent.KEYCODE_CTRL_LEFT, "Ctrl", KeyCategory.GAME_ACTION))
        add(KeyBindingOption(KeyEvent.KEYCODE_ALT_LEFT, "Alt", KeyCategory.GAME_ACTION))
        add(KeyBindingOption(KeyEvent.KEYCODE_ENTER, "Enter", KeyCategory.GAME_ACTION))
        add(KeyBindingOption(KeyEvent.KEYCODE_ESCAPE, "Esc", KeyCategory.GAME_ACTION))
        add(KeyBindingOption(KeyEvent.KEYCODE_DEL, "Bksp", KeyCategory.GAME_ACTION))

        // Hotbar / number row
        add(KeyBindingOption(KeyEvent.KEYCODE_1, "1", KeyCategory.HOTBAR))
        add(KeyBindingOption(KeyEvent.KEYCODE_2, "2", KeyCategory.HOTBAR))
        add(KeyBindingOption(KeyEvent.KEYCODE_3, "3", KeyCategory.HOTBAR))
        add(KeyBindingOption(KeyEvent.KEYCODE_4, "4", KeyCategory.HOTBAR))
        add(KeyBindingOption(KeyEvent.KEYCODE_5, "5", KeyCategory.HOTBAR))
        add(KeyBindingOption(KeyEvent.KEYCODE_6, "6", KeyCategory.HOTBAR))
        add(KeyBindingOption(KeyEvent.KEYCODE_7, "7", KeyCategory.HOTBAR))
        add(KeyBindingOption(KeyEvent.KEYCODE_8, "8", KeyCategory.HOTBAR))
        add(KeyBindingOption(KeyEvent.KEYCODE_9, "9", KeyCategory.HOTBAR))
        add(KeyBindingOption(KeyEvent.KEYCODE_0, "0", KeyCategory.HOTBAR))
        add(KeyBindingOption(KeyEvent.KEYCODE_MINUS, "-", KeyCategory.HOTBAR))
        add(KeyBindingOption(KeyEvent.KEYCODE_EQUALS, "=", KeyCategory.HOTBAR))

        // Full A–Z alphabet
        for (i in 0 until 26) {
            val ch = ('A'.code + i).toChar()
            add(KeyBindingOption(KeyEvent.KEYCODE_A + i, ch.toString(), KeyCategory.LETTERS))
        }

        // Arrows & Function keys
        add(KeyBindingOption(KeyEvent.KEYCODE_DPAD_UP, "Up", KeyCategory.ARROWS_FN))
        add(KeyBindingOption(KeyEvent.KEYCODE_DPAD_DOWN, "Down", KeyCategory.ARROWS_FN))
        add(KeyBindingOption(KeyEvent.KEYCODE_DPAD_LEFT, "Left", KeyCategory.ARROWS_FN))
        add(KeyBindingOption(KeyEvent.KEYCODE_DPAD_RIGHT, "Right", KeyCategory.ARROWS_FN))
        for (fn in 1..12) {
            add(KeyBindingOption(KeyEvent.KEYCODE_F1 + (fn - 1), "F$fn", KeyCategory.ARROWS_FN))
        }

        // Gamepad buttons
        add(KeyBindingOption(KeyEvent.KEYCODE_BUTTON_A, "Btn A", KeyCategory.GAMEPAD))
        add(KeyBindingOption(KeyEvent.KEYCODE_BUTTON_B, "Btn B", KeyCategory.GAMEPAD))
        add(KeyBindingOption(KeyEvent.KEYCODE_BUTTON_X, "Btn X", KeyCategory.GAMEPAD))
        add(KeyBindingOption(KeyEvent.KEYCODE_BUTTON_Y, "Btn Y", KeyCategory.GAMEPAD))
        add(KeyBindingOption(KeyEvent.KEYCODE_BUTTON_L1, "L1", KeyCategory.GAMEPAD))
        add(KeyBindingOption(KeyEvent.KEYCODE_BUTTON_R1, "R1", KeyCategory.GAMEPAD))
        add(KeyBindingOption(KeyEvent.KEYCODE_BUTTON_START, "Start", KeyCategory.GAMEPAD))
        add(KeyBindingOption(KeyEvent.KEYCODE_BUTTON_SELECT, "Select", KeyCategory.GAMEPAD))

        // System keys
        add(KeyBindingOption(KeyEvent.KEYCODE_BACK, "Back", KeyCategory.SYSTEM))
        add(KeyBindingOption(KeyEvent.KEYCODE_HOME, "Home", KeyCategory.SYSTEM))
        add(KeyBindingOption(KeyEvent.KEYCODE_APP_SWITCH, "Recents", KeyCategory.SYSTEM))
    }

    val sandboxQuickKeys: List<KeyBindingOption> = listOf(
        KeyBindingOption(KeyEvent.KEYCODE_SPACE, "Space", KeyCategory.GAME_ACTION),
        KeyBindingOption(KeyEvent.KEYCODE_Q, "Q", KeyCategory.GAME_ACTION),
        KeyBindingOption(KeyEvent.KEYCODE_E, "E", KeyCategory.GAME_ACTION),
        KeyBindingOption(KeyEvent.KEYCODE_R, "R", KeyCategory.GAME_ACTION),
        KeyBindingOption(KeyEvent.KEYCODE_F, "F", KeyCategory.GAME_ACTION),
        KeyBindingOption(KeyEvent.KEYCODE_1, "1", KeyCategory.HOTBAR),
        KeyBindingOption(KeyEvent.KEYCODE_2, "2", KeyCategory.HOTBAR),
        KeyBindingOption(KeyEvent.KEYCODE_3, "3", KeyCategory.HOTBAR),
        KeyBindingOption(KeyEvent.KEYCODE_TAB, "Tab", KeyCategory.GAME_ACTION),
        KeyBindingOption(KeyEvent.KEYCODE_ENTER, "Enter", KeyCategory.GAME_ACTION),
        KeyBindingOption(KeyEvent.KEYCODE_BUTTON_A, "Btn A", KeyCategory.GAMEPAD)
    )

    private val labelByCode: Map<Int, String> = allOptions
        .distinctBy { it.keyCode }
        .associate { it.keyCode to it.label }

    fun optionsForCategory(category: KeyCategory): List<KeyBindingOption> =
        allOptions.filter { it.category == category }

    fun labelForKeyCode(keyCode: Int): String {
        labelByCode[keyCode]?.let { return it }
        val raw = runCatching { KeyEvent.keyCodeToString(keyCode) }.getOrDefault("KEY_$keyCode")
        return raw.removePrefix("KEYCODE_")
    }

    fun formatMetaPrefix(metaState: Int): String {
        if (metaState == 0) return ""
        return buildString {
            if ((metaState and KeyEvent.META_CTRL_ON) != 0) append("Ctrl+")
            if ((metaState and KeyEvent.META_ALT_ON) != 0) append("Alt+")
            if ((metaState and KeyEvent.META_SHIFT_ON) != 0) append("Shift+")
        }
    }
}
