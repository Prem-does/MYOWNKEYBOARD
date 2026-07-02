package com.calm.launcher.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View

class CalmKeyboardView(context: Context) : View(context) {

    companion object {
        const val KEY_SHIFT = -1
        const val KEY_MODE  = -2
        const val KEY_ENTER = -4
        const val KEY_DEL   = -5
        const val KEY_SPACE = 32
        const val KEY_CLOSE = -6
    }

    private enum class KeyRole {
        LETTER,
        FUNCTION,
        ACTION,
        SUGGESTION
    }

    private data class Key(
        val code: Int,
        val label: String,
        val bounds: RectF,
        val role: KeyRole
    )

    private val keys = mutableListOf<Key>()
    private val titleText = "Calm"
    private var suggestionLabels = listOf("the", "to", "and")

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.SANS_SERIF
        color = Color.WHITE
    }

    private val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.SANS_SERIF
        color = Color.argb(160, 255, 255, 255)
    }

    private val activeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A1A1A")
        style = Paint.Style.FILL
    }

    private var isSymbols = false
    private var shiftState = 0
    private var enterLabel = "ENTER"
    private var titleHeight = 0f
    private var suggestionHeight = 0f
    private var rowHeight = 0f
    private var contentTop = 0f
    private var contentLeft = 0f

    private val density get() = resources.displayMetrics.density
    private val defaultHeightDp = 260f
    private val minHeightDp = 260f
    private val maxHeightDp = 280f
    private val minRowHeightDp = 42f
    private val maxRowHeightDp = 48f
    private val titleHeightDp = 40f
    private val suggestionHeightDp = 24f

    var listener: ((Int) -> Unit)? = null
    private var downKey: Key? = null

    fun reset() {
        reset(startInSymbols = false)
    }

    fun reset(startInSymbols: Boolean) {
        isSymbols = startInSymbols
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

    private fun col(col: Float) = contentLeft + col * cellW
    private fun row(row: Float) = contentTop + row * rowHeight

    private val cellW: Float get() = (width - contentLeft * 2f) / 10f

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (changed) rebuild()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        val defaultHeight = (defaultHeightDp * density).toInt()
        val maxHeight = (maxHeightDp * density).toInt()
        val minHeight = (minHeightDp * density).toInt()

        val h = when (MeasureSpec.getMode(heightSpec)) {
            MeasureSpec.EXACTLY -> MeasureSpec.getSize(heightSpec)
            MeasureSpec.AT_MOST -> minOf(defaultHeight, MeasureSpec.getSize(heightSpec))
            else -> defaultHeight
        }.coerceIn(minHeight, maxHeight)

        setMeasuredDimension(w, h)
    }

    private fun rebuild() {
        if (width <= 0 || height <= 0) return
        keys.clear()

        contentLeft = width * 0.04f
        titleHeight = titleHeightDp * density
        suggestionHeight = suggestionHeightDp * density

        rowHeight = (height - titleHeight - suggestionHeight) / 4f
        rowHeight = rowHeight.coerceIn(minRowHeightDp * density, maxRowHeightDp * density)

        contentTop = titleHeight + suggestionHeight
        if (isSymbols) buildSymbols() else buildQwerty()
    }

    private fun addKey(code: Int, label: String, bounds: RectF, role: KeyRole = KeyRole.LETTER) {
        keys.add(Key(code, label, bounds, role))
    }

    private fun buildQwerty() {
        buildSuggestions()

        buildRow("qwertyuiop", rowOff = 0f, colOff = 0f) { c -> labelFor(c) }
        buildRow("asdfghjkl", rowOff = 1f, colOff = 0.5f) { c -> labelFor(c) }

        val thirdRowY = row(2f)
        val unit = cellW
        val wide = unit * 1.5f

        addKey(
            KEY_SHIFT,
            "SHIFT",
            RectF(0f, thirdRowY, wide, thirdRowY + rowHeight),
            KeyRole.FUNCTION
        )

        "zxcvbnm".forEachIndexed { index, c ->
            val x = wide + index * unit
            addKey(c.code, labelFor(c), RectF(x, thirdRowY, x + unit, thirdRowY + rowHeight))
        }

        val deleteX = wide + 7f * unit
        addKey(
            KEY_DEL,
            "DEL",
            RectF(deleteX, thirdRowY, deleteX + wide, thirdRowY + rowHeight),
            KeyRole.FUNCTION
        )

        buildActionRow()
    }

    private fun buildActionRow() {
        val rowY = row(3f)
        val keyAreaWidth = width - contentLeft * 2f
        val leftWidth = maxOf(72f, keyAreaWidth * 0.20f)
        val rightAreaWidth = maxOf(92f, keyAreaWidth * 0.30f)
        val centerWidth = keyAreaWidth - leftWidth - rightAreaWidth
        val rightStart = contentLeft + keyAreaWidth - rightAreaWidth
        val rightGap = width * 0.011f
        val rightButtonWidth = (rightAreaWidth - rightGap) / 2f

        addKey(KEY_MODE, "?123", RectF(contentLeft, rowY, contentLeft + leftWidth, rowY + rowHeight), KeyRole.ACTION)
        addKey(KEY_SPACE, "Space bar", RectF(contentLeft + leftWidth, rowY, contentLeft + leftWidth + centerWidth, rowY + rowHeight), KeyRole.ACTION)
        addKey(KEY_ENTER, "↩︎", RectF(rightStart, rowY, rightStart + rightButtonWidth, rowY + rowHeight), KeyRole.ACTION)
        addKey(KEY_CLOSE, "×", RectF(rightStart + rightButtonWidth + rightGap, rowY, contentLeft + keyAreaWidth, rowY + rowHeight), KeyRole.ACTION)
    }

    fun updateSuggestions(suggestions: List<String>) {
        suggestionLabels = if (suggestions.isEmpty()) listOf("the", "to", "and") else suggestions
        rebuild()
        invalidate()
    }

    private fun buildSuggestions() {
        val keyAreaWidth = width - contentLeft * 2f
        val segmentWidth = keyAreaWidth / suggestionLabels.size.toFloat()
        suggestionLabels.forEachIndexed { index, label ->
            val left = contentLeft + index * segmentWidth
            addKey(
                (label + index).hashCode(),
                label,
                RectF(left, 0f, left + segmentWidth, suggestionHeight),
                KeyRole.SUGGESTION
            )
        }
    }

    private fun buildSymbols() {
        buildRow("1234567890", rowOff = 0f, colOff = 0f) { it.toString() }

        val sym1 = listOf('@', '#', '\$', '%', '&', '*', '-', '+', '(', ')')
        buildRow(sym1, rowOff = 1f, colOff = 0f) { it.toString() }

        val r2y = row(2f)
        val sym2 = listOf('.', ',', '?', '!', '\'', '"', ':', ';')
        val pad = col(0.25f)
        sym2.forEachIndexed { i, c ->
            val x = pad + i * cellW
            addKey(c.code, c.toString(), RectF(x, r2y, x + cellW, r2y + rowHeight))
        }
        val delX = pad + 8 * cellW
        addKey(KEY_DEL, "<", RectF(delX, r2y, delX + col(1.5f), r2y + rowHeight), KeyRole.FUNCTION)

        val r3y = row(3f)
        val modeW = col(1.5f)
        val enterW = col(1.5f)
        val spaceW = width - modeW - enterW

        addKey(KEY_MODE, "ABC", RectF(0f, r3y, modeW, r3y + rowHeight), KeyRole.FUNCTION)
        addKey(KEY_SPACE, "", RectF(modeW, r3y, modeW + spaceW, r3y + rowHeight), KeyRole.ACTION)
        addKey(KEY_ENTER, enterLabel, RectF(modeW + spaceW, r3y, width.toFloat(), r3y + rowHeight), KeyRole.FUNCTION)
    }

    private fun buildRow(
        chars: CharSequence,
        rowOff: Float,
        colOff: Float,
        label: (Char) -> String
    ) {
        val y = row(rowOff)
        chars.forEachIndexed { i, c ->
            val x = col(colOff + i)
            addKey(c.code, label(c), RectF(x, y, x + cellW, y + rowHeight))
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
            addKey(c.code, label(c), RectF(x, y, x + cellW, y + rowHeight))
        }
    }

    private fun labelFor(c: Char): String =
        if (shiftState == 0) c.lowercaseChar().toString() else c.uppercaseChar().toString()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.drawColor(Color.BLACK)

        drawTitle(canvas)
        drawSuggestions(canvas)

        for (key in keys) {
            if (key.role == KeyRole.SUGGESTION) {
                continue
            }

            if (key == downKey) {
                canvas.drawRect(key.bounds, activeFillPaint)
            }

            drawKeyLabel(canvas, key)
        }
    }

    private fun drawTitle(canvas: Canvas) {
        paint.color = Color.WHITE

        paint.textSize = titleHeight * 0.18f
        paint.typeface = Typeface.SANS_SERIF
        val headerBaseline = titleHeight * 0.16f - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText("KEYBOARD", width / 2f, headerBaseline, paint)

        paint.textSize = titleHeight * 0.45f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val titleBaseline = titleHeight * 0.55f - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(titleText, width / 2f, titleBaseline, paint)

        val lineY = titleHeight - 12f
        canvas.drawLine(width * 0.18f, lineY, width * 0.82f, lineY, paint)

        paint.typeface = Typeface.SANS_SERIF
    }

    private fun drawSuggestions(canvas: Canvas) {
        val lineY = suggestionHeight - 1f
        val labelSize = suggestionHeight * 0.42f
        mutedPaint.textSize = labelSize

        keys.filter { it.role == KeyRole.SUGGESTION }.forEachIndexed { index, key ->
            val selected = index == 1
            paint.textSize = labelSize
            paint.typeface = if (selected) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
            paint.color = if (selected) Color.WHITE else Color.argb(150, 255, 255, 255)

            val baseline = key.bounds.centerY() - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(key.label, key.bounds.centerX(), baseline, paint)

            if (selected) {
                val underlineLeft = key.bounds.left + key.bounds.width() * 0.12f
                val underlineRight = key.bounds.right - key.bounds.width() * 0.12f
                canvas.drawLine(underlineLeft, lineY, underlineRight, lineY, paint)
            }
        }

        paint.color = Color.WHITE
        paint.typeface = Typeface.SANS_SERIF
    }

    private fun drawKeyLabel(canvas: Canvas, key: Key) {
        val textSize = when (key.role) {
            KeyRole.LETTER -> height / 13f
            KeyRole.FUNCTION -> height / 16f
            KeyRole.ACTION -> height / 16f
            KeyRole.SUGGESTION -> height / 17f
        }
        paint.textSize = textSize

        val originalAlign = paint.textAlign
        paint.textAlign = if (key.code == KEY_MODE) Paint.Align.LEFT else Paint.Align.CENTER

        val x = if (key.code == KEY_MODE) {
            key.bounds.left + key.bounds.width() * 0.10f
        } else {
            key.bounds.centerX()
        }
        val y = key.bounds.centerY() - (paint.ascent() + paint.descent()) / 2f
        canvas.drawText(key.label, x, y, paint)
        paint.textAlign = originalAlign
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> {
                downKey = findKey(event.x, event.y)
                downKey?.let {
                    listener?.invoke(it.code)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> {
                downKey = null
                invalidate()
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
