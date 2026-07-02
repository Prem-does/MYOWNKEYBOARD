package com.calm.launcher.ime

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.EditorInfo

/**
 * The core Input Method Service for Calm Keyboard.
 * Bridges the Android input system with our custom Canvas-drawn keyboard view.
 *
 * Shift state machine: OFF -> ON (single tap) -> LOCK (double-tap within 400ms) -> OFF
 * Auto-reverts to lowercase after typing a letter in SHIFT_ON mode.
 */
class CalmInputService : InputMethodService() {

    private lateinit var keyboardView: CalmKeyboardView
    private lateinit var prefs: CalmKeyboardPrefs
    private lateinit var suggestionEngine: WordSuggestionEngine

    private var shiftState = SHIFT_OFF
    private var lastShiftTapTime = 0L

    companion object {
        const val SHIFT_OFF = 0
        const val SHIFT_ON = 1
        const val SHIFT_LOCK = 2
        private const val DOUBLE_TAP_MS = 400L
    }

    override fun onCreateInputView(): View {
        prefs = CalmKeyboardPrefs(this)
        suggestionEngine = WordSuggestionEngine(this, "wordfreq_large_en.csv")
        keyboardView = CalmKeyboardView(this)
        keyboardView.listener = this::onKey
        keyboardView.suggestionListener = this::onSuggestion
        return keyboardView
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        shiftState = SHIFT_OFF
        keyboardView.updateShiftState(SHIFT_OFF)
        keyboardView.reset(prefs.startInSymbols)
        info?.let { updateEnterLabel(it) }
    }

    private fun updateEnterLabel(info: EditorInfo) {
        if (!prefs.showActionLabelOnEnter) {
            keyboardView.updateEnterLabel("↩")
            return
        }

        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val label = when (action) {
            EditorInfo.IME_ACTION_SEARCH -> "Search"
            EditorInfo.IME_ACTION_GO    -> "Go"
            EditorInfo.IME_ACTION_NEXT  -> "Next"
            EditorInfo.IME_ACTION_DONE  -> "Done"
            EditorInfo.IME_ACTION_SEND  -> "Send"
            else -> null
        }
        keyboardView.updateEnterLabel(label ?: "↩")
    }

    /** Trigger haptic feedback with API-level compatibility. */
    private fun performHapticFeedback() {
        if (!prefs.hapticsEnabled) return

        @Suppress("DEPRECATION")
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.KEYBOARD_PRESS
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        keyboardView.performHapticFeedback(constant)
    }

    private fun onKey(code: Int) {
        performHapticFeedback()

        when (code) {
            CalmKeyboardView.KEY_DEL    -> handleDelete()
            CalmKeyboardView.KEY_SHIFT  -> handleShift()
            CalmKeyboardView.KEY_SPACE  -> handleSpace()
            CalmKeyboardView.KEY_ENTER  -> handleEnter()
            CalmKeyboardView.KEY_CLOSE  -> handleClose()
            CalmKeyboardView.KEY_MODE   -> handleModeToggle()
            else                        -> handleChar(code)
        }
    }

    private fun handleDelete() {
        currentInputConnection?.deleteSurroundingText(1, 0)
    }

    private fun handleSpace() {
        currentInputConnection?.commitText(" ", 1)
    }

    private fun handleEnter() {
        val ic = currentInputConnection ?: return
        val action = currentInputEditorInfo.imeOptions and EditorInfo.IME_MASK_ACTION
        val noEnter = currentInputEditorInfo.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION
        if (action == EditorInfo.IME_ACTION_UNSPECIFIED || noEnter != 0) {
            ic.commitText("\n", 1)
        } else {
            ic.performEditorAction(action)
        }
    }

    private fun handleClose() {
        requestHideSelf(0)
    }

    private fun handleChar(code: Int) {
        val char = code.toChar().toString()
        currentInputConnection?.commitText(char, 1)

        if (prefs.autoShiftReset && shiftState == SHIFT_ON && Character.isLetter(code)) {
            shiftState = SHIFT_OFF
            keyboardView.updateShiftState(SHIFT_OFF)
        }

        updateSuggestions()
    }

    private fun onSuggestion(word: String) {
        val ic = currentInputConnection ?: return
        val surrounding = ic.getTextBeforeCursor(32, 0)?.toString() ?: ""
        val lastWord = surrounding.takeLastWhile { it.isLetter() || it == '\'' }
        if (lastWord.isNotEmpty()) {
            ic.deleteSurroundingText(lastWord.length, 0)
        }
        ic.commitText(word + " ", 1)
        suggestionEngine.learnWord(word)
        updateSuggestions()
    }

    private fun updateSuggestions() {
        val ic = currentInputConnection ?: return
        val surrounding = ic.getTextBeforeCursor(32, 0)?.toString() ?: ""
        val lastWord = surrounding.takeLastWhile { it.isLetter() || it == '\'' }
        val suggestions = suggestionEngine.suggest(lastWord)
        keyboardView.updateSuggestions(suggestions)
    }

    private fun handleModeToggle() {
        keyboardView.toggleMode()
    }

    private fun handleShift() {
        val now = System.currentTimeMillis()
        shiftState = when (shiftState) {
            SHIFT_OFF  -> { lastShiftTapTime = now; SHIFT_ON }
            SHIFT_ON   -> if (now - lastShiftTapTime < DOUBLE_TAP_MS) SHIFT_LOCK else SHIFT_OFF
            SHIFT_LOCK -> SHIFT_OFF
            else       -> SHIFT_OFF
        }
        keyboardView.updateShiftState(shiftState)
    }
}
