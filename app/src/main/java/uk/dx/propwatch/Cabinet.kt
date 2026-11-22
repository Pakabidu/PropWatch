package uk.dx.propwatch

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BitmapShader
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

/** Shared hardware drawing: etched chrome plates and slotted screws. */
object Hardware {
    private val plateFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mottle = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.rgb(0x5E, 0x5D, 0x57) }
    private val screwPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val slot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x3A, 0x3A, 0x36) }

    /** Mottled, etched-looking chrome like the radio's control plates. */
    fun plate(c: Canvas, r: RectF, radius: Float, d: Float) {
        plateFill.shader = LinearGradient(r.left, r.top, r.right, r.bottom,
            intArrayOf(Color.rgb(0xB9, 0xB8, 0xB0), Color.rgb(0xE6, 0xE4, 0xDC), Color.rgb(0xA9, 0xA7, 0x9F)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, radius, radius, plateFill)
        mottle.shader = BitmapShader(Radio.mottle, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        c.drawRoundRect(r, radius, radius, mottle)
        edge.strokeWidth = 1.2f * d
        c.drawRoundRect(r, radius, radius, edge)
    }

    fun screw(c: Canvas, x: Float, y: Float, d: Float, r: Float = 2.4f) {
        screwPaint.shader = RadialGradient(x - d, y - d, r * 1.8f * d, Color.rgb(0xF4, 0xF2, 0xEA), Radio.CHROME_DARK, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r * d, screwPaint)
        slot.strokeWidth = 0.8f * d
        c.drawLine(x - r * 0.7f * d, y, x + r * 0.7f * d, y, slot)
    }
}

/** Crackled leather carrying strap on aged copper brackets, with a copper latch at each corner. */
class HandleView(ctx: Context) : CabinetView(ctx) {
    private val strap = paint { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val crack = paint(Color.argb(0x8C, 0x14, 0x0A, 0x06)) { strokeWidth = 0.6f * d }
    private val copper = paint()
    private val verdigris = paint(Color.argb(0x59, 0x46, 0x5A, 0x3C))
    private val rivet = paint(Color.rgb(0x3A, 0x24, 0x14))
    private val path = Path()
    private val cracks = ArrayList<FloatArray>()

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), (40 * d).toInt())

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val r = kotlin.random.Random(3)
        cracks.clear()
        repeat(28) { cracks += floatArrayOf(r.nextFloat(), r.nextFloat() * 2 - 1, r.nextFloat() * 2 - 1) }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val l = w * 0.32f; val r = w * 0.68f; val base = h - 6 * d
        path.reset(); path.moveTo(l, base); path.cubicTo(l, 3 * d, r, 3 * d, r, base)
        strap.shader = LinearGradient(0f, 0f, 0f, h, Color.rgb(0x8A, 0x4E, 0x36), Color.rgb(0x3A, 0x1E, 0x14), Shader.TileMode.CLAMP)
        strap.strokeWidth = 12 * d
        c.drawPath(path, strap)
        for (k in cracks) {   // fine crazing on the strap's top face
            val x = l + (r - l) * k[0]
            val y = 9 * d + kotlin.math.abs(k[0] - 0.5f) * 17 * d
            c.drawLine(x, y + k[1] * 3 * d, x + k[2] * 3 * d, y + 5 * d, crack)
        }
        copper.shader = LinearGradient(0f, base - 9 * d, 0f, h,
            intArrayOf(Color.rgb(0xD9, 0xA8, 0x77), Radio.COPPER, Radio.COPPER_DARK), null, Shader.TileMode.CLAMP)
        for (x in listOf(l, r)) {
            c.drawRoundRect(x - 15 * d, base - 7 * d, x + 15 * d, h, 3 * d, 3 * d, copper)
            c.drawCircle(x + 6 * d, base + 1 * d, 3 * d, verdigris)
            c.drawCircle(x - 10 * d, base - 1 * d, 1.6f * d, rivet)
            c.drawCircle(x + 10 * d, base - 1 * d, 1.6f * d, rivet)
        }
        for (x in listOf(18 * d, w - 18 * d)) {   // corner latches
            c.drawRoundRect(x - 9 * d, base - 4 * d, x + 9 * d, h, 2 * d, 2 * d, copper)
            c.drawRoundRect(x - 3 * d, base - 9 * d, x + 3 * d, base - 2 * d, 1.5f * d, 1.5f * d, copper)
        }
    }
}

/** Plain black speaker grille: a fine basket weave in a bevelled recess. */
class GrilleView(ctx: Context) : CabinetView(ctx) {
    private val recess = paint(Color.rgb(0x05, 0x05, 0x05))
    private val shade = paint()
    private val rim = paint(Color.BLACK) { style = Paint.Style.STROKE; strokeWidth = 2 * d }
    private val weave = paint()
    private val outer = RectF(); private val inner = RectF()

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        outer.set(0f, 0f, w.toFloat(), h.toFloat())
        inner.set(4 * d, 4 * d, w - 4 * d, h - 4 * d)
        // One weave cell: two short bars, alternating light/dark, tiled.
        val s = (3 * d).toInt().coerceAtLeast(3)
        val cell = android.graphics.Bitmap.createBitmap(s * 2, s * 2, android.graphics.Bitmap.Config.ARGB_8888)
        val cc = Canvas(cell)
        val light = Paint().apply { color = Color.rgb(0x2A, 0x28, 0x26) }
        val dark = Paint().apply { color = Color.rgb(0x0E, 0x0D, 0x0C) }
        cc.drawColor(Color.rgb(0x16, 0x15, 0x14))
        for (gy in 0..1) for (gx in 0..1) {
            val odd = (gx + gy) % 2 == 0
            val x = gx * s.toFloat(); val y = gy * s.toFloat()
            cc.drawRect(x, y, x + s * 0.9f, y + s * 0.45f, if (odd) light else dark)
            cc.drawRect(x, y + s * 0.5f, x + s * 0.9f, y + s * 0.95f, if (odd) dark else light)
        }
        weave.shader = BitmapShader(cell, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        shade.shader = RadialGradient(w * 0.35f, h * 0.3f, w * 0.9f, Color.argb(0x0D, 255, 255, 255),
            Color.argb(0x59, 0, 0, 0), Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        c.drawRoundRect(outer, 10 * d, 10 * d, recess)
        c.drawRoundRect(inner, 8 * d, 8 * d, weave)
        c.drawRoundRect(inner, 8 * d, 8 * d, shade)
        c.drawRoundRect(inner, 8 * d, 8 * d, rim)
    }
}

/**
 * Narrow chrome plate of rocker switches, like a tone selector, plus two round
 * sockets. The rockers are real toggles (e.g. SOUND, MONITOR, GPS).
 */
class TonePlateView(ctx: Context) : CabinetView(ctx) {
    var switches: List<Pair<String, Boolean>> = emptyList()
        set(v) { field = v; invalidate() }
    var onToggle: ((Int, Boolean) -> Unit)? = null

    private val label = paint(Radio.INK) { typeface = Radio.SERIF; textSize = 8.5f * d; letterSpacing = 0.1f; textAlign = Paint.Align.CENTER }
    private val arrow = paint(Color.rgb(0x33, 0x33, 0x33)) { typeface = Radio.SERIF; textSize = 6 * d; textAlign = Paint.Align.CENTER }
    private val slotPaint = paint(Color.rgb(0x1A, 0x19, 0x18))
    private val rocker = paint()
    private val lamp = paint(Radio.LAMP_ALERT)
    private val socket = paint()
    private val hole = paint(Color.rgb(0x0C, 0x0B, 0x0A))
    private val pin = paint(Color.rgb(0x6C, 0x6A, 0x64))
    private val r = RectF()

    private val rowH get() = 28 * d
    private fun rowTop(i: Int) = 17 * d + i * rowH

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        r.set(0f, 0f, w, h)
        Hardware.plate(c, r, 4 * d, d)
        Hardware.screw(c, w / 2, 7 * d, d)
        for ((i, s) in switches.withIndex()) {
            val y = rowTop(i)
            c.drawText(s.first, w / 2, y + 8 * d, label)
            val bx = w / 2 - 8 * d; val by = y + 11 * d
            r.set(bx - 2 * d, by - 1 * d, bx + 18 * d, by + 10 * d)
            c.drawRoundRect(r, 2 * d, 2 * d, slotPaint)
            val on = s.second
            r.set(bx, by + if (on) 0f else 1.5f * d, bx + 16 * d, by + 8 * d + if (on) 0f else 1.5f * d)
            rocker.shader = LinearGradient(0f, r.top, 0f, r.bottom,
                if (on) Color.rgb(0x05, 0x05, 0x05) else Color.rgb(0x45, 0x42, 0x3F),
                if (on) Color.rgb(0x2A, 0x28, 0x26) else Color.rgb(0x0A, 0x0A, 0x0A), Shader.TileMode.CLAMP)
            c.drawRoundRect(r, 1.5f * d, 1.5f * d, rocker)
            if (on) c.drawCircle(r.centerX(), r.centerY(), 1.4f * d, lamp)
            c.drawText("▸", bx - 6 * d, by + 7 * d, arrow)
            c.drawText("◂", bx + 22 * d, by + 7 * d, arrow)
        }
        // Two round sockets, filling whatever height is left below the switches.
        val top = rowTop(switches.size) + 4 * d
        val gap = ((h - top) / 2).coerceAtLeast(22 * d)
        for (k in 0..1) {
            val cx = w / 2; val cy = top + gap * (k + 0.5f)
            if (cy + 10 * d > h) break
            socket.shader = LinearGradient(cx - 10 * d, cy - 10 * d, cx + 10 * d, cy + 10 * d,
                Color.rgb(0xF0, 0xEE, 0xE6), Color.rgb(0x77, 0x75, 0x6E), Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, 10 * d, socket)
            c.drawCircle(cx, cy, 6 * d, hole)
            c.drawCircle(cx, cy, 2.2f * d, pin)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN) return true
        if (e.action == MotionEvent.ACTION_UP) {
            val i = ((e.y - rowTop(0)) / rowH).toInt()
            if (i in switches.indices && e.y >= rowTop(0)) {
                val on = !switches[i].second
                switches = switches.toMutableList().also { it[i] = it[i].first to on }
                onToggle?.invoke(i, on)
            }
            performClick()
            return true
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}

/**
 * The tuning dial: smoky glass in a chrome bezel. Gold shortwave-style arc scales
 * (HF, 6m, 2m, 70cm) above a centre pivot, the condition scale (QUIET, HEADS-UP,
 * OPEN) around the top, and a gold horseshoe scale below with the nameplate script.
 */
class DialView(ctx: Context) : CabinetView(ctx) {
    private companion object {
        const val A0 = -150f
        const val A1 = -30f
        val HF = listOf("80" to 3.6, "40" to 7.1, "20" to 14.15, "10" to 28.5)
        val HORSESHOE = listOf("3", "5", "7", "10", "14", "18", "21", "24", "28")
    }

    private var needle = 0.2f
    private var glow = 1f

    private val bezel = paint()
    private val glass = paint()
    private val lamp = paint()
    private val arc = paint { style = Paint.Style.STROKE; strokeWidth = 1.2f * d }
    private val tick = paint { strokeWidth = 1f * d }
    private val num = paint { typeface = Radio.SERIF; textSize = 7.5f * d; textAlign = Paint.Align.CENTER }
    private val tag = paint { typeface = Radio.SERIF; textSize = 7.5f * d; textAlign = Paint.Align.CENTER }
    private val mc = paint { typeface = Radio.SERIF; textSize = 6 * d; textAlign = Paint.Align.CENTER }
    private val cond = paint { typeface = Radio.SERIF; textSize = 8.5f * d; letterSpacing = 0.12f; textAlign = Paint.Align.CENTER }
    private val shoe = paint()
    private val shoeInk = paint(Radio.INK) { typeface = Radio.SERIF; textSize = 7.5f * d; textAlign = Paint.Align.CENTER }
    private val shoeTick = paint(Radio.INK) { strokeWidth = 0.8f * d }
    private val mega = paint { typeface = Radio.SERIF; textSize = 6.5f * d; letterSpacing = 0.25f; textAlign = Paint.Align.CENTER }
    private val script = paint { typeface = Radio.SERIF_ITALIC; textAlign = Paint.Align.CENTER }
    private val small = paint { typeface = Typeface.SERIF; textSize = 5.5f * d; letterSpacing = 0.15f; textAlign = Paint.Align.CENTER }
    private val needlePaint = paint(Color.rgb(0xF4, 0xEC, 0xD8)) {
        strokeWidth = 2f * d; strokeCap = Paint.Cap.ROUND
        setShadowLayer(4 * d, 0f, 1 * d, Color.argb(0x99, 0, 0, 0))
    }
    private val hub = paint()
    private val hubRing = paint(Radio.GOLD) { style = Paint.Style.STROKE; strokeWidth = 1.4f * d }
    private val hubCap = paint(Color.rgb(0x2A, 0x2A, 0x26))
    private val glare = paint()
    private val outer = RectF(); private val inner = RectF(); private val oval = RectF()
    private val shoePath = Path()

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }   // the needle's shadow

    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w)
        setMeasuredDimension(width, (width * 0.92f).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        outer.set(0f, 0f, w.toFloat(), h.toFloat())
        inner.set(7 * d, 7 * d, w - 7 * d, h - 7 * d)
        bezel.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(Color.rgb(0xF4, 0xF4, 0xF0), Radio.CHROME_DARK, Color.rgb(0xDC, 0xDC, 0xD6), Color.rgb(0x50, 0x50, 0x4C)),
            null, Shader.TileMode.CLAMP)
        glass.shader = RadialGradient(inner.centerX(), inner.centerY(), inner.width() * 0.75f,
            intArrayOf(Color.rgb(0x3E, 0x41, 0x36), Color.rgb(0x22, 0x24, 0x1E), Color.rgb(0x0E, 0x0F, 0x0C)),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        glare.shader = LinearGradient(inner.left, inner.top, inner.right, inner.bottom,
            intArrayOf(Color.argb(0x1A, 255, 255, 255), Color.argb(0x08, 255, 255, 255), Color.argb(0, 255, 255, 255), Color.argb(0, 255, 255, 255)),
            floatArrayOf(0f, 0.35f, 0.36f, 1f), Shader.TileMode.CLAMP)
    }

    private fun ang(t: Float) = Math.toRadians((A0 + (A1 - A0) * t).toDouble())
    private fun cx() = inner.centerX()
    private fun cy() = inner.top + inner.width() * 0.47f

    override fun onDraw(c: Canvas) {
        c.drawRoundRect(outer, 22 * d, 22 * d, bezel)
        c.drawRoundRect(inner, 16 * d, 16 * d, glass)
        c.save()
        c.clipRect(inner)
        val s = inner.width(); val px = cx(); val py = cy()
        lamp.shader = RadialGradient(px, py, s * 0.5f, Color.argb((46 * glow).toInt(), 0xFF, 0xC8, 0x78),
            Color.argb(0, 0xFF, 0xC8, 0x78), Shader.TileMode.CLAMP)
        c.drawCircle(px, py, s * 0.5f, lamp)

        val gold = Radio.mix(Color.rgb(0x4A, 0x3E, 0x28), Radio.GOLD, glow)
        val cream = Radio.mix(Color.rgb(0x50, 0x4A, 0x40), Radio.CREAM, glow)
        arc.color = gold; tick.color = gold; num.color = gold; tag.color = cream; mc.color = cream
        mega.color = gold; script.color = gold; small.color = cream

        fun cosf(a: Double) = kotlin.math.cos(a).toFloat()
        fun sinf(a: Double) = kotlin.math.sin(a).toFloat()
        fun scale(r: Float, label: String, marks: List<Pair<String?, Float>>, minor: Int, unit: Boolean) {
            val rr = s * r
            oval.set(px - rr, py - rr, px + rr, py + rr)
            c.drawArc(oval, A0, A1 - A0, false, arc)
            if (minor > 0) for (i in 0..minor) {
                val a = ang(i / minor.toFloat())
                c.drawLine(px + rr * cosf(a), py + rr * sinf(a), px + (rr + 2.5f * d) * cosf(a), py + (rr + 2.5f * d) * sinf(a), tick)
            }
            for ((t, v) in marks) {
                val a = ang(v)
                c.drawLine(px + rr * cosf(a), py + rr * sinf(a), px + (rr + 5 * d) * cosf(a), py + (rr + 5 * d) * sinf(a), tick)
                if (t != null) c.drawText(t, px + (rr + 10 * d) * cosf(a), py + (rr + 10 * d) * sinf(a) + 3 * d, num)
            }
            val a = ang(0f)
            c.drawText(label, px + (rr + 9 * d) * cosf(a), py + (rr + 9 * d) * sinf(a) + 3 * d, tag)
            if (unit) { val b = ang(1f); c.drawText("M.C.", px + (rr + 11 * d) * cosf(b), py + (rr + 11 * d) * sinf(b) + 3 * d, mc) }
        }
        val lo = ln(1.8); val hi = ln(32.0)
        scale(0.15f, "HF", HF.map { (l, f) -> l to ((ln(f) - lo) / (hi - lo)).toFloat() }, 0, false)
        scale(0.21f, "6M", (50..54).map { (if (it in 51..53) "$it" else null) to (it - 50) / 4f }, 16, false)
        scale(0.27f, "2M", (0..4).map { (if (it == 2) "145" else null) to it / 4f }, 20, false)
        scale(0.33f, "70", (0..2).map { (if (it == 1) "435" else null) to it / 2f }, 10, true)

        // Condition scale around the top; the word under the needle lights up.
        val rc = s * 0.405f
        for ((i, name) in listOf("QUIET", "HEADS-UP", "OPEN").withIndex()) {
            val t = levelPos(i); val a = ang(t)
            cond.color = if (kotlin.math.abs(needle - t) < 0.07f) Radio.mix(cream, Radio.LAMP_ALERT, glow * 0.8f) else cream
            c.drawText(name, px + rc * cosf(a), py + rc * sinf(a) + 3 * d, cond)
        }

        // Gold horseshoe scale below the pivot.
        val r1 = s * 0.30f; val r2 = s * 0.36f
        shoePath.reset()
        oval.set(px - r2, py - r2, px + r2, py + r2); shoePath.arcTo(oval, 18f, 144f, true)
        oval.set(px - r1, py - r1, px + r1, py + r1); shoePath.arcTo(oval, 162f, -144f, false)
        shoePath.close()
        shoe.shader = LinearGradient(0f, py, 0f, py + r2,
            Radio.mix(Color.rgb(0x5A, 0x4A, 0x2C), Color.rgb(0xE8, 0xC7, 0x7A), glow),
            Radio.mix(Color.rgb(0x40, 0x34, 0x20), Color.rgb(0xB8, 0x91, 0x3E), glow), Shader.TileMode.CLAMP)
        c.drawPath(shoePath, shoe)
        val b0 = Math.toRadians(18.0); val b1 = Math.toRadians(162.0)
        for (i in 0..18) {
            val a = b1 - (b1 - b0) * i / 18
            c.drawLine(px + r1 * cosf(a), py + r1 * sinf(a), px + (r1 + 2.5f * d) * cosf(a), py + (r1 + 2.5f * d) * sinf(a), shoeTick)
        }
        for ((i, m) in HORSESHOE.withIndex()) {
            val a = b1 - (b1 - b0) * (i + 0.5) / HORSESHOE.size
            val rm = (r1 + r2) / 2
            c.drawText(m, px + rm * cosf(a), py + rm * sinf(a) + 3 * d, shoeInk)
        }
        c.drawText("MEGACYCLES", px, py + r2 + 9 * d, mega)
        script.textSize = 11 * d
        c.drawText("PropWatch", px - s * 0.15f, py + s * 0.15f, script)
        script.textSize = 10 * d
        c.drawText("DX Receiver", px + s * 0.15f, py + s * 0.15f, script)
        c.drawText("HF · VHF · UHF", px - s * 0.15f, py + s * 0.15f + 10 * d, small)
        c.drawText("PROPAGATION", px + s * 0.15f, py + s * 0.15f + 10 * d, small)

        // Needle (with a short tail) and the pivot.
        val a = ang(needle)
        c.drawLine(px - s * 0.07f * cosf(a), py - s * 0.07f * sinf(a), px + s * 0.385f * cosf(a), py + s * 0.385f * sinf(a), needlePaint)
        hub.shader = RadialGradient(px - 2 * d, py - 2 * d, 12 * d, Color.rgb(0xF6, 0xF6, 0xF2), Radio.CHROME_DARK, Shader.TileMode.CLAMP)
        c.drawCircle(px, py, 9 * d, hub)
        c.drawCircle(px, py, 6 * d, hubRing)
        c.drawCircle(px, py, 2.5f * d, hubCap)
        c.restore()
        c.drawRoundRect(inner, 16 * d, 16 * d, glare)
    }

    private fun levelPos(level: Int) = when (level) { 2 -> 0.8f; 1 -> 0.5f; else -> 0.2f }

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
 * Etched chrome band plate: band name, black push-button, and the live condition for
 * that band on a bracket rule, laid out like a "best reception" chart. One button is
 * always down; pressing another pops the last one up.
 */
class BandPlateView(ctx: Context) : CabinetView(ctx) {
    /** label, status word, lamp colour (or null for unlit) */
    data class Row(val label: String, val status: String, val lamp: Int?)

    var rows: List<Row> = emptyList()
        set(v) { field = v; requestLayout(); invalidate() }
    var selected = 0
        set(v) { field = v; invalidate() }
    var onSelect: ((Int) -> Unit)? = null

    private val head = paint(Radio.INK) { typeface = Radio.SERIF; textSize = 8 * d; letterSpacing = 0.25f; textAlign = Paint.Align.CENTER }
    private val label = paint(Radio.INK) { typeface = Radio.SERIF; textSize = 9.5f * d; letterSpacing = 0.08f; textAlign = Paint.Align.RIGHT }
    private val status = paint(Radio.INK) { typeface = Radio.SERIF; textSize = 9.5f * d; letterSpacing = 0.1f; textAlign = Paint.Align.LEFT }
    private val rule = paint(Color.rgb(0x2A, 0x28, 0x24)) { strokeWidth = 1.1f * d }
    private val well = paint(Color.argb(0x59, 0, 0, 0))
    private val btn = paint()
    private val shine = paint(Color.argb(0x2E, 255, 255, 255))
    private val lampPaint = paint()
    private val lampRim = paint(Color.rgb(0x2A, 0x28, 0x24)) { style = Paint.Style.STROKE; strokeWidth = 0.8f * d }
    private val r = RectF()

    private val rowH get() = 30 * d
    private val top get() = 26 * d

    override fun onMeasure(w: Int, h: Int) =
        setMeasuredDimension(MeasureSpec.getSize(w), (top + rows.size * rowH + 10 * d).toInt())

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        r.set(0f, 0f, w, h)
        Hardware.plate(c, r, 5 * d, d)
        c.drawText("WAVEBAND", w * 0.30f, 17 * d, head)
        c.drawText("CONDITIONS NOW", w * 0.80f, 17 * d, head)
        val bx = w * 0.55f; val lineX = w * 0.66f
        c.drawLine(lineX, top + 2 * d, lineX, top + rows.size * rowH - 6 * d, rule)
        for ((i, row) in rows.withIndex()) {
            val cy = top + i * rowH + rowH / 2
            val down = i == selected
            c.drawText(row.label, bx - 12 * d, cy + 3.5f * d, label)
            r.set(bx - 2 * d, cy - 11 * d, bx + 22 * d, cy + 13 * d)
            c.drawRoundRect(r, 3 * d, 3 * d, well)
            r.set(bx, cy - 10 * d, bx + 20 * d, cy + 10 * d)
            if (down) r.offset(0f, 1.5f * d)
            btn.shader = if (down) LinearGradient(0f, r.top, 0f, r.bottom, Color.rgb(0x03, 0x03, 0x03), Color.rgb(0x1E, 0x1C, 0x1A), Shader.TileMode.CLAMP)
            else LinearGradient(0f, r.top, 0f, r.bottom, intArrayOf(Color.rgb(0x4A, 0x47, 0x44), Color.rgb(0x15, 0x14, 0x13), Color.rgb(0x05, 0x05, 0x05)),
                null, Shader.TileMode.CLAMP)
            c.drawRoundRect(r, 3 * d, 3 * d, btn)
            if (!down) c.drawRoundRect(r.left + 3 * d, r.top + 2 * d, r.right - 3 * d, r.top + 6 * d, 2 * d, 2 * d, shine)
            c.drawLine(lineX - 5 * d, cy, lineX + 5 * d, cy, rule)
            if (row.lamp != null) {
                lampPaint.shader = RadialGradient(lineX + 12 * d, cy - 1 * d, 4 * d, Color.WHITE, row.lamp, Shader.TileMode.CLAMP)
                c.drawCircle(lineX + 13 * d, cy, 3 * d, lampPaint)
                c.drawCircle(lineX + 13 * d, cy, 3 * d, lampRim)
            }
            c.drawText(row.status, lineX + 21 * d, cy + 3.5f * d, status)
        }
        for ((x, y) in listOf(8 * d to 8 * d, w - 8 * d to 8 * d, 8 * d to h - 8 * d, w - 8 * d to h - 8 * d)) Hardware.screw(c, x, y, d)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN) return true
        if (e.action == MotionEvent.ACTION_UP && rows.isNotEmpty()) {
            val i = ((e.y - top) / rowH).toInt()
            if (e.y >= top && i in rows.indices && i != selected) { selected = i; onSelect?.invoke(i) }
            performClick()
            return true
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}

/**
 * The lid's log chart: cream graph paper with a row per band and the last 24 hours
 * across, marking every check: green open, amber heads-up, a dash for quiet,
 * a red cross where the band couldn't be checked.
 */
class LogChartView(ctx: Context) : CabinetView(ctx) {
    var entries: List<Store.LogEntry> = emptyList()
        set(v) { field = v; invalidate() }

    private val bg = paint(Color.rgb(0x0A, 0x09, 0x08))
    private val paper = paint(Radio.PAPER)
    private val speck = paint()
    private val fine = paint(Color.argb(0x73, 0xA0, 0x82, 0x50)) { strokeWidth = 0.5f * d }
    private val bold = paint(Color.argb(0xCC, 0x5A, 0x46, 0x28)) { strokeWidth = 0.9f * d }
    private val scaleText = paint(Radio.CREAM) { typeface = Radio.SERIF; textSize = 7 * d; textAlign = Paint.Align.CENTER }
    private val rowText = paint(Radio.CREAM) { typeface = Radio.SERIF; textSize = 9 * d; letterSpacing = 0.1f; textAlign = Paint.Align.CENTER }
    private val key = paint(Radio.CREAM) { typeface = Typeface.SERIF; textSize = 6.5f * d; letterSpacing = 0.08f; textAlign = Paint.Align.CENTER }
    private val diamond = paint(Radio.CREAM)
    private val open = paint(Color.rgb(0x2E, 0x8B, 0x57))
    private val openRim = paint(Color.rgb(0x11, 0x22, 0x33)) { style = Paint.Style.STROKE; strokeWidth = 0.8f * d }
    private val heads = paint(Color.rgb(0xD9, 0x8A, 0x1E))
    private val quiet = paint(Color.argb(0xBF, 0x28, 0x1E, 0x14)) { strokeWidth = 1.2f * d }
    private val fail = paint(Color.rgb(0xB3, 0x26, 0x1E)) { strokeWidth = 1.2f * d }
    private val dpath = Path()
    private val specks = ArrayList<FloatArray>()

    private val head get() = 18 * d
    private val labW get() = 34 * d
    private val rowH get() = 22 * d

    override fun onMeasure(w: Int, h: Int) =
        setMeasuredDimension(MeasureSpec.getSize(w), (head + Bands.ALL.size * rowH + 16 * d).toInt())

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val r = kotlin.random.Random(11)
        specks.clear()
        repeat((w * h / (30 * d * d)).toInt().coerceAtMost(900)) {
            specks += floatArrayOf(r.nextFloat(), r.nextFloat(), (0.6f + r.nextFloat() * 1.4f) * d, 0.05f + r.nextFloat() * 0.07f)
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.drawRect(0f, 0f, w, h, bg)
        val gx = labW; val gw = w - labW - 6 * d; val gy = head; val gh = Bands.ALL.size * rowH
        c.drawRect(gx, gy, gx + gw, gy + gh, paper)
        for (s in specks) {
            speck.color = Color.argb((s[3] * 255).toInt(), 0x96, 0x78, 0x46)
            c.drawCircle(gx + s[0] * gw, gy + s[1] * gh, s[2], speck)
        }
        for (k in 0..48) c.drawLine(gx + gw * k / 48, gy, gx + gw * k / 48, gy + gh, fine)
        for (k in 0..Bands.ALL.size * 2) c.drawLine(gx, gy + gh * k / (Bands.ALL.size * 2), gx + gw, gy + gh * k / (Bands.ALL.size * 2), fine)
        for (k in 0..24 step 4) c.drawLine(gx + gw * k / 24, gy, gx + gw * k / 24, gy + gh, bold)
        for (k in 0..Bands.ALL.size) c.drawLine(gx, gy + rowH * k, gx + gw, gy + rowH * k, bold)
        for (k in 0..24) {
            val x = gx + gw * k / 24
            if (k % 4 == 0) c.drawText(if (k == 24) "NOW" else "${k - 24}", x, 12 * d, scaleText)
            else if (k % 2 == 0) {
                dpath.reset(); dpath.moveTo(x, 6 * d); dpath.lineTo(x + 2 * d, 8.5f * d); dpath.lineTo(x, 11 * d); dpath.lineTo(x - 2 * d, 8.5f * d); dpath.close()
                c.drawPath(dpath, diamond)
            }
        }
        for ((i, b) in Bands.ALL.withIndex()) c.drawText(b.second, labW / 2, gy + rowH * i + rowH / 2 + 3.5f * d, rowText)

        val now = System.currentTimeMillis()
        for (e in entries) {
            val ago = (now - e.timeMs) / 3_600_000f
            if (ago > 24f || ago < 0f) continue
            val x = gx + gw * (24 - ago) / 24
            for ((i, b) in Bands.ALL.withIndex()) {
                val y = gy + rowH * i + rowH / 2
                when (e.levels[b.first] ?: 0) {
                    2 -> { c.drawCircle(x, y, 4.2f * d, open); c.drawCircle(x, y, 4.2f * d, openRim) }
                    1 -> c.drawCircle(x, y, 3.6f * d, heads)
                    0 -> c.drawLine(x - 2.5f * d, y, x + 2.5f * d, y, quiet)
                    else -> {
                        c.drawLine(x - 2.5f * d, y - 2.5f * d, x + 2.5f * d, y + 2.5f * d, fail)
                        c.drawLine(x + 2.5f * d, y - 2.5f * d, x - 2.5f * d, y + 2.5f * d, fail)
                    }
                }
            }
        }
        c.drawText(if (entries.isEmpty()) "HOURS AGO · CHECKS WILL BE LOGGED HERE"
            else "HOURS AGO · ● OPEN  ● HEADS-UP  – QUIET  × NO DATA", w / 2, h - 4 * d, key)
    }
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
