package uz.ilhomvich.neonkeys

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextPaint
import android.text.TextUtils
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.max

interface KeyboardCallback {
    fun onText(s: String)
    fun onDelete()
    fun onEnter()
    fun onCursor(dir: Int)
    fun onSuggestion(s: String)
    fun onPicker()
    fun onLangChanged()
}

private class KR(val key: Key, val r: RectF)

private class PS(val downX: Float, val downY: Float) {
    var kr: KR? = null
    var area = 0            // 0 = tugma, 1 = yuqori panel, 2 = emoji grid
    var popup: List<String>? = null
    var sel = 0
    var longFired = false
    var spaceMoved = false
    var dragged = false
    var lastX = downX
    var lastY = downY
    var lp: Runnable? = null
    var repeat: Runnable? = null
}

private class Flash(val r: RectF, val color: Int, val t: Long)

class KeyboardView(ctx: Context) : View(ctx) {
    var callback: KeyboardCallback? = null

    private val prefs = Prefs(ctx)
    private val dens = resources.displayMetrics.density
    private fun dp(v: Float) = v * dens
    private val handler = Handler(Looper.getMainLooper())
    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // ---- holat ----
    var mode: String = "abc"
        set(v) { field = v; emojiScroll = 0f; layoutKeys(); invalidate() }
    var shift = 0                      // 0 o'chiq, 1 bir marta, 2 caps lock
    var enterLabel: String? = null
        set(v) { field = v; invalidate() }
    var suggestions: List<String> = emptyList()
        set(v) { field = v; disp = if (v.size == 3) listOf(v[1], v[0], v[2]) else v; invalidate() }
    private var disp: List<String> = emptyList()

    private var glowF = 0.7f
    private var animOn = true
    private var kh = 50f * dens
    private var numRow = true
    private var lang = 0
    private var phase = 0f
    private var animator: ValueAnimator? = null
    private var emojiCat = 0
    private var emojiScroll = 0f
    private var lastShiftTap = 0L

    // ---- chizish asboblari ----
    private val bg = 0xFF05050A.toInt()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val ip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val barP = Paint()
    private val path = Path()
    private val hsv = FloatArray(3)
    private val tmp = RectF()
    private var shader: LinearGradient? = null
    private val shM = Matrix()

    private val krs = ArrayList<KR>()
    private val pressedKr = HashSet<KR>()
    private val ptrs = SparseArray<PS>()
    private val flashes = ArrayList<Flash>()

    // ---- o'lchamlar ----
    private fun stripH() = dp(42f)
    private fun areaH() = kh * (if (numRow) 5 else 4)
    private fun totalH() = stripH() + dp(4f) + areaH() + dp(8f)
    private fun gridTop() = stripH() + dp(4f)
    private fun gridBottom() = gridTop() + areaH() - kh

    fun applyPrefs() {
        glowF = prefs.glow / 100f
        animOn = prefs.animate
        kh = dp(40f + prefs.keyHeight * 0.2f)
        numRow = prefs.numberRow
        lang = prefs.lang
        updateAnimator()
        requestLayout()
        layoutKeys()
        invalidate()
    }

    fun setAutoShift(on: Boolean) {
        if (shift == 2) return
        val n = if (on) 1 else 0
        if (n != shift) { shift = n; invalidate() }
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        setMeasuredDimension(View.MeasureSpec.getSize(wSpec), totalH().toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val cols = IntArray(9) { i -> Color.HSVToColor(floatArrayOf((i * 45f) % 360f, 0.9f, 1f)) }
        val s = LinearGradient(0f, 0f, w.toFloat().coerceAtLeast(1f), 0f, cols, null, Shader.TileMode.REPEAT)
        shader = s
        barP.shader = s
        layoutKeys()
    }

    private fun layoutKeys() {
        krs.clear()
        val w = width.toFloat()
        if (w <= 0f) return
        val padX = dp(3f)
        val top = gridTop()
        if (mode == "emoji") {
            place(listOf(Layouts.emojiBottom()), top + areaH() - kh, kh, padX, w)
        } else {
            val rows = Layouts.build(mode, lang, numRow)
            place(rows, top, areaH() / rows.size, padX, w)
        }
    }

    private fun place(rs: List<List<Key>>, top: Float, rh: Float, padX: Float, w: Float) {
        for ((ri, row) in rs.withIndex()) {
            var sum = 0f
            for (k in row) sum += k.w
            val unit = (w - 2 * padX) / sum
            var x = padX
            for (k in row) {
                val kw = k.w * unit
                if (k.type != KT.SPACER) krs.add(KR(k, RectF(x, top + ri * rh, x + kw, top + (ri + 1) * rh)))
                x += kw
            }
        }
    }

    // ---- animatsiya ----
    private fun updateAnimator() {
        val should = animOn && isAttachedToWindow && windowVisibility == View.VISIBLE
        if (should) {
            val dur = (14000 - prefs.speed * 115).toLong().coerceAtLeast(1500L)
            val a = animator
            if (a != null && a.isRunning && a.duration == dur) return
            a?.cancel()
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = dur
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { phase = it.animatedFraction; invalidate() }
                start()
            }
        } else {
            animator?.cancel()
            animator = null
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); applyPrefs() }
    override fun onDetachedFromWindow() { animator?.cancel(); animator = null; cancelAll(); super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != View.VISIBLE) cancelAll()
        updateAnimator()
    }

    // ---- rang yordamchilari ----
    private fun neon(x: Float, y: Float): Int {
        val w = if (width > 0) width else 1
        hsv[0] = (phase * 360f + x / w * 280f + y * 0.2f) % 360f
        hsv[1] = 0.88f
        hsv[2] = 1f
        return Color.HSVToColor(hsv)
    }

    private fun withA(c: Int, a: Int) = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    private fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(),
        (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(),
        (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt()
    )

    private fun baseY(p: Paint, cy: Float): Float {
        val fm = p.fontMetrics
        return cy - (fm.ascent + fm.descent) / 2f
    }

    private fun shiftOn() = shift != 0 && mode == "abc"

    // ---- chizish ----
    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        c.drawColor(bg)
        shader?.let { shM.setTranslate(phase * w, 0f); it.setLocalMatrix(shM) }
        drawStrip(c, w)
        if (mode == "emoji") drawEmojiGrid(c, w)

        pressedKr.clear()
        for (i in 0 until ptrs.size()) ptrs.valueAt(i).kr?.let { pressedKr.add(it) }
        for (kr in krs) drawKey(c, kr)
        drawFlashes(c)
        for (i in 0 until ptrs.size()) drawPopup(c, ptrs.valueAt(i))

        barP.alpha = 255
        c.drawRect(0f, height - dp(2f), w, height.toFloat(), barP)
    }

    private fun drawStrip(c: Canvas, w: Float) {
        val sh = stripH()
        val cy = sh / 2f
        if (mode == "emoji") {
            val tabs = EmojiData.TABS
            val tw = w / tabs.size
            tp.textSize = dp(22f)
            for (i in tabs.indices) {
                val x = tw * i + tw / 2
                tp.color = Color.WHITE
                tp.alpha = if (i == emojiCat) 255 else 110
                c.drawText(tabs[i], x, baseY(tp, cy), tp)
                if (i == emojiCat) {
                    fill.style = Paint.Style.FILL
                    fill.color = neon(x, sh)
                    tmp.set(x - tw * 0.3f, sh - dp(5f), x + tw * 0.3f, sh - dp(3f))
                    c.drawRoundRect(tmp, dp(2f), dp(2f), fill)
                }
            }
            tp.alpha = 255
        } else if (disp.isEmpty()) {
            tp.textSize = dp(13f)
            tp.letterSpacing = 0.35f
            tp.color = Color.WHITE
            tp.shader = shader
            c.drawText("NEON KEYS", w / 2, baseY(tp, cy), tp)
            tp.shader = null
            tp.letterSpacing = 0f
        } else {
            val n = disp.size
            val sw = w / n
            tp.textSize = dp(17f)
            for (i in 0 until n) {
                val best = if (n == 3) i == 1 else i == 0
                tp.color = if (best) Color.WHITE else 0xFFB4B4CC.toInt()
                tp.isFakeBoldText = best
                val t = TextUtils.ellipsize(disp[i], tp, sw - dp(12f), TextUtils.TruncateAt.END).toString()
                c.drawText(t, sw * i + sw / 2, baseY(tp, cy), tp)
                if (i > 0) {
                    fill.style = Paint.Style.FILL
                    fill.color = 0x33FFFFFF
                    c.drawRect(sw * i, dp(10f), sw * i + dp(1f), sh - dp(10f), fill)
                }
            }
            tp.isFakeBoldText = false
        }
        // neon chiziq
        barP.alpha = 255
        c.drawRect(0f, sh - dp(1.5f), w, sh, barP)
        barP.alpha = 60
        c.drawRect(0f, sh - dp(4f), w, sh, barP)
        barP.alpha = 255
    }

    private fun drawKey(c: Canvas, kr: KR) {
        val k = kr.key
        val g = dp(2.5f)
        val r = tmp
        r.set(kr.r.left + g, kr.r.top + g, kr.r.right - g, kr.r.bottom - g)
        val rad = dp(9f)
        val cx = r.centerX()
        val cy = r.centerY()
        val col = neon(cx, cy)
        val pr = pressedKr.contains(kr)
        val gl = glowF
        val boost = if (pr) 2.4f else 1f

        fill.style = Paint.Style.FILL
        fill.color = when {
            pr -> withA(col, 120)
            k.type == KT.ENTER -> withA(col, 70)
            k.type != KT.CHAR -> 0xFF12121C.toInt()
            else -> 0xFF0C0C15.toInt()
        }
        c.drawRoundRect(r, rad, rad, fill)

        stroke.color = col
        if (gl > 0f) {
            stroke.strokeWidth = dp(6f)
            stroke.alpha = (30 * gl * boost).toInt().coerceAtMost(255)
            c.drawRoundRect(r, rad, rad, stroke)
            stroke.strokeWidth = dp(3.2f)
            stroke.alpha = (75 * gl * boost).toInt().coerceAtMost(255)
            c.drawRoundRect(r, rad, rad, stroke)
        }
        stroke.strokeWidth = dp(1.4f)
        stroke.alpha = 255
        c.drawRoundRect(r, rad, rad, stroke)

        val light = blend(col, Color.WHITE, 0.6f)
        when (k.type) {
            KT.CHAR -> {
                val t = if (shiftOn()) k.label.uppercase() else k.label
                tp.textSize = dp(if (t.length > 1) 17f else 22f)
                if (gl > 0f) {
                    tp.style = Paint.Style.FILL_AND_STROKE
                    tp.strokeWidth = dp(3f)
                    tp.color = col
                    tp.alpha = (50 * gl).toInt()
                    c.drawText(t, cx, baseY(tp, cy), tp)
                    tp.style = Paint.Style.FILL
                }
                tp.color = light
                c.drawText(t, cx, baseY(tp, cy), tp)
                if (k.hint.isNotEmpty()) {
                    tp.textSize = dp(9.5f)
                    tp.textAlign = Paint.Align.RIGHT
                    tp.color = withA(col, 170)
                    c.drawText(k.hint, r.right - dp(6f), r.top + dp(12f), tp)
                    tp.textAlign = Paint.Align.CENTER
                }
            }
            KT.SPACE -> {
                tp.textSize = dp(12f)
                tp.letterSpacing = 0.12f
                tp.color = withA(light, 150)
                c.drawText(Layouts.NAMES[lang], cx, baseY(tp, cy), tp)
                tp.letterSpacing = 0f
            }
            KT.GOTO -> {
                tp.textSize = dp(14f)
                tp.color = light
                c.drawText(k.label, cx, baseY(tp, cy), tp)
            }
            KT.ENTER -> {
                val lbl = enterLabel
                if (lbl != null) {
                    tp.textSize = dp(13f)
                    tp.color = light
                    tp.isFakeBoldText = true
                    c.drawText(lbl, cx, baseY(tp, cy), tp)
                    tp.isFakeBoldText = false
                } else icon(c, k, cx, cy, light)
            }
            else -> icon(c, k, cx, cy, light)
        }
    }

    private fun icon(c: Canvas, k: Key, cx: Float, cy: Float, col: Int) {
        val s = dp(8.5f)
        ip.color = col
        ip.strokeWidth = dp(2f)
        ip.style = Paint.Style.STROKE
        when (k.type) {
            KT.SHIFT -> {
                path.reset()
                path.moveTo(cx, cy - s); path.lineTo(cx + s, cy); path.lineTo(cx + s * 0.45f, cy)
                path.lineTo(cx + s * 0.45f, cy + s * 0.8f); path.lineTo(cx - s * 0.45f, cy + s * 0.8f)
                path.lineTo(cx - s * 0.45f, cy); path.lineTo(cx - s, cy); path.close()
                ip.style = if (shift != 0) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
                c.drawPath(path, ip)
                if (shift == 2) {
                    ip.style = Paint.Style.STROKE
                    c.drawLine(cx - s * 0.45f, cy + s * 1.25f, cx + s * 0.45f, cy + s * 1.25f, ip)
                }
            }
            KT.DEL -> {
                path.reset()
                path.moveTo(cx - s, cy); path.lineTo(cx - s * 0.35f, cy - s * 0.75f)
                path.lineTo(cx + s, cy - s * 0.75f); path.lineTo(cx + s, cy + s * 0.75f)
                path.lineTo(cx - s * 0.35f, cy + s * 0.75f); path.close()
                c.drawPath(path, ip)
                c.drawLine(cx + s * 0.05f, cy - s * 0.3f, cx + s * 0.6f, cy + s * 0.3f, ip)
                c.drawLine(cx + s * 0.6f, cy - s * 0.3f, cx + s * 0.05f, cy + s * 0.3f, ip)
            }
            KT.ENTER -> {
                path.reset()
                path.moveTo(cx + s, cy - s * 0.7f); path.lineTo(cx + s, cy + s * 0.2f); path.lineTo(cx - s, cy + s * 0.2f)
                path.moveTo(cx - s * 0.3f, cy - s * 0.4f); path.lineTo(cx - s, cy + s * 0.2f); path.lineTo(cx - s * 0.3f, cy + s * 0.8f)
                c.drawPath(path, ip)
            }
            KT.EMOJI -> {
                c.drawCircle(cx, cy, s, ip)
                ip.style = Paint.Style.FILL
                c.drawCircle(cx - s * 0.38f, cy - s * 0.25f, dp(1.4f), ip)
                c.drawCircle(cx + s * 0.38f, cy - s * 0.25f, dp(1.4f), ip)
                ip.style = Paint.Style.STROKE
                tmp.set(cx - s * 0.55f, cy - s * 0.45f, cx + s * 0.55f, cy + s * 0.55f)
                c.drawArc(tmp, 25f, 130f, false, ip)
            }
            KT.LANG -> {
                c.drawCircle(cx, cy, s, ip)
                tmp.set(cx - s * 0.45f, cy - s, cx + s * 0.45f, cy + s)
                c.drawOval(tmp, ip)
                c.drawLine(cx - s, cy, cx + s, cy, ip)
            }
            else -> {}
        }
    }

    private fun drawEmojiGrid(c: Canvas, w: Float) {
        val list = EmojiData.LISTS[emojiCat]
        val cell = w / 8f
        val top = gridTop()
        val bottom = gridBottom()
        val rowsN = (list.size + 7) / 8
        val maxScroll = max(0f, rowsN * cell - (bottom - top))
        emojiScroll = emojiScroll.coerceIn(0f, maxScroll)
        c.save()
        c.clipRect(0f, top, w, bottom)
        tp.textSize = dp(26f)
        tp.color = Color.WHITE
        tp.alpha = 255
        for (i in list.indices) {
            val y = top + (i / 8) * cell - emojiScroll + cell / 2
            if (y < top - cell || y > bottom + cell) continue
            c.drawText(list[i], (i % 8) * cell + cell / 2, baseY(tp, y), tp)
        }
        c.restore()
    }

    private fun drawFlashes(c: Canvas) {
        if (flashes.isEmpty()) return
        val now = SystemClock.uptimeMillis()
        val it = flashes.iterator()
        while (it.hasNext()) {
            val f = it.next()
            val p = (now - f.t) / 380f
            if (p >= 1f) { it.remove(); continue }
            val e = dp(10f) * p
            tmp.set(f.r.left - e, f.r.top - e, f.r.right + e, f.r.bottom + e)
            stroke.color = f.color
            stroke.strokeWidth = dp(2f)
            stroke.alpha = (200 * (1 - p)).toInt()
            c.drawRoundRect(tmp, dp(9f) + e, dp(9f) + e, stroke)
        }
        if (flashes.isNotEmpty()) postInvalidateOnAnimation()
    }

    private fun popupBox(ps: PS): RectF {
        val kr = ps.kr!!
        val n = ps.popup!!.size
        val w = width.toFloat()
        var cw = max(dp(44f), kr.r.width())
        if (cw * n > w - dp(4f)) cw = (w - dp(4f)) / n
        val tw = cw * n
        val left = (kr.r.centerX() - tw / 2).coerceIn(dp(2f), max(dp(2f), w - tw - dp(2f)))
        val top = max(dp(2f), kr.r.top - kh - dp(6f))
        return RectF(left, top, left + tw, top + kh)
    }

    private fun drawPopup(c: Canvas, ps: PS) {
        val kr = ps.kr ?: return
        val pop = ps.popup
        val rad = dp(10f)
        if (pop != null) {
            val b = popupBox(ps)
            val cw = b.width() / pop.size
            val col = neon(b.centerX(), b.centerY())
            fill.style = Paint.Style.FILL
            fill.color = 0xFF0A0A12.toInt()
            c.drawRoundRect(b, rad, rad, fill)
            stroke.color = col
            stroke.strokeWidth = dp(6f); stroke.alpha = 50
            c.drawRoundRect(b, rad, rad, stroke)
            stroke.strokeWidth = dp(2f); stroke.alpha = 255
            c.drawRoundRect(b, rad, rad, stroke)
            for (i in pop.indices) {
                if (i == ps.sel) {
                    fill.color = withA(col, 130)
                    tmp.set(b.left + i * cw + dp(3f), b.top + dp(3f), b.left + (i + 1) * cw - dp(3f), b.bottom - dp(3f))
                    c.drawRoundRect(tmp, dp(7f), dp(7f), fill)
                }
                tp.textSize = dp(22f)
                tp.color = Color.WHITE
                val t = if (shiftOn()) pop[i].uppercase() else pop[i]
                c.drawText(t, b.left + i * cw + cw / 2, baseY(tp, b.centerY()), tp)
            }
        } else if (ps.area == 0 && kr.key.type == KT.CHAR && !ps.longFired) {
            val w = width.toFloat()
            val bw = kr.r.width() * 1.3f
            val bh = kh * 1.1f
            val left = (kr.r.centerX() - bw / 2).coerceIn(dp(2f), w - bw - dp(2f))
            val top = max(dp(2f), kr.r.top - bh + dp(4f))
            val b = RectF(left, top, left + bw, top + bh)
            val col = neon(b.centerX(), b.centerY())
            fill.style = Paint.Style.FILL
            fill.color = 0xFF0A0A12.toInt()
            c.drawRoundRect(b, rad, rad, fill)
            stroke.color = col
            stroke.strokeWidth = dp(6f); stroke.alpha = 60
            c.drawRoundRect(b, rad, rad, stroke)
            stroke.strokeWidth = dp(2f); stroke.alpha = 255
            c.drawRoundRect(b, rad, rad, stroke)
            tp.textSize = dp(30f)
            tp.color = blend(col, Color.WHITE, 0.6f)
            val t = if (shiftOn()) kr.key.label.uppercase() else kr.key.label
            c.drawText(t, b.centerX(), baseY(tp, b.centerY()), tp)
        }
    }

    // ---- teginish ----
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelAll()
                down(e.getPointerId(0), e.getX(0), e.getY(0))
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                down(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) move(e.getPointerId(i), e.getX(i), e.getY(i))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                up(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_CANCEL -> cancelAll()
        }
        return true
    }

    private fun hit(x: Float, y: Float): KR? {
        var best: KR? = null
        var bd = Float.MAX_VALUE
        for (kr in krs) {
            val dx = max(max(kr.r.left - x, x - kr.r.right), 0f)
            val dy = max(max(kr.r.top - y, y - kr.r.bottom), 0f)
            val dd = dx * dx + dy * dy
            if (dd < bd) { bd = dd; best = kr; if (dd == 0f) break }
        }
        return if (bd < dp(24f) * dp(24f)) best else null
    }

    private fun stopCb(ps: PS) {
        ps.lp?.let { handler.removeCallbacks(it) }
        ps.repeat?.let { handler.removeCallbacks(it) }
        ps.lp = null
        ps.repeat = null
    }

    private fun cancelAll() {
        for (i in 0 until ptrs.size()) stopCb(ptrs.valueAt(i))
        ptrs.clear()
        invalidate()
    }

    private fun down(id: Int, x: Float, y: Float) {
        val ps = PS(x, y)
        ptrs.put(id, ps)
        if (y < stripH()) { ps.area = 1; invalidate(); return }
        if (mode == "emoji" && y < gridBottom()) { ps.area = 2; invalidate(); return }
        val kr = hit(x, y) ?: return
        ps.kr = kr
        feedback(kr.key.type)
        when (kr.key.type) {
            KT.DEL -> {
                callback?.onDelete()
                var count = 0
                val r = object : Runnable {
                    override fun run() {
                        callback?.onDelete()
                        count++
                        haptic(HapticFeedbackConstants.KEYBOARD_TAP)
                        handler.postDelayed(this, if (count > 8) 35L else 70L)
                    }
                }
                ps.repeat = r
                handler.postDelayed(r, 380L)
            }
            else -> scheduleLong(ps)
        }
        invalidate()
    }

    private fun scheduleLong(ps: PS) {
        val k = ps.kr?.key ?: return
        if (!(k.type == KT.CHAR && k.alts.isNotEmpty()) && k.type != KT.LANG) return
        val r = Runnable {
            val cur = ps.kr ?: return@Runnable
            if (cur.key.type == KT.LANG) {
                ps.longFired = true
                callback?.onPicker()
            } else {
                ps.popup = cur.key.alts
                ps.sel = 0
                ps.longFired = true
                haptic(HapticFeedbackConstants.LONG_PRESS)
            }
            invalidate()
        }
        ps.lp = r
        handler.postDelayed(r, 360L)
    }

    private fun move(id: Int, x: Float, y: Float) {
        val ps = ptrs.get(id) ?: return
        if (ps.area == 2) {
            if (abs(y - ps.downY) > dp(8f)) ps.dragged = true
            if (ps.dragged) { emojiScroll -= (y - ps.lastY); invalidate() }
            ps.lastY = y
            return
        }
        val kr = ps.kr ?: return
        val pop = ps.popup
        if (pop != null) {
            val b = popupBox(ps)
            val s = ((x - b.left) / (b.width() / pop.size)).toInt().coerceIn(0, pop.size - 1)
            if (s != ps.sel) { ps.sel = s; invalidate() }
            return
        }
        when (kr.key.type) {
            KT.SPACE -> {
                val step = dp(13f)
                var dx = x - ps.lastX
                while (abs(dx) >= step) {
                    val dir = if (dx > 0) 1 else -1
                    callback?.onCursor(dir)
                    ps.spaceMoved = true
                    ps.lastX += dir * step
                    dx = x - ps.lastX
                }
            }
            KT.CHAR -> {
                val nk = hit(x, y)
                if (nk != null && nk !== kr && nk.key.type == KT.CHAR) {
                    stopCb(ps)
                    ps.kr = nk
                    scheduleLong(ps)
                    invalidate()
                }
            }
            else -> {}
        }
    }

    private fun up(id: Int, x: Float, y: Float) {
        val ps = ptrs.get(id) ?: return
        ptrs.delete(id)
        stopCb(ps)
        when (ps.area) {
            1 -> if (y < stripH() && abs(x - ps.downX) < dp(30f)) stripTap(x)
            2 -> if (!ps.dragged) gridTap(x, y)
            else -> {
                val kr = ps.kr
                if (kr != null) {
                    val k = kr.key
                    val pop = ps.popup
                    if (pop != null) commitChar(pop[ps.sel])
                    else if (!ps.longFired) when (k.type) {
                        KT.CHAR -> commitChar(k.label)
                        KT.SHIFT -> toggleShift()
                        KT.SPACE -> if (!ps.spaceMoved) callback?.onText(" ")
                        KT.ENTER -> callback?.onEnter()
                        KT.GOTO -> mode = k.data
                        KT.LANG -> cycleLang()
                        KT.EMOJI -> mode = "emoji"
                        else -> {}
                    }
                    val g = dp(2.5f)
                    val fr = RectF(kr.r.left + g, kr.r.top + g, kr.r.right - g, kr.r.bottom - g)
                    flashes.add(Flash(fr, neon(fr.centerX(), fr.centerY()), SystemClock.uptimeMillis()))
                }
            }
        }
        invalidate()
    }

    private fun stripTap(x: Float) {
        if (mode == "emoji") {
            val n = EmojiData.TABS.size
            emojiCat = (x / (width.toFloat() / n)).toInt().coerceIn(0, n - 1)
            emojiScroll = 0f
        } else if (disp.isNotEmpty()) {
            val i = (x / (width.toFloat() / disp.size)).toInt().coerceIn(0, disp.size - 1)
            haptic(HapticFeedbackConstants.KEYBOARD_TAP)
            callback?.onSuggestion(disp[i])
        }
    }

    private fun gridTap(x: Float, y: Float) {
        if (y < gridTop() || y > gridBottom()) return
        val list = EmojiData.LISTS[emojiCat]
        val cell = width / 8f
        val col = (x / cell).toInt().coerceIn(0, 7)
        val row = ((y - gridTop() + emojiScroll) / cell).toInt()
        val idx = row * 8 + col
        if (idx in list.indices) {
            feedback(KT.CHAR)
            callback?.onText(list[idx])
        }
    }

    private fun commitChar(label: String) {
        val sh = shiftOn()
        if (sh && shift == 1) shift = 0
        callback?.onText(if (sh) label.uppercase() else label)
    }

    private fun toggleShift() {
        val now = SystemClock.uptimeMillis()
        shift = when {
            shift == 2 -> 0
            shift == 1 && now - lastShiftTap < 320 -> 2
            shift == 1 -> 0
            else -> 1
        }
        lastShiftTap = now
    }

    private fun cycleLang() {
        lang = (lang + 1) % Layouts.NAMES.size
        prefs.lang = lang
        layoutKeys()
        callback?.onLangChanged()
    }

    // ---- fikr-mulohaza ----
    @Suppress("DEPRECATION")
    private fun haptic(type: Int) {
        if (prefs.haptic) performHapticFeedback(type, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
    }

    private fun feedback(t: KT) {
        haptic(HapticFeedbackConstants.KEYBOARD_TAP)
        if (prefs.sound) {
            val fx = when (t) {
                KT.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                KT.DEL -> AudioManager.FX_KEYPRESS_DELETE
                KT.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            audio.playSoundEffect(fx, 0.6f)
        }
    }
}
