package com.example.dotkeyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

interface KeyListener {
    fun onText(s: String)
    fun onDelete()
    fun onEnter()
    fun onEmoji()
}

enum class KType { CHAR, SHIFT, DEL, SYM, EMOJI, EMOJI_ABC, TAB, SPACE, ENTER, SPACER }

class Key(
    val type: KType,
    val label: String = "",
    val alt: String? = null,
    val color: Int = 0,
    val weight: Float = 1f,
    val tag: Int = 0
) {
    val rect = RectF()
}

// ======== EASY TO CHANGE: colors and shape ========
private val YEL = Color.parseColor("#E3B72F")
private val BLU = Color.parseColor("#2A5290")
private val BLK = Color.parseColor("#1C1C1E")
private val WHT = Color.parseColor("#EAE6E1")
private val RED = Color.parseColor("#C23535")
private val BG = Color.BLACK          // keyboard background
private const val CORNER_DP = 12f     // key corner roundness (0 = square, 28 = pill)
// ===================================================

private const val COLS = 8

class KeyboardView(context: Context, private val listener: KeyListener) : View(context) {

    private val dp = resources.displayMetrics.density
    private val gap = 5 * dp
    private val padX = 4 * dp
    private val padY = 8 * dp
    private val rowH = 56 * dp
    private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private val handler = Handler(Looper.getMainLooper())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    private var symbols = false
    private var shiftState = 0 // 0 off, 1 once, 2 caps lock
    private var lastShiftTap = 0L

    private var emojiMode = false
    private var emojiCat = 0
    private var scrollY = 0f
    private var gridPointer = -1
    private var gx0 = 0f
    private var gy0 = 0f
    private var gScroll0 = 0f
    private var gMoved = false

    private var rows: List<List<Key>> = emptyList()
    private val active = HashMap<Int, Key>()
    private val timers = HashMap<Int, Runnable>()
    private val longFired = HashSet<Int>()

    init { rows = buildRows() }

    // ---------- layouts ----------
    private fun k(c: String, alt: String?, col: Int) = Key(KType.CHAR, c, alt, col)
    private fun sp() = Key(KType.SPACER)

    private fun buildRows(): List<List<Key>> {
        if (emojiMode) {
            val tabColors = listOf(YEL, BLU, WHT, RED, YEL, BLU, WHT)
            val tabs = EmojiData.tabs.mapIndexed { i, t -> Key(KType.TAB, t, null, tabColors[i], 1f, i) }
            val bottomRow = listOf(Key(KType.EMOJI_ABC, "ABC", null, RED, 1.5f)) +
                tabs + Key(KType.DEL, "", null, WHT, 1.5f)
            return listOf(emptyList(), emptyList(), emptyList(), bottomRow)
        }
        val bottom = listOf(
            Key(KType.SYM, if (symbols) "ABC" else "?123", null, RED, 1.5f),
            Key(KType.EMOJI, "", null, BLU, 1f),
            Key(KType.CHAR, ",", null, WHT, 1f),
            Key(KType.SPACE, "", null, BLK, 4f),
            Key(KType.CHAR, ".", ",", BLK, 1f),
            Key(KType.ENTER, "", null, RED, 1.5f)
        )
        return if (!symbols) listOf(
            listOf(k("q","1",YEL), k("w","2",BLU), k("e","3",BLK), k("r","4",WHT), k("t","5",RED),
                   k("y","6",BLU), k("u","7",WHT), k("i","8",RED), k("o","9",YEL), k("p","0",BLK)),
            listOf(sp(), k("a","@",WHT), k("s","#",RED), k("d","&",BLU), k("f","*",BLK), k("g","-",YEL),
                   k("h","!",WHT), k("j","?",RED), k("k","(",BLU), k("l",")",BLK)),
            listOf(Key(KType.SHIFT, "", null, YEL, 1.5f), k("z","\"",BLK), k("x",null,YEL), k("c","ç",WHT),
                   k("v",null,RED), k("b","'",BLU), k("n",":",BLK), k("m","/",YEL),
                   Key(KType.DEL, "", null, WHT, 1.5f)),
            bottom
        ) else listOf(
            listOf(k("1",null,YEL), k("2",null,BLU), k("3",null,BLK), k("4",null,WHT), k("5",null,RED),
                   k("6",null,BLU), k("7",null,WHT), k("8",null,RED), k("9",null,YEL), k("0",null,BLK)),
            listOf(sp(), k("@",null,WHT), k("#",null,RED), k("$",null,BLU), k("%",null,BLK), k("&",null,YEL),
                   k("-",null,WHT), k("+",null,RED), k("(",null,BLU), k(")",null,BLK)),
            listOf(Key(KType.CHAR, "=", null, YEL, 1.5f), k("*",null,BLK), k("\"",null,YEL), k("'",null,WHT),
                   k(":",null,RED), k(";",null,BLU), k("!",null,BLK), k("?",null,YEL),
                   Key(KType.DEL, "", null, WHT, 1.5f)),
            bottom
        )
    }

    private fun layoutKeys() {
        val u = (width - 2 * padX - 9 * gap) / 10f
        var y = padY
        for (row in rows) {
            var x = padX
            for (key in row) {
                if (key.type == KType.SPACER) { x += 0.5f * (u + gap); continue }
                val w = key.weight * u + (key.weight - 1) * gap
                key.rect.set(x, y, x + w, y + rowH)
                x += w + gap
            }
            y += rowH + gap
        }
    }

    private fun relayout() { rows = buildRows(); layoutKeys() }

    fun reset() {
        shiftState = 0
        if (symbols || emojiMode) { symbols = false; emojiMode = false; relayout() }
        invalidate()
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (4 * rowH + 3 * gap + 2 * padY).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { layoutKeys() }

    // ---------- emoji grid helpers ----------
    private fun gridBottom() = padY + 3 * rowH + 2 * gap
    private fun cellW() = (width - 2 * padX) / COLS
    private fun cellH() = rowH * 0.95f
    private fun maxScroll(): Float {
        val n = EmojiData.sets[emojiCat].size
        val rowsN = Math.ceil(n / COLS.toDouble()).toFloat()
        return maxOf(0f, rowsN * cellH() - (gridBottom() - padY))
    }

    private fun drawEmojiGrid(c: Canvas) {
        val list = EmojiData.sets[emojiCat]
        val cw = cellW()
        val ch = cellH()
        val top = padY
        val bottom = gridBottom()
        c.save()
        c.clipRect(padX, top, width - padX, bottom)
        emojiPaint.textSize = 26 * dp
        val fm = emojiPaint.fontMetrics
        paint.style = Paint.Style.FILL
        paint.color = BLK
        list.forEachIndexed { i, e ->
            val cx = padX + (i % COLS + 0.5f) * cw
            val cy = top + (i / COLS) * ch + ch / 2 - scrollY
            if (cy + ch < top || cy - ch > bottom) return@forEachIndexed
            c.drawRoundRect(cx - cw / 2 + 2 * dp, cy - ch / 2 + 2 * dp,
                cx + cw / 2 - 2 * dp, cy + ch / 2 - 2 * dp, CORNER_DP * dp * 0.8f, CORNER_DP * dp * 0.8f, paint)
            c.drawText(e, cx, cy - (fm.ascent + fm.descent) / 2, emojiPaint)
        }
        c.restore()
    }

    private fun tapEmoji(x: Float, y: Float) {
        val col = ((x - padX) / cellW()).toInt()
        val row = ((y - padY + scrollY) / cellH()).toInt()
        if (col !in 0 until COLS || row < 0) return
        val i = row * COLS + col
        val list = EmojiData.sets[emojiCat]
        if (i in list.indices) {
            listener.onText(list[i])
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    // ---------- drawing ----------
    override fun onDraw(c: Canvas) {
        c.drawColor(BG)
        if (emojiMode) drawEmojiGrid(c)
        for (row in rows) for (key in row) {
            if (key.type == KType.SPACER) continue
            val r = RectF(key.rect)
            if (active.containsValue(key)) r.inset(3 * dp, 3 * dp)
            var bg = key.color
            if (key.type == KType.SHIFT && shiftState == 1) bg = BLK
            if (key.type == KType.TAB && key.tag != emojiCat) bg = BLK
            paint.style = Paint.Style.FILL
            paint.color = bg
            c.drawRoundRect(r, CORNER_DP * dp, CORNER_DP * dp, paint)
            var fg = if (bg == YEL || bg == WHT) Color.BLACK else Color.WHITE
            if (key.type == KType.SHIFT && shiftState == 1) fg = YEL
            drawContent(c, key, r, fg)
            if (key.alt != null) {
                textPaint.color = fg
                textPaint.alpha = 160
                textPaint.textSize = 11 * dp
                textPaint.textAlign = Paint.Align.RIGHT
                c.drawText(key.alt, r.right - 7 * dp, r.top + 15 * dp, textPaint)
                textPaint.textAlign = Paint.Align.CENTER
                textPaint.alpha = 255
            }
        }
    }

    private fun drawContent(c: Canvas, key: Key, r: RectF, fg: Int) {
        val cx = r.centerX()
        val cy = r.centerY()
        when (key.type) {
            KType.CHAR -> drawGlyph(c, key.label, cx, cy, fg)
            KType.SHIFT -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.5f * dp
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = fg
                val s = 9 * dp
                c.drawLine(cx - s, cy + s / 2, cx, cy - s / 2, paint)
                c.drawLine(cx, cy - s / 2, cx + s, cy + s / 2, paint)
                if (shiftState == 2) c.drawLine(cx - s, cy + s, cx + s, cy + s, paint)
                paint.style = Paint.Style.FILL
            }
            KType.DEL -> drawText(c, "\u232B", cx, cy, fg, 22 * dp)
            KType.SYM, KType.EMOJI_ABC -> drawText(c, key.label, cx, cy, fg, 17 * dp)
            KType.EMOJI -> drawDotSmiley(c, cx, cy, fg)
            KType.ENTER -> drawText(c, "\u21B5", cx, cy, fg, 26 * dp)
            KType.TAB -> {
                emojiPaint.textSize = 22 * dp
                val fm = emojiPaint.fontMetrics
                c.drawText(key.label, cx, cy - (fm.ascent + fm.descent) / 2, emojiPaint)
            }
            else -> {}
        }
    }

    private fun drawDotSmiley(c: Canvas, cx: Float, cy: Float, fg: Int) {
        paint.style = Paint.Style.FILL
        paint.color = fg
        val dotR = 1.7f * dp
        val ring = 14 * dp
        for (i in 0 until 16) {
            val a = 2 * Math.PI * i / 16
            c.drawCircle(cx + (ring * Math.cos(a)).toFloat(), cy + (ring * Math.sin(a)).toFloat(), dotR, paint)
        }
        c.drawCircle(cx - 5 * dp, cy - 4 * dp, dotR * 1.1f, paint)
        c.drawCircle(cx + 5 * dp, cy - 4 * dp, dotR * 1.1f, paint)
        for (i in 0..4) {
            val a = Math.PI * (0.25 + 0.5 * i / 4)
            c.drawCircle(cx + (8 * dp * Math.cos(a)).toFloat(), cy - 1 * dp + (8 * dp * Math.sin(a)).toFloat(), dotR, paint)
        }
    }

    private fun drawText(c: Canvas, s: String, cx: Float, cy: Float, fg: Int, size: Float) {
        textPaint.color = fg
        textPaint.textSize = size
        val fm = textPaint.fontMetrics
        c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2, textPaint)
    }

    private fun drawGlyph(c: Canvas, label: String, cx: Float, cy: Float, fg: Int) {
        val ch0: Char? = if (label.length == 1) label[0] else null
        val upper = shiftState != 0 && ch0 != null && ch0.isLetter()
        val glyph = if (ch0 == null) null else DotFont.map[if (upper) ch0.uppercaseChar() else ch0]
        if (glyph == null) { drawText(c, label, cx, cy, fg, 20 * dp); return }
        val step = rowH * 0.075f
        val x0 = cx - 2.5f * step
        val y0 = cy - 4.5f * step
        paint.style = Paint.Style.FILL
        paint.color = fg
        glyph.second.forEachIndexed { i, row ->
            row.forEachIndexed { col, ch ->
                if (ch == '#') {
                    c.drawCircle(x0 + col * step + step / 2, y0 + (glyph.first + i) * step + step / 2, step * 0.42f, paint)
                }
            }
        }
    }

    // ---------- touch ----------
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val idx = e.actionIndex
        val id = e.getPointerId(idx)

        if (emojiMode) {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> if (e.getY(idx) < gridBottom()) {
                    gridPointer = id
                    gx0 = e.getX(idx); gy0 = e.getY(idx)
                    gScroll0 = scrollY; gMoved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> if (gridPointer >= 0) {
                    val i = e.findPointerIndex(gridPointer)
                    if (i >= 0) {
                        val dy = e.getY(i) - gy0
                        if (Math.abs(dy) > slop) gMoved = true
                        if (gMoved) { scrollY = (gScroll0 - dy).coerceIn(0f, maxScroll()); invalidate() }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> if (gridPointer == id) {
                    if (!gMoved) tapEmoji(e.getX(idx), e.getY(idx))
                    gridPointer = -1
                    return true
                }
                MotionEvent.ACTION_POINTER_UP -> if (gridPointer == id) { gridPointer = -1; return true }
                MotionEvent.ACTION_CANCEL -> gridPointer = -1
            }
        }

        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                press(id, e.getX(idx), e.getY(idx))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP ->
                release(id)
            MotionEvent.ACTION_CANCEL -> {
                for (i in active.keys.toList()) { timers.remove(i)?.let { handler.removeCallbacks(it) } }
                active.clear(); longFired.clear(); invalidate()
            }
        }
        return true
    }

    private fun keyAt(x: Float, y: Float): Key? {
        for (row in rows) for (key in row) {
            if (key.type != KType.SPACER && key.rect.contains(x, y)) return key
        }
        var best: Key? = null
        var bestD = Float.MAX_VALUE
        for (row in rows) for (key in row) {
            if (key.type == KType.SPACER) continue
            val dx = maxOf(key.rect.left - x, 0f, x - key.rect.right)
            val dy = maxOf(key.rect.top - y, 0f, y - key.rect.bottom)
            val d = dx * dx + dy * dy
            if (d < bestD) { bestD = d; best = key }
        }
        return if (bestD < (gap * 2) * (gap * 2)) best else null
    }

    private fun press(id: Int, x: Float, y: Float) {
        val key = keyAt(x, y) ?: return
        active[id] = key
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (key.type == KType.DEL) {
            listener.onDelete()
            longFired.add(id)
            val r = object : Runnable {
                override fun run() {
                    listener.onDelete()
                    handler.postDelayed(this, 50)
                }
            }
            timers[id] = r
            handler.postDelayed(r, 400)
        } else if (key.alt != null) {
            val r = Runnable {
                longFired.add(id)
                listener.onText(key.alt)
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                invalidate()
            }
            timers[id] = r
            handler.postDelayed(r, 350)
        }
        invalidate()
    }

    private fun release(id: Int) {
        val key = active.remove(id) ?: return
        timers.remove(id)?.let { handler.removeCallbacks(it) }
        if (!longFired.remove(id)) fire(key)
        invalidate()
    }

    private fun fire(key: Key) {
        when (key.type) {
            KType.CHAR -> {
                val isLetter = key.label.length == 1 && key.label[0].isLetter()
                listener.onText(if (shiftState != 0 && isLetter) key.label.uppercase() else key.label)
                if (shiftState == 1) shiftState = 0
            }
            KType.SPACE -> listener.onText(" ")
            KType.SHIFT -> {
                val now = SystemClock.uptimeMillis()
                shiftState = when {
                    shiftState == 1 && now - lastShiftTap < 350 -> 2
                    shiftState == 0 -> 1
                    else -> 0
                }
                lastShiftTap = now
            }
            KType.SYM -> { symbols = !symbols; relayout() }
            KType.EMOJI -> { emojiMode = true; scrollY = 0f; relayout() }
            KType.EMOJI_ABC -> { emojiMode = false; relayout() }
            KType.TAB -> { emojiCat = key.tag; scrollY = 0f }
            KType.ENTER -> listener.onEnter()
            else -> {}
        }
    }
}
