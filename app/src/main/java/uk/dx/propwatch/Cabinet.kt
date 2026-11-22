package uk.dx.propwatch

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin

/** Shared bits for the custom-drawn cabinet parts. */
abstract class CabinetView(ctx: Context) : View(ctx) {
    protected val d = resources.displayMetrics.density
    private val anims = mutableListOf<ValueAnimator>()

    protected fun animate(from: Float, to: Float, ms: Long, delay: Long = 0,
                          interp: TimeInterpolator = DecelerateInterpolator(), repeat: Boolean = false,
                          set: (Float) -> Unit) {
        anims += ValueAnimator.ofFloat(from, to).apply {
            duration = ms; startDelay = delay; interpolator = interp
            if (repeat) { repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART }
            addUpdateListener { set(it.animatedValue as Float); invalidate() }
            start()
        }
    }

    protected fun stopAnims() { anims.forEach { it.cancel() }; anims.clear() }

    override fun onDetachedFromWindow() { stopAnims(); super.onDetachedFromWindow() }

    protected fun paint(color: Int = Color.BLACK, block: Paint.() -> Unit = {}) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; block() }
}

/** Worn leather carrying strap with chrome end brackets, across the top of the case. */
class HandleView(ctx: Context) : CabinetView(ctx) {
    private val strap = paint { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val stitch = paint(Color.argb(0x90, 0xE8, 0xD2, 0xA8)) {
        style = Paint.Style.STROKE; strokeWidth = 0.9f * d; pathEffect = DashPathEffect(floatArrayOf(3 * d, 2.5f * d), 0f)
    }
    private val chrome = paint()
    private val rivet = paint(Radio.CHROME_DARK)
    private val path = Path()

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), (40 * d).toInt())

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val l = w * 0.30f; val r = w * 0.70f; val base = h - 6 * d
        path.reset()
        path.moveTo(l, base)
        path.cubicTo(l, 2 * d, r, 2 * d, r, base)
        strap.shader = LinearGradient(0f, 0f, 0f, h, Color.rgb(0x8A, 0x52, 0x3A), Color.rgb(0x3E, 0x22, 0x18), Shader.TileMode.CLAMP)
        strap.strokeWidth = 11 * d
        c.drawPath(path, strap)
        c.drawPath(path, stitch)
        // Chrome brackets the strap loops through.
        chrome.shader = LinearGradient(0f, base - 10 * d, 0f, h, Color.rgb(0xF2, 0xF2, 0xEE), Radio.CHROME_DARK, Shader.TileMode.CLAMP)
        for (x in listOf(l, r)) {
            c.drawRoundRect(x - 13 * d, base - 7 * d, x + 13 * d, h, 3 * d, 3 * d, chrome)
            c.drawCircle(x - 8 * d, base - 1 * d, 1.6f * d, rivet)
            c.drawCircle(x + 8 * d, base - 1 * d, 1.6f * d, rivet)
        }
    }
}

/**
 * Speaker cloth: dark woven grille behind a chrome-edged opening, with a stitched
 * cream motif — a Yagi beam sending out radio waves.
 */
class ClothView(ctx: Context) : CabinetView(ctx) {
    private val bg = paint(Color.rgb(0x10, 0x0F, 0x0E))
    private val weave = paint(Color.rgb(0x26, 0x24, 0x21)) { strokeWidth = 1f * d }
    private val rim = paint(Radio.CHROME_DARK) { style = Paint.Style.STROKE; strokeWidth = 1.4f * d }
    private val thread = paint(Color.rgb(0xE6, 0xDC, 0xC2)) {
        style = Paint.Style.STROKE; strokeWidth = 1.3f * d; strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(3.2f * d, 1.8f * d), 0f)
    }
    private val label = paint(Radio.GOLD) {
        typeface = Radio.SERIF_ITALIC; textSize = 15 * d; textAlign = Paint.Align.RIGHT; letterSpacing = 0.05f
    }
    private val sub = paint(Radio.CHROME) { typeface = Radio.SERIF; textSize = 7.5f * d; textAlign = Paint.Align.RIGHT; letterSpacing = 0.3f }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), (78 * d).toInt())

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val box = RectF(0f, 0f, w, h)
        c.drawRoundRect(box, 6 * d, 6 * d, bg)
        c.save()
        c.clipRect(box)
        // Diamond weave.
        var i = -h
        while (i < w + h) {
            c.drawLine(i, 0f, i + h, h, weave)
            c.drawLine(i + h, 0f, i, h, weave)
            i += 4.5f * d
        }
        c.restore()
        c.drawRoundRect(box, 6 * d, 6 * d, rim)

        // Stitched Yagi on a mast, beaming to the right.
        val cx = h * 0.95f; val cy = h * 0.5f; val s = h / 78f
        c.drawLine(cx - 26 * s, cy, cx + 20 * s, cy, thread)                // boom
        for ((k, half) in listOf(-22 to 20, -12 to 17, -2 to 15, 8 to 13, 17 to 11)) {
            c.drawLine(cx + k * s, cy - half * s, cx + k * s, cy + half * s, thread)
        }
        c.drawLine(cx - 4 * s, cy, cx - 4 * s, h - 8 * s, thread)           // mast
        c.drawLine(cx - 14 * s, h - 8 * s, cx + 6 * s, h - 8 * s, thread)
        for (k in 0..2) {                                                    // waves
            val rr = (14 + k * 9) * s
            c.drawArc(cx + 22 * s - rr, cy - rr, cx + 22 * s + rr, cy + rr, -40f, 80f, false, thread)
        }
        // Maker's script on the cloth, like a nameplate.
        c.drawText("PropWatch", w - 12 * d, h * 0.52f, label)
        c.drawText("DX RECEIVER · HF–UHF", w - 12 * d, h * 0.52f + 13 * d, sub)
    }
}

/**
 * The tuning dial: a black face in a chrome bezel, with gold arc scales for HF, 6m,
 * 2m and 70cm fanning out from a pivot near the bottom. The needle swings to
 * QUIET, HEADS-UP or OPEN on the outer scale.
 */
class DialView(ctx: Context) : CabinetView(ctx) {
    private companion object {
        val HF_BANDS = listOf("160" to 1.85, "80" to 3.6, "40" to 7.1, "20" to 14.15, "15" to 21.2, "10" to 28.5)
        const val SPAN = 42f   // degrees either side of vertical
    }

    private var needle = 1f   // 0..1 across the scale
    private var glow = 1f     // 0 = cold, 1 = warmed up

    private val bezel = paint()
    private val face = paint()
    private val lamp = paint()
    private val arc = paint { style = Paint.Style.STROKE; strokeWidth = 1.3f * d }
    private val tick = paint { strokeWidth = 1.1f * d }
    private val num = paint { typeface = Radio.SERIF; textSize = 7.5f * d; textAlign = Paint.Align.CENTER }
    private val tag = paint { typeface = Radio.SERIF; textSize = 7.5f * d; textAlign = Paint.Align.CENTER; letterSpacing = 0.05f }
    private val cond = paint { typeface = Radio.SERIF; textSize = 8f * d; textAlign = Paint.Align.CENTER; letterSpacing = 0.12f }
    private val script = paint { typeface = Radio.SERIF_ITALIC; textSize = 8.5f * d; textAlign = Paint.Align.CENTER }
    private val needlePaint = paint(Color.rgb(0xF4, 0xEC, 0xD8)) {
        strokeWidth = 2f * d; strokeCap = Paint.Cap.ROUND
        setShadowLayer(3 * d, 0f, 1 * d, Color.argb(0xA0, 0, 0, 0))
    }
    private val hub = paint()
    private val glare = paint()
    private val outer = RectF(); private val inner = RectF(); private val oval = RectF()

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }   // the needle's shadow

    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w)
        setMeasuredDimension(width, (width * 0.98f).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        outer.set(0f, 0f, w.toFloat(), h.toFloat())
        inner.set(6 * d, 6 * d, w - 6 * d, h - 6 * d)
        bezel.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(Color.rgb(0xF4, 0xF4, 0xF0), Radio.CHROME_DARK, Color.rgb(0xDC, 0xDC, 0xD6), Color.rgb(0x50, 0x50, 0x4C)),
            null, Shader.TileMode.CLAMP)
        glare.shader = LinearGradient(inner.left, inner.top, inner.right, inner.top + inner.height() * 0.6f,
            Color.argb(0x30, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
    }

    private fun angle(t: Float) = Math.toRadians((-90f - SPAN + 2 * SPAN * t).toDouble())

    override fun onDraw(c: Canvas) {
        c.drawRoundRect(outer, 16 * d, 16 * d, bezel)
        face.shader = RadialGradient(inner.centerX(), inner.bottom, inner.height(),
            Radio.mix(Color.rgb(0x14, 0x12, 0x10), Color.rgb(0x3A, 0x2C, 0x18), glow), Color.rgb(0x08, 0x08, 0x08),
            Shader.TileMode.CLAMP)
        c.drawRoundRect(inner, 11 * d, 11 * d, face)

        val gold = Radio.mix(Color.rgb(0x4A, 0x3E, 0x28), Radio.GOLD, glow)
        val cream = Radio.mix(Color.rgb(0x50, 0x4A, 0x40), Radio.CREAM, glow)
        arc.color = gold; tick.color = gold; num.color = gold; tag.color = cream; cond.color = cream; script.color = gold

        val s = inner.width()
        val px = inner.centerX(); val py = inner.top + s * 0.86f
        // Dial lamp glowing behind the pivot.
        lamp.shader = RadialGradient(px, py, s * 0.55f, Color.argb((70 * glow).toInt(), 0xFF, 0xC8, 0x70),
            Color.argb(0, 0xFF, 0xC8, 0x70), Shader.TileMode.CLAMP)
        c.drawCircle(px, py, s * 0.55f, lamp)

        fun scale(r: Float, label: String, marks: List<Pair<String?, Float>>, minor: Int) {
            val rr = s * r
            oval.set(px - rr, py - rr, px + rr, py + rr)
            c.drawArc(oval, -90f - SPAN, 2 * SPAN, false, arc)
            if (minor > 0) for (i in 0..minor) {
                val a = angle(i / minor.toFloat())
                c.drawLine(px + rr * cos(a).toFloat(), py + rr * sin(a).toFloat(),
                    px + (rr + 2.5f * d) * cos(a).toFloat(), py + (rr + 2.5f * d) * sin(a).toFloat(), tick)
            }
            for ((text, t) in marks) {
                val a = angle(t)
                c.drawLine(px + rr * cos(a).toFloat(), py + rr * sin(a).toFloat(),
                    px + (rr + 5 * d) * cos(a).toFloat(), py + (rr + 5 * d) * sin(a).toFloat(), tick)
                if (text != null) c.drawText(text, px + (rr + 10 * d) * cos(a).toFloat(),
                    py + (rr + 10 * d) * sin(a).toFloat() + 3 * d, num)
            }
            // Band name at the left end of its scale (inside the arc for HF, the innermost).
            val a = angle(0f)
            val tr = if (label == "HF") rr - 10 * d else rr + 10 * d
            c.drawText(label, px + tr * cos(a).toFloat(), py + tr * sin(a).toFloat() + 3 * d, tag)
        }
        val lo = ln(1.8); val hi = ln(32.0)
        scale(0.27f, "HF", HF_BANDS.map { (l, f) -> l to ((ln(f) - lo) / (hi - lo)).toFloat() }, 0)
        // End marks are unnumbered: the band name sits at the left end instead.
        scale(0.39f, "6M", (50..54).map { (if (it in 51..53) "$it" else null) to (it - 50) / 4f }, 16)
        scale(0.51f, "2M", (0..4).map { (if (it == 2) "145" else null) to it / 4f }, 20)
        scale(0.63f, "70", (0..2).map { (if (it == 1) "435" else null) to it / 2f }, 10)

        // Condition scale: the label under the needle lights up.
        val rc = s * 0.77f
        for ((i, name) in listOf("QUIET", "HEADS-UP", "OPEN").withIndex()) {
            val t = levelPos(i)
            val a = angle(t)
            cond.color = if (kotlin.math.abs(needle - t) < 0.07f) Radio.mix(cream, Radio.LAMP_ALERT, glow * 0.85f) else cream
            c.drawText(name, px + rc * cos(a).toFloat(), py + rc * sin(a).toFloat() + 3 * d, cond)
        }
        c.drawText("Short Wave · VHF · UHF", px, py + s * 0.11f, script)

        // Needle and chrome hub.
        val a = angle(needle)
        c.drawLine(px, py, px + s * 0.69f * cos(a).toFloat(), py + s * 0.69f * sin(a).toFloat(), needlePaint)
        hub.shader = RadialGradient(px - 2 * d, py - 2 * d, 9 * d, Color.rgb(0xF6, 0xF6, 0xF2), Radio.CHROME_DARK, Shader.TileMode.CLAMP)
        c.drawCircle(px, py, 7 * d, hub)
        c.drawRoundRect(inner, 11 * d, 11 * d, glare)
    }

    private fun levelPos(level: Int) = when (level) { 2 -> 0.82f; 1 -> 0.5f; else -> 0.18f }

    /** Valve warm-up: the lamp glows up, then the needle swings onto the reading. Timed to the startup sound. */
    fun warmUp(level: Int) {
        stopAnims()
        glow = 0f; needle = 1f; invalidate()
        animate(0f, 1f, 1600, 150) { glow = it }
        animate(1f, levelPos(level), 1050, 1000, OvershootInterpolator(0.8f)) { needle = it }
    }

    /** level: 0 quiet, 1 heads-up, 2 open (alert). */
    fun setLevel(level: Int, animated: Boolean = true) {
        val to = levelPos(level)
        if (!animated) { needle = to; invalidate(); return }
        animate(needle, to, 700, 0, OvershootInterpolator(1f)) { needle = it }
    }
}

/**
 * Brushed-chrome plate of black band push-buttons. One is always down; pressing
 * another pops the last one up, like a mechanical band switch.
 */
class BandStripView(ctx: Context) : CabinetView(ctx) {
    var bands: List<Pair<String, String>> = emptyList()   // key to label
        set(v) { field = v; invalidate() }
    var selected = 0
        set(v) { field = v; invalidate() }
    var onSelect: ((Int) -> Unit)? = null

    private val plate = paint()
    private val hair = paint(Color.argb(0x22, 0xFF, 0xFF, 0xFF)) { strokeWidth = 0.6f * d }
    private val edge = paint(Radio.CHROME_DARK) { style = Paint.Style.STROKE; strokeWidth = 1.2f * d }
    private val text = paint(Color.rgb(0x22, 0x20, 0x1C)) {
        typeface = Radio.SERIF; textSize = 9.5f * d; letterSpacing = 0.12f; textAlign = Paint.Align.RIGHT
    }
    private val head = paint(Color.rgb(0x22, 0x20, 0x1C)) {
        typeface = Radio.SERIF; textSize = 7.5f * d; letterSpacing = 0.25f; textAlign = Paint.Align.CENTER
    }
    private val btn = paint()
    private val btnEdge = paint(Color.BLACK) { style = Paint.Style.STROKE; strokeWidth = 1f * d }
    private val pilot = paint(Radio.LAMP_ALERT)
    private val screw = paint()
    private val r = RectF()

    private fun rowTop(i: Int) = height * 0.13f + i * rowH()
    private fun rowH() = height * 0.80f / bands.size.coerceAtLeast(1)

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        plate.shader = LinearGradient(0f, 0f, w.toFloat(), 0f,
            intArrayOf(Color.rgb(0xA8, 0xA8, 0xA2), Color.rgb(0xE8, 0xE8, 0xE2), Color.rgb(0xB4, 0xB4, 0xAE), Color.rgb(0xD6, 0xD6, 0xD0)),
            floatArrayOf(0f, 0.35f, 0.7f, 1f), Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        r.set(0f, 0f, w, h)
        c.drawRoundRect(r, 5 * d, 5 * d, plate)
        var y = 2 * d
        while (y < h) { c.drawLine(3 * d, y, w - 3 * d, y, hair); y += 2.2f * d }
        c.drawRoundRect(r, 5 * d, 5 * d, edge)
        c.drawText("WAVEBAND", w / 2, h * 0.09f, head)
        val sy = h - 6 * d
        screw.shader = RadialGradient(w / 2 - 1 * d, sy - 1 * d, 4 * d, Color.WHITE, Radio.CHROME_DARK, Shader.TileMode.CLAMP)
        c.drawCircle(w / 2, sy, 2.4f * d, screw)

        val bs = (rowH() * 0.62f).coerceAtMost(24 * d)
        for ((i, b) in bands.withIndex()) {
            val cy = rowTop(i) + rowH() / 2
            val down = i == selected
            val right = w - 8 * d
            r.set(right - bs, cy - bs / 2, right, cy + bs / 2)
            if (down) r.offset(0f, 1.5f * d)
            btn.shader = LinearGradient(0f, r.top, 0f, r.bottom,
                if (down) Color.rgb(0x05, 0x05, 0x05) else Color.rgb(0x3C, 0x39, 0x36),
                if (down) Color.rgb(0x1C, 0x1A, 0x18) else Color.rgb(0x08, 0x08, 0x08), Shader.TileMode.CLAMP)
            c.drawRoundRect(r, 3 * d, 3 * d, btn)
            c.drawRoundRect(r, 3 * d, 3 * d, btnEdge)
            if (down) c.drawCircle(r.centerX(), r.centerY(), 2.2f * d, pilot)
            c.drawText(b.second, right - bs - 7 * d, cy + 3.5f * d, text)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN) return true
        if (e.action == MotionEvent.ACTION_UP && bands.isNotEmpty()) {
            val i = ((e.y - rowTop(0)) / rowH()).toInt()
            if (i in bands.indices && i != selected) { selected = i; onSelect?.invoke(i) }
            performClick()
            return true
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}

/**
 * The station log in the lid: a cream-on-black world chart with the station pinned
 * on it, and the reading below. Dims and warms up when retuned; a cursor sweeps the
 * chart while a fix is coming in.
 */
class StationLogView(ctx: Context) : CabinetView(ctx) {
    private var glow = 1f
    private var sweep = 0f
    private var pulse = 0f
    private var locating = false
    private var lines: List<String> = emptyList()   // city, country, lat/lon, locator, footnote
    private var message = "TUNING IN…"
    private var pinLat = Double.NaN
    private var pinLon = Double.NaN

    private val ribbon = paint(Radio.CREAM)
    private val ribbonText = paint(Radio.INK) { typeface = Radio.SERIF; textSize = 10 * d; letterSpacing = 0.45f; textAlign = Paint.Align.CENTER }
    private val sea = paint(Color.rgb(0x0B, 0x0A, 0x09))
    private val grid = paint { strokeWidth = 0.6f * d }
    private val land = paint()
    private val coast = paint { style = Paint.Style.STROKE; strokeWidth = 0.7f * d }
    private val frame = paint { style = Paint.Style.STROKE; strokeWidth = 1f * d }
    private val pin = paint(Color.rgb(0xE0, 0x3A, 0x2E))
    private val halo = paint { style = Paint.Style.STROKE; strokeWidth = 1.5f * d }
    private val sweepPaint = paint(Radio.LAMP_ALERT) { strokeWidth = 1.6f * d }
    private val big = paint { typeface = Radio.SERIF; textSize = 20 * d; letterSpacing = 0.05f; textAlign = Paint.Align.CENTER }
    private val mid = paint { typeface = Radio.SERIF; textSize = 12.5f * d; letterSpacing = 0.25f; textAlign = Paint.Align.CENTER }
    private val mono = paint { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textSize = 12.5f * d; textAlign = Paint.Align.CENTER }
    private val note = paint { typeface = Typeface.MONOSPACE; textSize = 9 * d; letterSpacing = 0.05f; textAlign = Paint.Align.CENTER }
    private val mapRect = RectF()
    private val shapes = ArrayList<Path>()

    private val ribbonH get() = 22 * d
    private val textH get() = 112 * d

    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w)
        setMeasuredDimension(width, (ribbonH + width * 0.46f + textH).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        mapRect.set(0f, ribbonH + 4 * d, w.toFloat(), ribbonH + 4 * d + w * 0.46f - 4 * d)
        shapes.clear()
        for (poly in WorldMap.LAND) {
            val p = Path()
            for (i in poly.indices step 2) {
                val x = mapX(poly[i].toDouble()); val y = mapY(poly[i + 1].toDouble())
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
            shapes += p
        }
    }

    private fun mapX(lon: Double) = mapRect.left + ((lon + 180) / 360 * mapRect.width()).toFloat()
    private fun mapY(lat: Double) = mapRect.top + ((WorldMap.TOP - lat) / (WorldMap.TOP - WorldMap.BOTTOM) * mapRect.height()).toFloat()

    private fun fit(c: Canvas, t: String, y: Float, p: Paint) {
        val size = p.textSize
        while (p.measureText(t) > width - 16 * d && p.textSize > 7 * d) p.textSize *= 0.93f
        c.drawText(t, width / 2f, y, p)
        p.textSize = size
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val cream = Radio.mix(Color.rgb(0x40, 0x3B, 0x33), Radio.CREAM, glow)
        // Title ribbon, like the lid's nameplate band.
        c.drawRoundRect(0f, 0f, w, ribbonH, 2 * d, 2 * d, ribbon)
        c.drawText("STATION LOG", w / 2, ribbonH * 0.68f, ribbonText)

        // World chart.
        c.drawRect(mapRect, sea)
        grid.color = Radio.mix(Color.rgb(0x1E, 0x1B, 0x17), Color.rgb(0x3A, 0x34, 0x2A), glow)
        for (lon in -150..150 step 30) c.drawLine(mapX(lon.toDouble()), mapRect.top, mapX(lon.toDouble()), mapRect.bottom, grid)
        for (lat in -30..60 step 30) c.drawLine(mapRect.left, mapY(lat.toDouble()), mapRect.right, mapY(lat.toDouble()), grid)
        land.color = Radio.mix(Color.rgb(0x3A, 0x35, 0x2C), Color.rgb(0xD8, 0xCB, 0xA6), glow)
        coast.color = Radio.mix(Color.rgb(0x50, 0x48, 0x3A), Radio.CREAM, glow)
        c.save(); c.clipRect(mapRect)
        for (p in shapes) { c.drawPath(p, land); c.drawPath(p, coast) }
        if (locating) {
            val x = mapRect.left + mapRect.width() * sweep
            sweepPaint.alpha = 0xC0
            c.drawLine(x, mapRect.top, x, mapRect.bottom, sweepPaint)
        } else if (!pinLat.isNaN()) {
            val x = mapX(pinLon); val y = mapY(pinLat)
            halo.color = Color.argb((0xC0 * (1 - pulse)).toInt(), 0xE0, 0x3A, 0x2E)
            c.drawCircle(x, y, 4 * d + pulse * 12 * d, halo)
            c.drawCircle(x, y, 3.6f * d, pin)
        }
        c.restore()
        frame.color = cream
        c.drawRect(mapRect, frame)

        // The reading.
        listOf(big, mid, mono, note).forEach { it.color = cream }
        val top = mapRect.bottom + 6 * d
        if (locating || lines.isEmpty()) {
            fit(c, message, top + textH * 0.42f, mid)
        } else {
            fit(c, lines[0], top + 24 * d, big)
            fit(c, lines[1], top + 42 * d, mid)
            fit(c, lines[2], top + 64 * d, mono)
            fit(c, lines[3], top + 82 * d, mono)
            fit(c, lines[4], top + 100 * d, note)
        }
    }

    /** Chart dims, warms up and a cursor sweeps it while the fix comes in. */
    fun showLocating(text: String = "TUNING IN…") {
        stopAnims()
        message = text; locating = true; lines = emptyList()
        animate(0f, 1f, 1400) { glow = it }
        animate(0f, 1f, 1600, 200, LinearInterpolator(), repeat = true) { sweep = it }
    }

    fun showResult(city: String, country: String, latLon: String, locator: String, footnote: String,
                   lat: Double, lon: Double, animate: Boolean = true) {
        stopAnims()
        locating = false; glow = 1f
        lines = listOf(city, country, latLon, locator, footnote)
        pinLat = lat; pinLon = lon
        if (animate) {
            animate(0.7f, 1f, 450) { glow = it }   // a flicker as the reading settles
            animate(0f, 1f, 900, 0, DecelerateInterpolator()) { pulse = it }
        } else { pulse = 1f; invalidate() }
    }

    fun showMessage(text: String) {
        stopAnims()
        locating = false; lines = emptyList(); message = text; glow = 1f
        invalidate()
    }
}
