package uk.dx.propwatch

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.ln
import kotlin.random.Random

/**
 * The 1940s portable-receiver look: black leatherette case, chrome louvre grille,
 * brass trim, and a backlit cream slide-rule dial with a red needle.
 */
object Radio {
    val LEATHER = Color.rgb(0x1C, 0x17, 0x14)
    val PANEL = Color.rgb(0x26, 0x20, 0x1B)
    val BRASS = Color.rgb(0xC9, 0xA4, 0x5C)
    val BRASS_DARK = Color.rgb(0x8A, 0x6A, 0x2E)
    val CREAM = Color.rgb(0xF1, 0xE6, 0xC8)
    val INK = Color.rgb(0x3B, 0x2A, 0x1A)

    // Pilot-lamp colours. Green is the "magic eye" tuning-indicator glow.
    val LAMP_ALERT = Color.rgb(0x7C, 0xFF, 0x9B)
    val LAMP_HEADSUP = Color.rgb(0xFF, 0xB3, 0x47)
    val LAMP_ERROR = Color.rgb(0xFF, 0x5A, 0x4A)
    val LAMP_OFF = Color.rgb(0x6B, 0x5E, 0x4F)

    val SERIF: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    val MONO: Typeface = Typeface.MONOSPACE

    fun dp(ctx: Context, v: Float) = v * ctx.resources.displayMetrics.density

    /** Tiled, slightly mottled leatherette. */
    fun leatherette(ctx: Context): BitmapDrawable {
        val n = 96
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val r = Random(42)
        for (y in 0 until n) for (x in 0 until n) {
            var v = r.nextInt(-7, 8)
            if (r.nextInt(14) == 0) v -= 9   // pores
            bmp.setPixel(x, y, Color.rgb(
                (0x1C + v).coerceIn(0, 255), (0x17 + v).coerceIn(0, 255), (0x14 + v).coerceIn(0, 255)))
        }
        return BitmapDrawable(ctx.resources, bmp).apply { setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
    }

    /** Dark bakelite push-button with a brass rim; darkens when pressed. */
    fun buttonBg(ctx: Context, big: Boolean = true): StateListDrawable {
        val radius = dp(ctx, 6f)
        val stroke = dp(ctx, if (big) 2f else 1f).toInt()
        fun g(top: Int, bottom: Int, rim: Int) = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)
        ).apply { cornerRadius = radius; setStroke(stroke, rim) }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed),
                g(Color.rgb(0x14, 0x10, 0x0D), Color.rgb(0x2A, 0x22, 0x1C), CREAM))
            addState(intArrayOf(-android.R.attr.state_enabled),
                g(Color.rgb(0x22, 0x1C, 0x18), Color.rgb(0x1A, 0x15, 0x12), BRASS_DARK))
            addState(intArrayOf(),
                g(Color.rgb(0x3A, 0x30, 0x2A), Color.rgb(0x1E, 0x19, 0x15), if (big) BRASS else BRASS_DARK))
        }
    }

    fun panelBg(ctx: Context, rim: Int = BRASS_DARK, radius: Float = 10f) = GradientDrawable().apply {
        setColor(PANEL)
        cornerRadius = dp(ctx, radius)
        setStroke(dp(ctx, 1.5f).toInt(), rim)
    }

    fun switchTints() = Pair(
        ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(BRASS, LAMP_OFF)),
        ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(Color.argb(0x88, 0xC9, 0xA4, 0x5C), Color.argb(0x66, 0x6B, 0x5E, 0x4F))),
    )
}

/** Chrome louvre grille with a brass name badge. */
class GrilleView(ctx: Context) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x12, 0x0E, 0x0B) }
    private val bar = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badge = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgeText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Radio.INK; typeface = Radio.SERIF; textSize = 11 * d; letterSpacing = 0.25f
        textAlign = Paint.Align.CENTER
    }
    private val bars = mutableListOf<Pair<RectF, LinearGradient>>()

    override fun onMeasure(w: Int, h: Int) =
        setMeasuredDimension(MeasureSpec.getSize(w), (60 * d).toInt())

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        bars.clear()
        val step = 7 * d
        val bh = 3.6f * d
        var y = 6 * d
        while (y + bh < h - 5 * d) {
            bars += RectF(10 * d, y, w - 10 * d, y + bh) to LinearGradient(0f, y, 0f, y + bh,
                intArrayOf(Color.rgb(0xF2, 0xF2, 0xF2), Color.rgb(0x8C, 0x8C, 0x8C), Color.rgb(0x55, 0x55, 0x55)),
                null, Shader.TileMode.CLAMP)
            y += step
        }
        badge.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
            intArrayOf(Color.rgb(0xE6, 0xC8, 0x82), Radio.BRASS_DARK, Color.rgb(0xD4, 0xB0, 0x6A)), null, Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.drawRoundRect(0f, 0f, w, h, 8 * d, 8 * d, bg)
        for ((r, g) in bars) { bar.shader = g; c.drawRoundRect(r, r.height() / 2, r.height() / 2, bar) }
        val label = "HF · 6m · 2m · 70cm"
        val bw = badgeText.measureText(label) + 28 * d
        val bt = h / 2 - 11 * d
        c.drawRoundRect(w / 2 - bw / 2, bt, w / 2 + bw / 2, bt + 22 * d, 4 * d, 4 * d, badge)
        c.drawText(label, w / 2, bt + 15 * d, badgeText)
    }
}

/**
 * Backlit slide-rule dial. Decorative band scales (HF to 6m, 2m, 70cm) and a
 * condition scale the needle points at: QUIET, HEADS-UP or OPEN.
 */
class DialView(ctx: Context) : View(ctx) {
    private companion object {
        val HF_BANDS = listOf("160" to 1.85, "80" to 3.6, "40" to 7.1, "20" to 14.15, "15" to 21.2, "10" to 28.5, "6" to 50.2)
    }
    private val d = resources.displayMetrics.density
    private var needle = 1f      // 0..1 across the scale
    private var glow = 1f        // 0 = cold, 1 = fully warmed up
    private val anims = mutableListOf<ValueAnimator>()

    private val bezel = Paint(Paint.ANTI_ALIAS_FLAG)
    private val face = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glare = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Radio.INK; strokeWidth = 1.2f * d }
    private val num = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Radio.INK; typeface = Radio.SERIF; textSize = 10 * d; textAlign = Paint.Align.CENTER
    }
    private val title = Paint(num).apply { textSize = 13 * d; letterSpacing = 0.35f }
    private val small = Paint(num).apply { textSize = 9 * d; textAlign = Paint.Align.LEFT; typeface = Radio.MONO }
    private val cond = Paint(num).apply { textSize = 10.5f * d; letterSpacing = 0.15f }
    private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0xB3, 0x26, 0x1E); strokeWidth = 2.6f * d; strokeCap = Paint.Cap.ROUND
        setShadowLayer(3 * d, 1 * d, 1 * d, Color.argb(0x80, 0, 0, 0))
    }
    private val outer = RectF(); private val inner = RectF()

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }   // needed for the needle's shadow

    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w)
        setMeasuredDimension(width, (width * 0.62f).toInt().coerceAtMost((270 * d).toInt()))
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        outer.set(0f, 0f, w.toFloat(), h.toFloat())
        inner.set(7 * d, 7 * d, w - 7 * d, h - 7 * d)
        bezel.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(Color.rgb(0xE6, 0xC8, 0x82), Radio.BRASS_DARK, Color.rgb(0xD4, 0xB0, 0x6A), Color.rgb(0x6E, 0x52, 0x22)),
            null, Shader.TileMode.CLAMP)
        glare.shader = LinearGradient(0f, inner.top, 0f, inner.top + inner.height() * 0.5f,
            Color.argb(0x50, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
    }

    private fun mix(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt())

    override fun onDraw(c: Canvas) {
        c.drawRoundRect(outer, 18 * d, 18 * d, bezel)
        face.shader = RadialGradient(inner.centerX(), inner.centerY() + inner.height() * 0.15f, inner.width() * 0.62f,
            mix(Color.rgb(0x4A, 0x3F, 0x30), Color.rgb(0xF6, 0xE6, 0xB8), glow),
            mix(Color.rgb(0x26, 0x20, 0x18), Color.rgb(0xB8, 0x98, 0x5E), glow), Shader.TileMode.CLAMP)
        c.drawRoundRect(inner, 12 * d, 12 * d, face)

        val ink = mix(Color.rgb(0x1A, 0x14, 0x0E), Radio.INK, glow)
        listOf(tick, num, title, small, cond).forEach { it.color = ink }

        val l = inner.left + 30 * d
        val r = inner.right - 18 * d
        val span = r - l
        val h = inner.height()
        c.drawText("PROPWATCH", inner.centerX(), inner.top + h * 0.13f, title)

        // HF-6m scale: popular band markers on a log frequency scale, 1.8 - 54 MHz, like a shortwave dial.
        val y0 = inner.top + h * 0.34f
        c.drawText("HF", inner.left + 8 * d, y0, small)
        c.drawLine(l, y0, r, y0, tick)
        val lo = ln(1.8); val hi = ln(54.0)
        for ((label, mhz) in HF_BANDS) {
            val x = l + span * ((ln(mhz) - lo) / (hi - lo)).toFloat()
            c.drawLine(x, y0, x, y0 - 7 * d, tick)
            c.drawText(label, x, y0 - 10 * d, num)
        }
        // 2m band scale: 144.0 - 146.0 MHz
        val y1 = inner.top + h * 0.52f
        c.drawText("2m", inner.left + 8 * d, y1, small)
        c.drawLine(l, y1, r, y1, tick)
        for (i in 0..20) {
            val x = l + span * i / 20f
            val len = when { i % 10 == 0 -> 9f; i % 5 == 0 -> 6f; else -> 3.5f } * d
            c.drawLine(x, y1, x, y1 - len, tick)
            if (i % 10 == 0) c.drawText("${144 + i / 10}", x, y1 - 11 * d, num)
        }
        // 70cm band scale: 430 - 440 MHz
        val y2 = inner.top + h * 0.69f
        c.drawText("70", inner.left + 8 * d, y2, small)
        c.drawLine(l, y2, r, y2, tick)
        for (i in 0..10) {
            val x = l + span * i / 10f
            val len = (if (i % 5 == 0) 9f else 4.5f) * d
            c.drawLine(x, y2, x, y2 - len, tick)
            if (i % 5 == 0) c.drawText("${430 + i}", x, y2 - 11 * d, num)
        }
        // Condition scale: the label under the needle lights up in needle red.
        val y3 = inner.top + h * 0.89f
        cond.textAlign = Paint.Align.CENTER
        for ((i, name) in listOf("QUIET", "HEADS-UP", "OPEN").withIndex()) {
            val pos = levelPos(i)
            cond.color = if (kotlin.math.abs(needle - pos) < 0.06f) needlePaint.color else ink
            c.drawText(name, l + span * pos, y3, cond)
        }

        val x = l + span * needle
        c.drawLine(x, inner.top + h * 0.19f, x, y3 - 14 * d, needlePaint)
        c.drawRoundRect(inner, 12 * d, 12 * d, glare)
    }

    private fun levelPos(level: Int) = when (level) { 2 -> 0.88f; 1 -> 0.5f; else -> 0.12f }

    private fun animate(from: Float, to: Float, ms: Long, delay: Long, interp: android.animation.TimeInterpolator,
                        set: (Float) -> Unit) {
        anims += ValueAnimator.ofFloat(from, to).apply {
            duration = ms; startDelay = delay; interpolator = interp
            addUpdateListener { set(it.animatedValue as Float); invalidate() }
            start()
        }
    }

    /** Valve warm-up: the lamp glows up, then the needle tunes down onto the reading. Timed to the startup sound. */
    fun warmUp(level: Int) {
        anims.forEach { it.cancel() }; anims.clear()
        glow = 0f; needle = 1f; invalidate()
        animate(0f, 1f, 1600, 150, DecelerateInterpolator()) { glow = it }
        animate(1f, levelPos(level), 1050, 1000, OvershootInterpolator(0.8f)) { needle = it }
    }

    /** level: 0 quiet, 1 heads-up, 2 open (alert). */
    fun setLevel(level: Int, animated: Boolean = true) {
        val to = levelPos(level)
        if (!animated) { needle = to; invalidate(); return }
        animate(needle, to, 700, 0, OvershootInterpolator(1f)) { needle = it }
    }

    override fun onDetachedFromWindow() {
        anims.forEach { it.cancel() }; anims.clear()
        super.onDetachedFromWindow()
    }
}

/**
 * Backlit cream screen, like a valve set's tuning window, showing the station's
 * location. Warms up when shown; a red cursor sweeps while the fix is coming in.
 */
class LocationScreenView(ctx: Context) : View(ctx) {
    private val d = resources.displayMetrics.density
    private var glow = 0f
    private var sweep = 0f
    private var locating = true
    private var lines: List<String> = emptyList()   // city, country, lat/lon, locator, note
    private var message = "TUNING IN…"
    private val anims = mutableListOf<ValueAnimator>()

    private val bezel = Paint(Paint.ANTI_ALIAS_FLAG)
    private val face = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glare = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1.2f * d }
    private val head = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Radio.SERIF; textSize = 11 * d; letterSpacing = 0.35f; textAlign = Paint.Align.CENTER
    }
    private val big = Paint(head).apply { textSize = 24 * d; letterSpacing = 0.04f }
    private val mid = Paint(head).apply { textSize = 15 * d; letterSpacing = 0.12f; typeface = Typeface.SERIF }
    private val mono = Paint(head).apply { textSize = 14 * d; letterSpacing = 0.05f; typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }
    private val note = Paint(head).apply { textSize = 10 * d; letterSpacing = 0.1f; typeface = Typeface.MONOSPACE }
    private val cursor = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0xB3, 0x26, 0x1E); strokeWidth = 2.4f * d; strokeCap = Paint.Cap.ROUND
    }
    private val outer = RectF(); private val inner = RectF()

    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w)
        setMeasuredDimension(width, (width * 0.78f).toInt().coerceAtMost((300 * d).toInt()))
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        outer.set(0f, 0f, w.toFloat(), h.toFloat())
        inner.set(8 * d, 8 * d, w - 8 * d, h - 8 * d)
        bezel.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(Color.rgb(0xE6, 0xC8, 0x82), Radio.BRASS_DARK, Color.rgb(0xD4, 0xB0, 0x6A), Color.rgb(0x6E, 0x52, 0x22)),
            null, Shader.TileMode.CLAMP)
        glare.shader = LinearGradient(0f, inner.top, 0f, inner.top + inner.height() * 0.45f,
            Color.argb(0x55, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
    }

    private fun mix(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt())

    /** Draws [text] centred, shrinking it to fit the screen width. */
    private fun fit(c: Canvas, text: String, y: Float, p: Paint) {
        val max = inner.width() - 28 * d
        val size = p.textSize
        while (p.measureText(text) > max && p.textSize > 8 * d) p.textSize *= 0.93f
        c.drawText(text, inner.centerX(), y, p)
        p.textSize = size
    }

    override fun onDraw(c: Canvas) {
        c.drawRoundRect(outer, 20 * d, 20 * d, bezel)
        face.shader = RadialGradient(inner.centerX(), inner.centerY(), inner.width() * 0.7f,
            mix(Color.rgb(0x4A, 0x3F, 0x30), Color.rgb(0xF8, 0xE9, 0xBC), glow),
            mix(Color.rgb(0x22, 0x1C, 0x15), Color.rgb(0xB8, 0x98, 0x5E), glow), Shader.TileMode.CLAMP)
        c.drawRoundRect(inner, 14 * d, 14 * d, face)
        val ink = mix(Color.rgb(0x1A, 0x14, 0x0E), Radio.INK, glow)
        listOf(rule, head, big, mid, mono, note).forEach { it.color = ink }

        val h = inner.height()
        val top = inner.top
        c.drawText("STATION LOCATION", inner.centerX(), top + h * 0.13f, head)
        c.drawLine(inner.left + 24 * d, top + h * 0.18f, inner.right - 24 * d, top + h * 0.18f, rule)

        if (locating || lines.isEmpty()) {
            fit(c, message, top + h * 0.52f, mid)
            if (locating) {
                // Tuning cursor sweeping along a little scale.
                val l = inner.left + 36 * d; val r = inner.right - 36 * d; val y = top + h * 0.72f
                c.drawLine(l, y, r, y, rule)
                for (i in 0..10) { val x = l + (r - l) * i / 10f; c.drawLine(x, y, x, y - (if (i % 5 == 0) 8 else 4) * d, rule) }
                val x = l + (r - l) * sweep
                c.drawLine(x, y - 16 * d, x, y + 6 * d, cursor)
            }
        } else {
            fit(c, lines[0], top + h * 0.37f, big)
            fit(c, lines[1], top + h * 0.50f, mid)
            c.drawLine(inner.left + 60 * d, top + h * 0.57f, inner.right - 60 * d, top + h * 0.57f, rule)
            fit(c, lines[2], top + h * 0.69f, mono)
            fit(c, lines[3], top + h * 0.80f, mono)
            fit(c, lines[4], top + h * 0.91f, note)
        }
        c.drawRoundRect(inner, 14 * d, 14 * d, glare)
    }

    private fun animate(from: Float, to: Float, ms: Long, delay: Long = 0, repeat: Boolean = false, set: (Float) -> Unit) {
        anims += ValueAnimator.ofFloat(from, to).apply {
            duration = ms; startDelay = delay; interpolator = DecelerateInterpolator()
            if (repeat) { repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE }
            addUpdateListener { set(it.animatedValue as Float); invalidate() }
            start()
        }
    }

    /** Lamp warms up and the cursor starts sweeping. */
    fun showLocating(text: String = "TUNING IN…") {
        message = text; locating = true; lines = emptyList()
        anims.forEach { it.cancel() }; anims.clear()
        animate(glow, 1f, 1400) { glow = it }
        animate(0f, 1f, 1100, 300, repeat = true) { sweep = it }
    }

    fun showResult(city: String, country: String, latLon: String, locator: String, footnote: String) {
        anims.forEach { it.cancel() }; anims.clear()
        locating = false; glow = 1f
        lines = listOf(city, country, latLon, locator, footnote)
        // A brief flicker as the reading settles, like a lamp on a shaky filament.
        animate(0.75f, 1f, 450) { glow = it }
    }

    fun showMessage(text: String) {
        anims.forEach { it.cancel() }; anims.clear()
        locating = false; lines = emptyList(); message = text; glow = 1f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        anims.forEach { it.cancel() }; anims.clear()
        super.onDetachedFromWindow()
    }
}
