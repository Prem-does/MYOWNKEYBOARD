package com.calm.launcher.ime

import android.content.Context
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit

class CalmKeyboardSettingsActivity : AppCompatActivity() {

    private val prefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val background = 0xFF000000.toInt()
        val foreground = 0xFFFFFFFF.toInt()
        val surface = 0xFF111111.toInt()

        val root = ScrollView(this).apply {
            setBackgroundColor(background)
            isFillViewport = true
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 64)
        }

        val title = TextView(this).apply {
            text = "Calm Keyboard"
            textSize = 28f
            setTextColor(foreground)
        }

        val subtitle = TextView(this).apply {
            text = "Minimal keyboard preferences"
            textSize = 15f
            setTextColor(0xFFB0B0B0.toInt())
        }

        content.addView(title)
        content.addView(subtitle)
        content.addView(spacer())

        content.addView(settingSwitch(
            label = "Haptic feedback",
            description = "Use keyboard press vibration on every key",
            checked = prefs.getBoolean(KEY_HAPTICS_ENABLED, true),
            surface = surface,
            foreground = foreground
        ) { checked -> prefs.edit { putBoolean(KEY_HAPTICS_ENABLED, checked) } })

        content.addView(settingSwitch(
            label = "Start in symbols mode",
            description = "Open the keyboard on the ?123 layer",
            checked = prefs.getBoolean(KEY_START_IN_SYMBOLS, false),
            surface = surface,
            foreground = foreground
        ) { checked -> prefs.edit { putBoolean(KEY_START_IN_SYMBOLS, checked) } })

        content.addView(settingSwitch(
            label = "Auto-reset shift",
            description = "Return to lowercase after typing one letter",
            checked = prefs.getBoolean(KEY_AUTO_SHIFT_RESET, true),
            surface = surface,
            foreground = foreground
        ) { checked -> prefs.edit { putBoolean(KEY_AUTO_SHIFT_RESET, checked) } })

        content.addView(settingSwitch(
            label = "Show editor action on Enter",
            description = "Display Search, Go, Next, Done, or Send when available",
            checked = prefs.getBoolean(KEY_SHOW_ACTION_LABEL_ON_ENTER, true),
            surface = surface,
            foreground = foreground
        ) { checked -> prefs.edit { putBoolean(KEY_SHOW_ACTION_LABEL_ON_ENTER, checked) } })

        root.addView(content)
        setContentView(root)
    }

    private fun settingSwitch(
        label: String,
        description: String,
        checked: Boolean,
        surface: Int,
        foreground: Int,
        onChanged: (Boolean) -> Unit
    ): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 28, 32, 28)
            setBackgroundColor(surface)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val textBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val titleView = TextView(this).apply {
            text = label
            textSize = 18f
            setTextColor(foreground)
        }

        val descView = TextView(this).apply {
            text = description
            textSize = 13f
            setTextColor(0xFFB0B0B0.toInt())
        }

        val switch = SwitchCompat(this).apply {
            isChecked = checked
            setTextColor(foreground)
            setOnCheckedChangeListener { _, isChecked -> onChanged(isChecked) }
        }

        textBlock.addView(titleView)
        textBlock.addView(descView)
        header.addView(textBlock)
        header.addView(switch)
        row.addView(header)

        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = 24
        row.layoutParams = params
        return row
    }

    private fun spacer(): TextView {
        return TextView(this).apply {
            text = ""
            textSize = 8f
        }
    }

    companion object {
        private const val PREFS_NAME = "calm_keyboard_prefs"
        private const val KEY_HAPTICS_ENABLED = "haptics_enabled"
        private const val KEY_START_IN_SYMBOLS = "start_in_symbols"
        private const val KEY_AUTO_SHIFT_RESET = "auto_shift_reset"
        private const val KEY_SHOW_ACTION_LABEL_ON_ENTER = "show_action_label_on_enter"
    }
}