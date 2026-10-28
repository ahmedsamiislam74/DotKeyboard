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

interface KeyListener {
    fun onText(s: String)
    fun onDelete()
    fun onEnter()
    fun onEmoji()
}

enum class KType { CHAR, SHIFT, DEL, SYM, EMOJI, SPACE, ENTER, SPACER }

class Key(
    val type: KType,
    val label: String = "",
    val alt: String? = null,
    val color: Int = 0,
    val weight: Float = 1f
) {
    val rect = RectF()
}

private val YEL = Color.parseColor("#E3B72F")
private val BLU = Color.parseColor("#2A5290")
private val BLK = Color.parseColor("#1C1C1E")
private val WHT = Color.parseColor("#EAE6E1")
private val RED = Color.parseColor("#C23535")

class KeyboardView(context: Context, private val listener: KeyListener) : View(context) {

    private val dp = resources.displayMetrics.density
    private val gap = 5 * dp
    private val padX = 4 * dp
    private val padY = 8 * dp
    private val rowH = 56 * dp

    private val handler = Handler(Looper.getMainLooper())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private var symbols = false
    private var shiftState = 0 // 0 off, 1 once, 2 caps lock
    private var lastShiftTap = 0L

    private var rows: List<List<Key>> = emptyList()
    private val active = HashMap<Int, Key>()
    private val timers = HashMap<Int, Runnable>()
    private val longFired = HashSet<Int>()

    init { rows = buildRows() }

    // ---------- layouts ----------
    private fun k(c: String, alt: String?, col: Int) = Key(KType.CHAR, c, alt, col)
    private fun sp() = Key(KType.SPACER)

    private fun buildRows(): List<List<Key>> {
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

    fun reset() {
        shiftState = 0
        if (symbols) { symbols = false; rows = buildRows(); layoutKeys() }
        invalidate()
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (4 * rowH + 3 * gap + 2 * padY).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { layoutKeys() }

    // ---------- drawing ----------
    override fun onDraw(c: Canvas) {
        c.drawColor(Color.BLACK)
        for (row in rows) for (key in row) {
            if (key.type == KType.SPACER) continue
            val r = RectF(key.rect)
            if (active.containsValue(key)) r.inset(3 * dp, 3 * dp)
            var bg = key.color
            if (key.type == KType.SHIFT && shiftState == 1) bg = BLK
            paint.style = Paint.Style.FILL
            paint.color = bg
            c.drawRoundRect(r, 12 * dp, 12 * dp, paint)
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
            KType.SYM -> drawText(c, key.label, cx, cy, fg, 17 * dp)
            KType.EMOJI -> drawText(c, "\u263A", cx, cy, fg, 28 * dp)
            KType.ENTER -> drawText(c, "\u21B5", cx, cy, fg, 26 * dp)
            else -> {}
        }
    }

    private fun drawText(c: Canvas, s: String, cx: Float, cy: Float, fg: Int, size: Float) {
        textPaint.color = fg
        textPaint.textSize = size
        val fm = textPaint.fontMetrics
        c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2, textPaint)
    }

    private fun drawGlyph(c: Canvas, label: String, cx: Float, cy: Float, fg: Int) {
        val glyph = if (label.length == 1) DotFont.map[label[0]] else null
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
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                press(e.getPointerId(idx), e.getX(idx), e.getY(idx))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP ->
                release(e.getPointerId(idx))
            MotionEvent.ACTION_CANCEL -> {
                for (id in active.keys.toList()) { timers.remove(id)?.let { handler.removeCallbacks(it) } }
                active.clear(); longFired.clear(); invalidate()
            }
        }
        return true
    }

    private fun keyAt(x: Float, y: Float): Key? {
        for (row in rows) for (key in row) {
            if (key.type != KType.SPACER && key.rect.contains(x, y)) return key
        }
        // nearest key within a tolerance so small gaps don't swallow taps
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
            KType.SYM -> { symbols = !symbols; rows = buildRows(); layoutKeys() }
            KType.EMOJI -> listener.onEmoji()
            KType.ENTER -> listener.onEnter()
            else -> {}
        }
    }
}
