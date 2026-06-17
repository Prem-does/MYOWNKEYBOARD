package com.calm.launcher.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View

/**
 * Minimalist keyboard View that draws directly to the Canvas for max performance.
 *
 * Visual design matches Light Phone 3:
 *   - Pure monochrome (black bg, white text)
 *   - No key borders, shadows, or gradients
 *   - Large sans-serif typography
 *   - No pop-up previews on key press (haptic-only feedback)
 *
 * Layout uses a 10-column grid.  Row staggering matches standard phone keyboards:
 *   Row 0 (QWERTYUIOP): 10 keys, full width
 *   Row 1 (ASDFGHJKL):   9 keys, offset 0.5 cols
 *   Row 2 (Shift + ZXCVBNM + Del): shift/del at 1.5x width, letters at 1x
 *   Row 3 (?123 + Space + Enter): mode/enter at 1.5x, space fills remainder
 */
class CalmKeyboardView(context: Context) : View(context) {

    // ── Special key codes ─────────────────────────────────────────────────
    companion object {
        const val KEY_SHIFT = -1
        const val KEY_MODE  = -2
        const val KEY_ENTER = -4
        const val KEY_DEL   = -5
        const val KEY_SPACE = 32
    }

    // ── Key model ─────────────────────────────────────────────────────────
    private class Key(
        val code: Int,
        val label: String,
        val bounds: RectF,
        val isFunctional: Boolean = false
    )

    // ── State ─────────────────────────────────────────────────────────────
    private val keys = mutableListOf<Key>()

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT
        color = Color.WHITE
    }

    private var isSymbols = false
    private var shiftState = 0
    private var enterLabel = "↩"

    var listener: ((Int) -> Unit)? = null
    private var downKey: Key? = null

    // ── Public API ────────────────────────────────────────────────────────

    fun reset() {
        isSymbols = false
        shiftState = 0
        downKey = null
        rebuild()
        invalidate()
    }

    fun toggleMode() {
        isSymbols = !isSymbols
        rebuild()
        invalidate()
    }

    fun updateShiftState(state: Int) {
        shiftState = state
        rebuild()
        invalidate()
    }

    fun updateEnterLabel(label: String) {
        enterLabel = label
        rebuild()
        invalidate()
    }

    // ── Layout helpers ───────────────────────────────────────────────────

    private fun col(col: Float) = col * cellW
    private fun row(row: Float) = row * rowH

    private val cellW: Float get() = width / 10f
    private val rowH: Float get() = height / 4f

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (changed) rebuild()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        val h = (w * 0.45f).toInt().coerceIn(200, 420)
        setMeasuredDimension(w, h)
    }

    private fun rebuild() {
        if (width <= 0 || height <= 0) return
        keys.clear()
        if (isSymbols) buildSymbols() else buildQwerty()
    }

    // ── QWERTY key layout ────────────────────────────────────────────────

    private fun buildQwerty() {
        // Row 0: Q W E R T Y U I O P  (10 keys, full width)
        buildRow("QWERTYUIOP", rowOff = 0f, colOff = 0f) { c ->
            labelFor(c)
        }

        // Row 1: A S D F G H J K L  (9 keys, offset 0.5 cols)
        buildRow("ASDFGHJKL", rowOff = 1f, colOff = 0.5f) { c ->
            labelFor(c)
        }

        // Row 3 (third row): [Shift 1.5x] Z X C V B N M [Delete 1.5x]
        val r2y = row(2f)
        val r2h = rowH

        val shiftLabel = when (shiftState) {
            2 -> "\u21EA"
            1 -> "\u2191"
            else -> "\u21E7"
        }
        keys.add(Key(KEY_SHIFT, shiftLabel,
            RectF(col(0f), r2y, col(1.5f), r2y + r2h), isFunctional = true))

        "ZXCVBNM".forEachIndexed { i, c ->
            val x = col(1.5f + i)
            keys.add(Key(c.code, labelFor(c),
                RectF(x, r2y, x + cellW, r2y + r2h)))
        }

        val delX = col(1.5f + 7f)
        keys.add(Key(KEY_DEL, "\u232B",
            RectF(delX, r2y, delX + col(1.5f), r2y + r2h), isFunctional = true))

        // Row 4 (bottom): [?123 1.5x] [Space flex] [Enter 1.5x]
        val r3y = row(3f)
        val modeW = col(1.5f)
        val entW  = col(1.5f)
        val spaceW = width - modeW - entW

        keys.add(Key(KEY_MODE, "?123",
            RectF(0f, r3y, modeW, r3y + rowH), isFunctional = true))
        keys.add(Key(KEY_SPACE, "",
            RectF(modeW, r3y, modeW + spaceW, r3y + rowH)))
        keys.add(Key(KEY_ENTER, enterLabel,
            RectF(modeW + spaceW, r3y, width.toFloat(), r3y + rowH), isFunctional = true))
    }

    // ── Symbols key layout ───────────────────────────────────────────────

    private fun buildSymbols() {
        // Row 0: 1 2 3 4 5 6 7 8 9 0
        buildRow("1234567890", rowOff = 0f, colOff = 0f) { it.toString() }

        // Row 1: symbols (10 keys, full width)
        val sym1 = listOf('@', '#', '\$', '%', '&', '*', '-', '+', '(', ')')
        buildRow(sym1, rowOff = 1f, colOff = 0f) { it.toString() }

        // Row 2: punctuation centred + Delete
        val r2y = row(2f)
        val sym2 = listOf('.', ',', '?', '!', '\'', '"', ':', ';')
        // 8 punctuation at 1x cellW + Delete at 1.5x = 9.5x  -> padding 0.25x each side
        val pad = col(0.25f)
        sym2.forEachIndexed { i, c ->
            val x = pad + i * cellW
            keys.add(Key(c.code, c.toString(),
                RectF(x, r2y, x + cellW, r2y + rowH)))
        }
        val delX = pad + 8 * cellW
        keys.add(Key(KEY_DEL, "\u232B",
            RectF(delX, r2y, delX + col(1.5f), r2y + rowH), isFunctional = true))

        // Row 3: [ABC 1.5x] [Space flex] [Enter 1.5x]
        val r3y = row(3f)
        val modeW = col(1.5f)
        val entW  = col(1.5f)
        val spaceW = width - modeW - entW

        keys.add(Key(KEY_MODE, "ABC",
            RectF(0f, r3y, modeW, r3y + rowH), isFunctional = true))
        keys.add(Key(KEY_SPACE, "",
            RectF(modeW, r3y, modeW + spaceW, r3y + rowH)))
        keys.add(Key(KEY_ENTER, enterLabel,
            RectF(modeW + spaceW, r3y, width.toFloat(), r3y + rowH), isFunctional = true))
    }

    // ── Row builder helper ───────────────────────────────────────────────

    private fun buildRow(
        chars: CharSequence,
        rowOff: Float,
        colOff: Float,
        label: (Char) -> String
    ) {
        val y = row(rowOff)
        chars.forEachIndexed { i, c ->
            val x = col(colOff + i)
            keys.add(Key(c.code, label(c),
                RectF(x, y, x + cellW, y + rowH)))
        }
    }

    private fun buildRow(
        chars: List<Char>,
        rowOff: Float,
        colOff: Float,
        label: (Char) -> String
    ) {
        val y = row(rowOff)
        chars.forEachIndexed { i, c ->
            val x = col(colOff + i)
            keys.add(Key(c.code, label(c),
                RectF(x, y, x + cellW, y + rowH)))
        }
    }

    private fun labelFor(c: Char): String =
        if (shiftState == 0) c.lowercaseChar().toString() else c.uppercaseChar().toString()

    // ── Canvas drawing ───────────────────────────────────────────────────

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Pure black background
        canvas.drawColor(Color.BLACK)

        val textSize = height / 13f
        val funcTextSize = height / 16f

        for (key in keys) {
            val cx = key.bounds.centerX()
            val cy = key.bounds.centerY()

            // Space bar: draw a thin horizontal line
            if (key.code == KEY_SPACE) {
                drawSpaceBar(canvas, key, cy)
                continue
            }

            paint.textSize = if (key.isFunctional) funcTextSize else textSize
            val baseline = cy - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(key.label, cx, baseline, paint)
        }
    }

    private fun drawSpaceBar(canvas: Canvas, key: Key, centerY: Float) {
        val lineLen = key.bounds.width() * 0.30f
        val lineY = centerY + height * 0.04f
        paint.strokeWidth = maxOf(2f, height * 0.008f)
        paint.style = Paint.Style.STROKE
        canvas.drawLine(
            key.bounds.centerX() - lineLen / 2f, lineY,
            key.bounds.centerX() + lineLen / 2f, lineY,
            paint
        )
        paint.style = Paint.Style.FILL
    }

    // ── Touch handling ───────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                downKey = findKey(event.x, event.y)
                downKey?.let {
                    listener?.invoke(it.code)
                }
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> {
                downKey = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun findKey(x: Float, y: Float): Key? {
        for (key in keys) {
            if (key.bounds.contains(x, y)) return key
        }
        return null
    }
}
