package com.calm.launcher.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

class CalmKeyboardView(context: Context) : View(context) {

    companion object {
        const val KEY_SHIFT = -1
        const val KEY_MODE  = -2
        const val KEY_ENTER = -4
        const val KEY_DEL   = -5
        const val KEY_SPACE = 32
        const val KEY_CLOSE = -6
        const val KEY_SUGGESTION = -7
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
        val role: KeyRole,
        val payload: String? = null
    )

    private val keys = mutableListOf<Key>()
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

    private val suggestionFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(40, 255, 255, 255)
        style = Paint.Style.FILL
    }

    private val suggestionStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private var isSymbols = false
    private var shiftState = 0
    private var enterLabel = "ENTER"
    private var titleHeight = 0f
    private var suggestionHeight = 0f
    private var rowHeight = 0f
    private var contentTop = 0f
    private var contentLeft = 0f
    private var shiftLongPressed = false
    private var shiftLongPressRunnable: Runnable? = null
    private var deleteRepeatRunnable: Runnable? = null
    private val deleteRepeatInitialDelayMs = 120L
    private val deleteRepeatIntervalMs = 45L

    private val density get() = resources.displayMetrics.density
    private val defaultHeightDp = 260f
    private val minHeightDp = 260f
    private val maxHeightDp = 280f
    private val minRowHeightDp = 44f
    private val maxRowHeightDp = 58f
    private val titleHeightDp = 0f
    private val suggestionHeightDp = 34f
    private val keyGapDp = 5f
    private val suggestionGapDp = 8f

    var listener: ((Int) -> Unit)? = null
    var suggestionListener: ((String) -> Unit)? = null
    var shiftLongPressListener: (() -> Unit)? = null
    private var downKey: Key? = null
    private var activePointerId = -1

    fun reset() {
        reset(startInSymbols = false)
    }

    fun reset(startInSymbols: Boolean) {
        stopDeleteRepeat()
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

    private fun col(col: Float) = contentLeft + col * cellW + col * keyGap
    private fun row(row: Float) = contentTop + row * rowHeight

    private val keyGap: Float get() = keyGapDp * density
    private val cellW: Float get() = (width - contentLeft * 2f - keyGap * 9f) / 10f

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

        contentLeft = width * 0.035f
        titleHeight = 0f
        suggestionHeight = suggestionHeightDp * density

        rowHeight = (height - suggestionHeight) / 4f
        rowHeight = rowHeight.coerceIn(minRowHeightDp * density, maxRowHeightDp * density)

        contentTop = suggestionHeight
        if (isSymbols) buildSymbols() else buildQwerty()
    }

    private fun addKey(code: Int, label: String, bounds: RectF, role: KeyRole = KeyRole.LETTER, payload: String? = null) {
        keys.add(Key(code, label, bounds, role, payload))
    }

    private fun buildQwerty() {
        buildSuggestions()

        buildRow("qwertyuiop", rowOff = 0f, colOff = 0f) { c -> labelFor(c) }
        buildRow("asdfghjkl", rowOff = 1f, colOff = 0.5f) { c -> labelFor(c) }

        val thirdRowY = row(2f)
        val unit = cellW
        val wide = unit * 1.5f
        val gap = keyGap
        val rowLeft = contentLeft
        val shiftRight = rowLeft + wide

        addKey(
            KEY_SHIFT,
            shiftLabel(),
            RectF(rowLeft, thirdRowY, shiftRight, thirdRowY + rowHeight),
            KeyRole.FUNCTION
        )

        "zxcvbnm".forEachIndexed { index, c ->
            val x = shiftRight + gap + index * (unit + gap)
            addKey(c.code, labelFor(c), RectF(x, thirdRowY, x + unit, thirdRowY + rowHeight))
        }

        val deleteX = shiftRight + gap + 7f * (unit + gap)
        addKey(
            KEY_DEL,
            "<",
            RectF(deleteX, thirdRowY, deleteX + wide, thirdRowY + rowHeight),
            KeyRole.FUNCTION
        )

        buildActionRow()
    }

    private fun buildActionRow() {
        val rowY = row(3f)
        val unit = cellW
        val leftWidth = maxOf(72f, unit * 1.5f)
        val rightWidth = maxOf(72f, unit * 1.5f)
        val gap = keyGap
        val keyAreaWidth = width - contentLeft * 2f
        val spaceWidth = keyAreaWidth - leftWidth - rightWidth * 2f - gap * 2f

        addKey(KEY_MODE, "?123", RectF(contentLeft, rowY, contentLeft + leftWidth, rowY + rowHeight), KeyRole.ACTION)
        addKey(
            KEY_SPACE,
            "Space bar",
            RectF(contentLeft + leftWidth + gap, rowY, contentLeft + leftWidth + gap + spaceWidth, rowY + rowHeight),
            KeyRole.ACTION
        )
        addKey(
            KEY_ENTER,
            "↩︎",
            RectF(contentLeft + leftWidth + gap + spaceWidth + gap, rowY, contentLeft + leftWidth + gap + spaceWidth + gap + rightWidth, rowY + rowHeight),
            KeyRole.ACTION
        )
        addKey(
            KEY_CLOSE,
            "×",
            RectF(contentLeft + leftWidth + gap + spaceWidth + gap + rightWidth + gap, rowY, contentLeft + keyAreaWidth, rowY + rowHeight),
            KeyRole.ACTION
        )
    }

    fun updateSuggestions(suggestions: List<String>) {
        suggestionLabels = suggestions
        rebuild()
        invalidate()
    }

    private fun buildSuggestions() {
        if (suggestionLabels.isEmpty()) return
        val gap = suggestionGapDp * density
        val keyAreaWidth = width - contentLeft * 2f - gap * (suggestionLabels.size - 1)
        val segmentWidth = keyAreaWidth / suggestionLabels.size.toFloat()
        suggestionLabels.forEachIndexed { index, label ->
            val left = contentLeft + index * (segmentWidth + gap)
            addKey(
                KEY_SUGGESTION,
                label,
                RectF(left, 0f, left + segmentWidth, suggestionHeight),
                KeyRole.SUGGESTION,
                label
            )
        }
    }

    private fun buildSymbols() {
        buildRow("1234567890", rowOff = 0f, colOff = 0f) { it.toString() }

        val sym1 = listOf('@', '#', '\$', '%', '&', '*', '-', '+', '(', ')')
        buildRow(sym1, rowOff = 1f, colOff = 0f) { it.toString() }

        val r2y = row(2f)
        val sym2 = listOf('.', ',', '?', '!', '\'', '"', ':', ';')
        val gap = keyGap
        val pad = contentLeft
        val wide = maxOf(72f, cellW * 1.5f)

        sym2.forEachIndexed { i, c ->
            val x = pad + i * (cellW + gap)
            addKey(c.code, c.toString(), RectF(x, r2y, x + cellW, r2y + rowHeight))
        }
        val delX = pad + sym2.size * (cellW + gap)
        addKey(KEY_DEL, "<", RectF(delX, r2y, delX + wide, r2y + rowHeight), KeyRole.FUNCTION)

        val r3y = row(3f)
        val leftWidth = wide
        val rightWidth = wide
        val spaceWidth = width - contentLeft * 2f - leftWidth - rightWidth - gap * 2f

        addKey(KEY_MODE, "ABC", RectF(contentLeft, r3y, contentLeft + leftWidth, r3y + rowHeight), KeyRole.FUNCTION)
        addKey(KEY_SPACE, "", RectF(contentLeft + leftWidth + gap, r3y, contentLeft + leftWidth + gap + spaceWidth, r3y + rowHeight), KeyRole.ACTION)
        addKey(KEY_ENTER, "↩︎", RectF(contentLeft + leftWidth + gap + spaceWidth + gap, r3y, contentLeft + leftWidth + gap + spaceWidth + gap + rightWidth, r3y + rowHeight), KeyRole.FUNCTION)
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

    private fun shiftLabel(): String =
        if (shiftState == 0) "^" else "↓"

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
        // No title label. Suggestion row has enough space.
    }

    private fun drawSuggestions(canvas: Canvas) {
        if (suggestionLabels.isEmpty()) return

        val labelSize = suggestionHeight * 0.42f
        paint.textSize = labelSize
        paint.typeface = Typeface.SANS_SERIF
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER

        val suggestionKeys = keys.filter { it.role == KeyRole.SUGGESTION }
        suggestionKeys.forEachIndexed { index, key ->
            val displayLabel = key.label.replaceFirstChar { it.uppercaseChar() }
            val baseline = key.bounds.centerY() - (paint.ascent() + paint.descent()) / 2f
            canvas.drawText(displayLabel, key.bounds.centerX(), baseline, paint)

            if (index < suggestionKeys.size - 1) {
                val separatorX = key.bounds.right + key.bounds.width() * 0.05f
                canvas.drawText("|", separatorX, baseline, mutedPaint)
            }
        }
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
        val actionIndex = event.actionIndex
        val pointerId = event.getPointerId(actionIndex)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = pointerId
                handleTouchDown(event.getX(actionIndex), event.getY(actionIndex))
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (pointerId != activePointerId) return true
                activePointerId = -1
                handleTouchUp()
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                activePointerId = -1
                handleTouchUp()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTouchDown(x: Float, y: Float) {
        downKey = findKey(x, y)
        shiftLongPressed = false
        shiftLongPressRunnable?.let { removeCallbacks(it) }
        stopDeleteRepeat()

        if (downKey?.code == KEY_SHIFT) {
            shiftLongPressRunnable = Runnable {
                if (downKey?.code == KEY_SHIFT) {
                    shiftLongPressed = true
                    shiftLongPressListener?.invoke()
                    invalidate()
                }
            }
            postDelayed(shiftLongPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
        } else if (downKey?.code == KEY_DEL) {
            listener?.invoke(KEY_DEL)
            deleteRepeatRunnable = object : Runnable {
                override fun run() {
                    if (downKey?.code == KEY_DEL) {
                        listener?.invoke(KEY_DEL)
                        postDelayed(this, deleteRepeatIntervalMs)
                    } else {
                        stopDeleteRepeat()
                    }
                }
            }
            postDelayed(deleteRepeatRunnable, deleteRepeatInitialDelayMs)
        }

        invalidate()
    }

    private fun handleTouchUp() {
        shiftLongPressRunnable?.let { removeCallbacks(it) }
        stopDeleteRepeat()
        val key = downKey

        if (!shiftLongPressed && key != null && key.code != KEY_DEL) {
            if (key.role == KeyRole.SUGGESTION && key.payload != null) {
                suggestionListener?.invoke(key.payload)
            } else {
                listener?.invoke(key.code)
            }
        }

        downKey = null
        shiftLongPressRunnable = null
        invalidate()
    }

    private fun stopDeleteRepeat() {
        deleteRepeatRunnable?.let { removeCallbacks(it) }
        deleteRepeatRunnable = null
    }

    private fun findKey(x: Float, y: Float): Key? {
        for (key in keys) {
            if (key.bounds.contains(x, y)) return key
        }
        return null
    }
}
