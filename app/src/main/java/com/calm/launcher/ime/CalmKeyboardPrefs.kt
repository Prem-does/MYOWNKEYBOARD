package com.calm.launcher.ime

import android.content.Context

class CalmKeyboardPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val hapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS_ENABLED, true)

    val startInSymbols: Boolean
        get() = prefs.getBoolean(KEY_START_IN_SYMBOLS, false)

    val autoShiftReset: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SHIFT_RESET, true)

    val showActionLabelOnEnter: Boolean
        get() = prefs.getBoolean(KEY_SHOW_ACTION_LABEL_ON_ENTER, true)

    companion object {
        private const val PREFS_NAME = "calm_keyboard_prefs"
        private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
        private const val KEY_START_IN_SYMBOLS = "start_in_symbols"
        private const val KEY_AUTO_SHIFT_RESET = "auto_shift_reset"
        private const val KEY_SHOW_ACTION_LABEL_ON_ENTER = "show_action_label_on_enter"
    }
}