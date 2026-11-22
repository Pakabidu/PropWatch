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

    /**
     * Text unit: 1.3x the original lettering, growing with the phone's font-size
     * setting up to 1.5x, so the cabinet stays readable without overflowing.
     */
    protected val t = d * (1.3f * resources.configuration.fontScale).coerceIn(1.3f, 1.5f)

    /** Draws text with the paint's alignment, shrinking it until it fits [maxW]. */
    protected fun fitText(c: Canvas, text: String, x: Float, y: Float, p: Paint, maxW: Float) {
        val size = p.textSize
        while (p.measureText(text) > maxW && p.textSize > 8 * d) p.textSize *= 0.94f
        c.drawText(text, x, y, p)
        p.textSize = size
    }
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

    private val clip = Path()

    /** Mottled, etched-looking chrome like the radio's control plates, scratched and grubby with use. */
    fun plate(c: Canvas, r: RectF, radius: Float, d: Float, seed: Int = 0) {
        plateFill.shader = LinearGradient(r.left, r.top, r.right, r.bottom,
            intArrayOf(Color.rgb(0xB0, 0xAE, 0xA4), Color.rgb(0xDC, 0xD9, 0xCE), Color.rgb(0x9E, 0x9B, 0x90)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, radius, radius, plateFill)
        mottle.shader = BitmapShader(Radio.mottle, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        c.drawRoundRect(r, radius, radius, mottle)
        clip.reset(); clip.addRoundRect(r, radius, radius, Path.Direction.CW)
        c.save(); c.clipPath(clip)
        Wear.panel(c, r, seed, d, heavy = true)
        Wear.chips(c, r, seed, 14, d, Color.argb(150, 0x6A, 0x60, 0x50))   // plating worn through to the base metal
        c.restore()
        edge.strokeWidth = 1.2f * d
        c.drawRoundRect(r, radius, radius, edge)
    }

    private val jewelRing = Paint(Paint.ANTI_ALIAS_FLAG)
    private val jewelDome = Paint(Paint.ANTI_ALIAS_FLAG)
    private val jewelFacet = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /**
     * A faceted jewel pilot lamp in a chrome ring, lit from behind: [colour] is the
     * glass. Used for the small decorative lamps around the set.
     */
    fun jewel(c: Canvas, x: Float, y: Float, r: Float, colour: Int, d: Float) {
        jewelRing.shader = RadialGradient(x - r * 0.4f, y - r * 0.4f, r * 1.9f,
            intArrayOf(Color.rgb(0xF2, 0xF2, 0xEC), Radio.CHROME_DARK, Color.rgb(0x44, 0x44, 0x40)), null, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r * 1.35f, jewelRing)
        jewelDome.shader = RadialGradient(x - r * 0.25f, y - r * 0.3f, r * 1.2f,
            intArrayOf(Color.WHITE, Radio.mix(colour, Color.WHITE, 0.35f), colour, Radio.mix(colour, Color.BLACK, 0.55f)),
            floatArrayOf(0f, 0.25f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, jewelDome)
        // Cut facets catching the light.
        jewelFacet.strokeWidth = 0.5f * d; jewelFacet.color = Color.argb(70, 255, 255, 255)
        for (k in 0 until 6) {
            val a = k / 6.0 * Math.PI * 2
            c.drawLine(x + r * 0.35f * kotlin.math.cos(a).toFloat(), y + r * 0.35f * kotlin.math.sin(a).toFloat(),
                x + r * 0.95f * kotlin.math.cos(a).toFloat(), y + r * 0.95f * kotlin.math.sin(a).toFloat(), jewelFacet)
        }
    }

    /** Rust bloom around a screw head that's seen damp sheds. */
    private val rust = Paint(Paint.ANTI_ALIAS_FLAG)
    fun rustRing(c: Canvas, x: Float, y: Float, d: Float, r: Float = 2.4f) {
        rust.shader = RadialGradient(x, y, r * 2.2f * d, intArrayOf(Color.argb(0, 0x8A, 0x4A, 0x1A), Color.argb(70, 0x8A, 0x4A, 0x1A),
            Color.argb(0, 0x8A, 0x4A, 0x1A)), floatArrayOf(0.4f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r * 2.2f * d, rust)
    }

    fun screw(c: Canvas, x: Float, y: Float, d: Float, r: Float = 2.4f) {
        rustRing(c, x, y, d, r)
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
        // Years of dust settled into the cloth, and a paler patch where it's been brushed.
        c.save(); c.clipRect(inner)
        Wear.dust(c, inner, 3, (inner.width() * inner.height() / (d * d) / 30).toInt(), d, alpha = 60)
        Wear.scuffs(c, inner, 3, 3, d, alpha = 16, colour = Color.rgb(0xC8, 0xC0, 0xB0))
        c.restore()
        c.drawRoundRect(inner, 8 * d, 8 * d, rim)
    }
}

/**
 * Narrow chrome plate of big, well-used rocker switches (SOUND, MONITOR, GPS), each
 * with a jewel lamp, plus the MOOD LIGHT knob in the first of the two round sockets.
 * The rockers and the knob are real toggles.
 */
class TonePlateView(ctx: Context) : CabinetView(ctx), MoodGlow {
    var switches: List<Pair<String, Boolean>> = emptyList()
        set(v) { field = v; invalidate() }
    var onToggle: ((Int, Boolean) -> Unit)? = null
    var mood = false
        set(v) { field = v; invalidate() }
    var onMood: ((Boolean) -> Unit)? = null

    private val label = paint(Radio.INK) { typeface = Radio.SERIF; textSize = 9f * t; letterSpacing = 0.12f; textAlign = Paint.Align.CENTER }
    private val tiny = paint(Color.rgb(0x2A, 0x28, 0x24)) { typeface = Radio.SERIF; textSize = 5f * t; textAlign = Paint.Align.CENTER }
    private val bezel = paint()
    private val slotPaint = paint(Color.rgb(0x0E, 0x0C, 0x0A))
    private val rocker = paint()
    private val thumb = paint()
    private val jewel = paint()
    private val jewelRim = paint(Color.rgb(0x5A, 0x58, 0x52)) { style = Paint.Style.STROKE; strokeWidth = 1.2f * d }
    private val knob = paint()
    private val ridge = paint(Color.argb(0xAA, 0, 0, 0)) { strokeWidth = 1f * d }
    private val socket = paint()
    private val hole = paint(Color.rgb(0x0C, 0x0B, 0x0A))
    private val pin = paint(Color.rgb(0x6C, 0x6A, 0x64))
    private val r = RectF()

    private val rowH get() = 9 * t + 34 * d
    private fun rowTop(i: Int) = 14 * d + i * rowH
    private val moodY get() = rowTop(switches.size) + 8 * d + 5 * t + 17 * d
    private val moodR get() = 15 * d

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        r.set(0f, 0f, w, h)
        Hardware.plate(c, r, 4 * d, d, seed = 11)
        Hardware.screw(c, w / 2, 7 * d, d)
        for ((i, s) in switches.withIndex()) rockerSwitch(c, w, rowTop(i), s.first, s.second, i)
        moodKnob(c, w / 2, moodY)
        // The other socket stays empty, as on the real set.
        val sy = moodY + moodR + 5 * t + 26 * d
        if (sy + 10 * d < h) {
            val cx = w / 2
            socket.shader = LinearGradient(cx - 10 * d, sy - 10 * d, cx + 10 * d, sy + 10 * d,
                Color.rgb(0xE0, 0xDE, 0xD6), Color.rgb(0x6A, 0x68, 0x62), Shader.TileMode.CLAMP)
            c.drawCircle(cx, sy, 10 * d, socket)
            c.drawCircle(cx, sy, 6 * d, hole)
            c.drawCircle(cx, sy, 2.2f * d, pin)
        }
    }

    /** A big bakelite rocker in a chrome escutcheon, with a jewel lamp above it. */
    private fun rockerSwitch(c: Canvas, w: Float, top: Float, name: String, on: Boolean, i: Int) {
        val cx = w / 2
        val base = top + 9 * t
        fitText(c, name, cx, base, label, w - 10 * d)
        Wear.rub(c, RectF(cx - w * 0.3f, base - 9 * t, cx + w * 0.3f, base), 40 + i, d, Color.rgb(0xC8, 0xC5, 0xBA), 2)
        // Jewel lamp, lit when the switch is on.
        val jy = base + 7 * d
        jewel.shader = RadialGradient(cx - 0.8f * d, jy - 0.8f * d, 4 * d,
            if (on) intArrayOf(Color.WHITE, Mood.LAMP, Color.rgb(0xB0, 0x50, 0x10)) else intArrayOf(Color.rgb(0x70, 0x50, 0x38), Color.rgb(0x40, 0x28, 0x18), Color.rgb(0x20, 0x14, 0x0C)),
            null, Shader.TileMode.CLAMP)
        c.drawCircle(cx, jy, 3.2f * d, jewel); c.drawCircle(cx, jy, 3.2f * d, jewelRim)
        // Chrome escutcheon with two screws.
        val ey = jy + 6 * d
        r.set(cx - 24 * d, ey, cx + 24 * d, ey + 20 * d)
        bezel.shader = LinearGradient(r.left, r.top, r.right, r.bottom, intArrayOf(Color.rgb(0xEC, 0xEA, 0xE2), Color.rgb(0x80, 0x7E, 0x76), Color.rgb(0xC4, 0xC2, 0xBA)),
            null, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 4 * d, 4 * d, bezel)
        Hardware.screw(c, r.left + 5 * d, r.centerY(), d, 1.8f)
        Hardware.screw(c, r.right - 5 * d, r.centerY(), d, 1.8f)
        tiny.color = Color.rgb(0x2A, 0x28, 0x24)
        c.drawText("I", cx - 13 * d, r.top - 1.5f * d, tiny); c.drawText("O", cx + 13 * d, r.top - 1.5f * d, tiny)
        // Slot and paddle; the paddle tips towards whichever side is pressed.
        val slot = RectF(cx - 14 * d, r.top + 3 * d, cx + 14 * d, r.bottom - 3 * d)
        c.drawRoundRect(slot, 2 * d, 2 * d, slotPaint)
        val pad = RectF(slot.left + 1.5f * d, slot.top + 1.5f * d, slot.right - 1.5f * d, slot.bottom - 1.5f * d)
        val mid = pad.centerX()
        // Pressed half darker and lower, raised half catching the light.
        rocker.shader = LinearGradient(pad.left, 0f, pad.right, 0f,
            if (on) intArrayOf(Color.rgb(0x0A, 0x07, 0x05), Color.rgb(0x16, 0x10, 0x0C), Color.rgb(0x4A, 0x3A, 0x2E), Color.rgb(0x22, 0x18, 0x12))
            else intArrayOf(Color.rgb(0x22, 0x18, 0x12), Color.rgb(0x4A, 0x3A, 0x2E), Color.rgb(0x16, 0x10, 0x0C), Color.rgb(0x0A, 0x07, 0x05)),
            floatArrayOf(0f, 0.48f, 0.52f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(pad, 2 * d, 2 * d, rocker)
        // Shiny thumb-worn patch on the raised half.
        val tx = if (on) mid + pad.width() * 0.22f else mid - pad.width() * 0.22f
        thumb.shader = RadialGradient(tx, pad.centerY(), pad.height() * 0.6f, Color.argb(60, 0xFF, 0xE8, 0xD0), Color.argb(0, 0xFF, 0xE8, 0xD0), Shader.TileMode.CLAMP)
        c.drawCircle(tx, pad.centerY(), pad.height() * 0.6f, thumb)
        c.save(); c.clipRect(r)
        Wear.scratches(c, r, 70 + i, 6, d, alpha = 50)
        c.restore()
    }

    /** Bakelite knob with a pointer and a jewel; turned to ON, the room light goes down. */
    private fun moodKnob(c: Canvas, x: Float, y: Float) {
        val top = y - moodR - 8 * d - 5 * t
        fitText(c, "MOOD LIGHT", x, top + 4 * t, label.apply { textSize = 7f * t }, width - 8 * d)
        label.textSize = 9f * t
        tiny.color = Color.rgb(0x2A, 0x28, 0x24)
        c.drawText("OFF", x - moodR - 5 * d, y + moodR + 6 * d, tiny)
        c.drawText("ON", x + moodR + 5 * d, y + moodR + 6 * d, tiny)
        socket.shader = LinearGradient(x - moodR * 1.25f, y - moodR * 1.25f, x + moodR * 1.25f, y + moodR * 1.25f,
            Color.rgb(0xE0, 0xDE, 0xD6), Color.rgb(0x6A, 0x68, 0x62), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, moodR * 1.2f, socket)
        knob.shader = RadialGradient(x - moodR * 0.3f, y - moodR * 0.35f, moodR * 1.3f, intArrayOf(Color.rgb(0x5A, 0x40, 0x2E), Color.rgb(0x2A, 0x1C, 0x14), Color.rgb(0x0E, 0x08, 0x05)),
            null, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, moodR, knob)
        for (k in 0 until 24) {   // knurled edge
            val a = k / 24.0 * Math.PI * 2
            c.drawLine(x + moodR * 0.82f * kotlin.math.cos(a).toFloat(), y + moodR * 0.82f * kotlin.math.sin(a).toFloat(),
                x + moodR * kotlin.math.cos(a).toFloat(), y + moodR * kotlin.math.sin(a).toFloat(), ridge)
        }
        // Pointer: left for OFF, right for ON.
        val a = Math.toRadians(if (mood) 225.0 + 90 else 225.0)
        val px = kotlin.math.cos(a).toFloat(); val py = kotlin.math.sin(a).toFloat()
        ridge.color = Color.rgb(0xE8, 0xDC, 0xC0); ridge.strokeWidth = 2f * d
        c.drawLine(x + px * moodR * 0.25f, y + py * moodR * 0.25f, x + px * moodR * 0.8f, y + py * moodR * 0.8f, ridge)
        ridge.color = Color.argb(0xAA, 0, 0, 0); ridge.strokeWidth = 1f * d
        jewel.shader = RadialGradient(x, y, 3.5f * d, if (mood) intArrayOf(Color.WHITE, Mood.LAMP, Color.rgb(0xB0, 0x50, 0x10))
            else intArrayOf(Color.rgb(0x70, 0x50, 0x38), Color.rgb(0x40, 0x28, 0x18), Color.rgb(0x20, 0x14, 0x0C)), null, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, 3f * d, jewel)
    }

    override fun glows(): List<Glow> {
        val out = ArrayList<Glow>()
        val w = width.toFloat()
        for ((i, s) in switches.withIndex()) if (s.second) {
            val jy = rowTop(i) + 9 * t + 7 * d
            out += Glow(RectF(w / 2 - 6 * d, jy - 6 * d, w / 2 + 6 * d, jy + 6 * d), Mood.LAMP, 0.9f)
        }
        if (mood) out += Glow(RectF(w / 2 - 8 * d, moodY - 8 * d, w / 2 + 8 * d, moodY + 8 * d), Mood.LAMP, 1f)
        return out
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN) return true
        if (e.action == MotionEvent.ACTION_UP) {
            if (kotlin.math.hypot(e.x - width / 2f, e.y - moodY) < moodR * 1.6f) {
                mood = !mood; onMood?.invoke(mood)
            } else {
                val i = ((e.y - rowTop(0)) / rowH).toInt()
                if (i in switches.indices && e.y >= rowTop(0)) {
                    val on = !switches[i].second
                    switches = switches.toMutableList().also { it[i] = it[i].first to on }
                    onToggle?.invoke(i, on)
                }
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
class DialView(ctx: Context) : CabinetView(ctx), MoodGlow {
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
    private val num = paint { typeface = Radio.SERIF; textSize = 7.5f * t; textAlign = Paint.Align.CENTER }
    private val tag = paint { typeface = Radio.SERIF; textSize = 7.5f * t; textAlign = Paint.Align.CENTER }
    private val mc = paint { typeface = Radio.SERIF; textSize = 6 * t; textAlign = Paint.Align.CENTER }
    private val cond = paint { typeface = Radio.SERIF; textSize = 8.5f * t; letterSpacing = 0.12f; textAlign = Paint.Align.CENTER }
    private val shoe = paint()
    private val shoeInk = paint(Radio.INK) { typeface = Radio.SERIF; textSize = 7.5f * t; textAlign = Paint.Align.CENTER }
    private val shoeTick = paint(Radio.INK) { strokeWidth = 0.8f * d }
    private val mega = paint { typeface = Radio.SERIF; textSize = 6.5f * t; letterSpacing = 0.25f; textAlign = Paint.Align.CENTER }
    private val script = paint { typeface = Radio.SERIF_ITALIC; textAlign = Paint.Align.CENTER }
    private val small = paint { typeface = Typeface.SERIF; textSize = 5.5f * t; letterSpacing = 0.15f; textAlign = Paint.Align.CENTER }
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
        // Chrome bezel worn through to the brass underneath, where hands have gripped it.
        c.save(); c.clipRect(outer)
        Wear.chips(c, outer, 21, 22, d, Color.argb(170, 0xB8, 0x8A, 0x40))
        Wear.scratches(c, outer, 21, 18, d, alpha = 60)
        c.restore()
        c.drawRoundRect(inner, 16 * d, 16 * d, glass)
        // Two amber dial lamps in the bezel's top corners.
        Hardware.jewel(c, outer.left + 10 * d, outer.top + 10 * d, 3.6f * d, DIAL_LAMP, d)
        Hardware.jewel(c, outer.right - 10 * d, outer.top + 10 * d, 3.6f * d, DIAL_LAMP, d)
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
                if (t != null) {
                    val tr = rr + 4 * d + num.textSize * 0.55f
                    c.drawText(t, px + tr * cosf(a), py + tr * sinf(a) + num.textSize * 0.36f, num)
                }
            }
            val a = ang(0f)
            val lr = rr + 4 * d + tag.textSize * 0.6f
            c.drawText(label, px + lr * cosf(a), py + lr * sinf(a) + tag.textSize * 0.36f, tag)
            if (unit) {
                val b = ang(1f); val mr = rr + 5 * d + mc.textSize
                c.drawText("M.C.", px + mr * cosf(b), py + mr * sinf(b) + mc.textSize * 0.36f, mc)
            }
        }
        val lo = ln(1.8); val hi = ln(32.0)
        scale(0.13f, "HF", HF.map { (l, f) -> l to ((ln(f) - lo) / (hi - lo)).toFloat() }, 0, false)
        scale(0.185f, "6M", (50..54).map { (if (it == 51 || it == 53) "$it" else null) to (it - 50) / 4f }, 16, false)
        scale(0.24f, "2M", (0..4).map { (if (it == 2) "145" else null) to it / 4f }, 20, false)
        // 70cm numbers sit off-centre so they never stack on 2m's "145".
        scale(0.295f, "70", listOf(null to 0f, "432" to 0.2f, null to 0.5f, "438" to 0.8f, null to 1f), 10, true)

        // Condition scale around the top; the word under the needle lights up.
        val rc = s * 0.405f
        for ((i, name) in listOf("QUIET", "HEADS-UP", "OPEN").withIndex()) {
            val t = levelPos(i); val a = ang(t)
            cond.color = if (kotlin.math.abs(needle - t) < 0.07f) Radio.mix(cream, Radio.LAMP_ALERT, glow * 0.8f) else cream
            c.drawText(name, px + rc * cosf(a), py + rc * sinf(a) + cond.textSize * 0.36f, cond)
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
            c.drawText(m, px + rm * cosf(a), py + rm * sinf(a) + shoeInk.textSize * 0.36f, shoeInk)
        }
        c.drawText("MEGACYCLES", px, py + r2 + 3 * d + mega.textSize, mega)
        // Script nameplates inside the horseshoe, kept clear of its inner edge.
        script.textSize = 9.5f * t
        val sy = py + s * 0.14f
        fitText(c, "PropWatch", px - s * 0.135f, sy, script, s * 0.24f)
        fitText(c, "DX Receiver", px + s * 0.135f, sy, script, s * 0.24f)
        fitText(c, "HF · VHF · UHF", px - s * 0.125f, sy + small.textSize * 1.5f, small, s * 0.16f)
        fitText(c, "PROPAGATION", px + s * 0.125f, sy + small.textSize * 1.5f, small, s * 0.16f)

        // Needle (with a short tail) and the pivot.
        val a = ang(needle)
        c.drawLine(px - s * 0.07f * cosf(a), py - s * 0.07f * sinf(a), px + s * 0.385f * cosf(a), py + s * 0.385f * sinf(a), needlePaint)
        hub.shader = RadialGradient(px - 2 * d, py - 2 * d, 12 * d, Color.rgb(0xF6, 0xF6, 0xF2), Radio.CHROME_DARK, Shader.TileMode.CLAMP)
        c.drawCircle(px, py, 9 * d, hub)
        c.drawCircle(px, py, 6 * d, hubRing)
        c.drawCircle(px, py, 2.5f * d, hubCap)
        c.restore()
        c.drawRoundRect(inner, 16 * d, 16 * d, glare)
        // Old glass: faintly yellowed, dusty, with a couple of long scratches.
        c.save(); c.clipRect(inner)
        fill.color = Color.argb(18, 0xD8, 0xB0, 0x50); c.drawRect(inner, fill)
        if (Mood.on) {   // the dial lamp turned up in the dark: a warm wash over the glass
            fill.color = Color.argb(40, 0xFF, 0x9A, 0x3C); c.drawRect(inner, fill)
        }
        Wear.dust(c, inner, 22, 160, d, alpha = 70)
        Wear.scratches(c, inner, 23, 3, d, alpha = 45)
        c.restore()
    }

    override fun glows(): List<Glow> = listOf(Glow(RectF(inner), Mood.AMBER, 1f, screen = true, radius = 16 * d),
        Glow(RectF(outer.left + 4 * d, outer.top + 4 * d, outer.left + 16 * d, outer.top + 16 * d), DIAL_LAMP, 0.9f),
        Glow(RectF(outer.right - 16 * d, outer.top + 4 * d, outer.right - 4 * d, outer.top + 16 * d), DIAL_LAMP, 0.9f))
    private val DIAL_LAMP = Color.rgb(0xFF, 0xA8, 0x3A)
    private val fill = paint()

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
class BandPlateView(ctx: Context) : CabinetView(ctx), MoodGlow {
    private val PILOT_RED = Color.rgb(0xE8, 0x30, 0x22)
    /** label, status word, lamp colour (or null for unlit) */
    data class Row(val label: String, val status: String, val lamp: Int?)

    var rows: List<Row> = emptyList()
        set(v) { field = v; requestLayout(); invalidate() }
    var selected = 0
        set(v) { field = v; invalidate() }
    var onSelect: ((Int) -> Unit)? = null

    private val head = paint(Radio.INK) { typeface = Radio.SERIF; textSize = 8 * t; letterSpacing = 0.25f; textAlign = Paint.Align.CENTER }
    private val label = paint(Radio.INK) { typeface = Radio.SERIF; textSize = 9.5f * t; letterSpacing = 0.08f; textAlign = Paint.Align.RIGHT }
    private val status = paint(Radio.INK) { typeface = Radio.SERIF; textSize = 9.5f * t; letterSpacing = 0.1f; textAlign = Paint.Align.LEFT }
    private val rule = paint(Color.rgb(0x2A, 0x28, 0x24)) { strokeWidth = 1.1f * d }
    private val well = paint(Color.argb(0x59, 0, 0, 0))
    private val btn = paint()
    private val shine = paint(Color.argb(0x2E, 255, 255, 255))
    private val lampPaint = paint()
    private val lampRim = paint(Color.rgb(0x2A, 0x28, 0x24)) { style = Paint.Style.STROKE; strokeWidth = 0.8f * d }
    private val r = RectF()

    private val rowH get() = maxOf(30 * d, 9.5f * t + 20 * d)
    private val top get() = 8 * t + 16 * d

    override fun onMeasure(w: Int, h: Int) =
        setMeasuredDimension(MeasureSpec.getSize(w), (top + rows.size * rowH + 10 * d).toInt())

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        r.set(0f, 0f, w, h)
        Hardware.plate(c, r, 5 * d, d, seed = 31)
        val bx = w * 0.53f; val lineX = w * 0.64f
        fitText(c, "WAVEBAND", w * 0.28f, 8 * t + 7 * d, head, w * 0.42f)
        // A red pilot lamp by the heading: the set is on.
        Hardware.jewel(c, 20 * d, 8 * t + 7 * d - head.textSize * 0.36f, 3.4f * d, PILOT_RED, d)
        fitText(c, "CONDITIONS", (lineX + w) / 2, 8 * t + 7 * d, head, w - lineX - 10 * d)
        c.drawLine(lineX, top + 2 * d, lineX, top + rows.size * rowH - 6 * d, rule)
        for ((i, row) in rows.withIndex()) {
            val cy = top + i * rowH + rowH / 2
            val down = i == selected
            fitText(c, row.label, bx - 10 * d, cy + label.textSize * 0.36f, label, bx - 22 * d)
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
            fitText(c, row.status, lineX + 20 * d, cy + status.textSize * 0.36f, status, w - lineX - 28 * d)
        }
        for ((x, y) in listOf(8 * d to 8 * d, w - 8 * d to 8 * d, 8 * d to h - 8 * d, w - 8 * d to h - 8 * d)) Hardware.screw(c, x, y, d)
        // Lettering rubbed thin where fingers find the buttons.
        Wear.rub(c, RectF(0f, top, w * 0.5f, h), 32, d, Color.rgb(0xC8, 0xC5, 0xBA), 4)
    }

    override fun glows(): List<Glow> {
        val lineX = width * 0.64f
        val pilotY = 8 * t + 7 * d - 8 * t * 0.36f
        return listOf(Glow(RectF(14 * d, pilotY - 6 * d, 26 * d, pilotY + 6 * d), PILOT_RED, 0.9f)) + rows.withIndex().filter { it.value.lamp != null }.map { (i, row) ->
            val cy = top + i * rowH + rowH / 2
            Glow(RectF(lineX + 7 * d, cy - 6 * d, lineX + 19 * d, cy + 6 * d), row.lamp!!, 0.8f)
        }
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
 * The DX log: the last 24 hours of checks, shown behind a backlit glass screen in a
 * worn chrome bezel, glowing warm amber like the broadcast set's dial. Gold rules and
 * lettering, lit dots for each check, old yellowed glass with dust and scratches.
 */
class LogChartView(ctx: Context) : CabinetView(ctx), MoodGlow {
    var entries: List<Store.LogEntry> = emptyList()
        set(v) { field = v; invalidate() }

    private val bezel = paint()
    private val face = paint()
    private val bulb = paint()
    private val glare = paint()
    private val fill = paint()
    private val outer = RectF()
    private val inner = RectF()
    private val clip = Path()

    override fun glows(): List<Glow> = if (inner.isEmpty) emptyList() else listOf(
        Glow(RectF(inner), Mood.AMBER, 1.1f, screen = true, radius = 6 * d))

    private val fine = paint(Color.argb(0x50, 0xE8, 0xB8, 0x60)) { strokeWidth = 0.5f * d }
    private val bold = paint(Color.argb(0xB0, 0xE8, 0xC0, 0x70)) { strokeWidth = 0.9f * d }
    private val scaleText = paint(PALE_GOLD) { typeface = Radio.SERIF; textSize = 7 * t; textAlign = Paint.Align.CENTER }
    private val rowText = paint(PALE_GOLD) { typeface = Radio.SERIF; textSize = 9 * t; letterSpacing = 0.1f; textAlign = Paint.Align.CENTER }
    private val key = paint(PALE_GOLD) { typeface = Typeface.SERIF; textSize = 7 * t; letterSpacing = 0.08f; textAlign = Paint.Align.CENTER }
    private val diamond = paint(PALE_GOLD)
    private val dot = paint()
    private val halo = paint()
    private val quiet = paint(Color.argb(0xC0, 0xE8, 0xC0, 0x70)) { strokeWidth = 1.2f * d }
    private val fail = paint(Color.rgb(0xFF, 0x5A, 0x3A)) { strokeWidth = 1.3f * d }
    private val dpath = Path()

    private val rim get() = 8 * d
    private val pad get() = scaleText.measureText("NOW") / 2 + 4 * d
    private val head get() = 7 * t + 10 * d
    private val labW get() = 26 * t
    private val rowH get() = maxOf(22 * d, 14 * t)
    private val keyH get() = 7 * t * 2.6f + 6 * d

    override fun onMeasure(w: Int, h: Int) =
        setMeasuredDimension(MeasureSpec.getSize(w), (2 * rim + head + Bands.ALL.size * rowH + keyH).toInt())

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        outer.set(0f, 0f, w.toFloat(), h.toFloat())
        inner.set(rim, rim, w - rim, h - rim)
        bezel.shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(),
            intArrayOf(Color.rgb(0xF0, 0xF0, 0xEC), Radio.CHROME_DARK, Color.rgb(0xDA, 0xDA, 0xD4), Color.rgb(0x50, 0x50, 0x4C)),
            null, Shader.TileMode.CLAMP)
        // Backlit from below, like the broadcast set's dial with its lamp full on.
        face.shader = RadialGradient(inner.centerX(), inner.bottom, maxOf(inner.width() * 0.75f, inner.height() * 1.3f),
            intArrayOf(Color.rgb(0xA8, 0x70, 0x2C), Color.rgb(0x58, 0x36, 0x14), Color.rgb(0x1E, 0x12, 0x08)),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        clip.reset(); clip.addRoundRect(inner, 6 * d, 6 * d, Path.Direction.CW)
        glare.shader = LinearGradient(inner.left, inner.top, inner.right, inner.bottom,
            intArrayOf(Color.argb(0x1C, 255, 255, 255), Color.argb(0x08, 255, 255, 255), Color.argb(0, 255, 255, 255), Color.argb(0, 255, 255, 255)),
            floatArrayOf(0f, 0.3f, 0.31f, 1f), Shader.TileMode.CLAMP)
    }

    private fun drawBulb(c: Canvas, x: Float, y: Float, r: Float, a: Int) {
        bulb.shader = RadialGradient(x, y, r, Color.argb(a, 0xFF, 0xD8, 0x90), Color.argb(0, 0xFF, 0xC0, 0x60), Shader.TileMode.CLAMP)
        c.drawRect(inner, bulb)
    }

    override fun onDraw(c: Canvas) {
        c.drawRoundRect(outer, 10 * d, 10 * d, bezel)
        // Chrome worn through to brass where it's been handled.
        c.save(); c.clipRect(outer)
        Wear.chips(c, outer, 31, 14, d, Color.argb(170, 0xB8, 0x8A, 0x40))
        Wear.scratches(c, outer, 32, 10, d, alpha = 60)
        c.restore()

        c.save()
        c.clipPath(clip)
        c.drawRect(inner, face)
        // Two bulbs behind the glass, low down, each throwing a pool of light.
        val bulbA = if (Mood.on) 150 else 120
        drawBulb(c, inner.left + inner.width() * 0.25f, inner.bottom, inner.height() * 0.8f, bulbA)
        drawBulb(c, inner.left + inner.width() * 0.75f, inner.bottom, inner.height() * 0.8f, bulbA)

        val gx = inner.left + labW; val gw = inner.right - pad - gx
        val gy = inner.top + head; val gh = Bands.ALL.size * rowH
        for (k in 0..48) c.drawLine(gx + gw * k / 48, gy, gx + gw * k / 48, gy + gh, fine)
        for (k in 0..Bands.ALL.size * 2) c.drawLine(gx, gy + gh * k / (Bands.ALL.size * 2), gx + gw, gy + gh * k / (Bands.ALL.size * 2), fine)
        for (k in 0..24 step 4) c.drawLine(gx + gw * k / 24, gy, gx + gw * k / 24, gy + gh, bold)
        for (k in 0..Bands.ALL.size) c.drawLine(gx, gy + rowH * k, gx + gw, gy + rowH * k, bold)
        for (k in 0..24) {
            val x = gx + gw * k / 24
            val base = gy - 6 * d
            if (k % 4 == 0) c.drawText(if (k == 24) "NOW" else "${k - 24}", x, base, scaleText)
            else if (k % 2 == 0) {
                val m = base - scaleText.textSize * 0.36f
                dpath.reset(); dpath.moveTo(x, m - 2.5f * d); dpath.lineTo(x + 2 * d, m); dpath.lineTo(x, m + 2.5f * d); dpath.lineTo(x - 2 * d, m); dpath.close()
                c.drawPath(dpath, diamond)
            }
        }
        for ((i, b) in Bands.ALL.withIndex())
            fitText(c, b.second, inner.left + labW / 2, gy + rowH * i + rowH / 2 + rowText.textSize * 0.36f, rowText, labW - 6 * d)

        val now = System.currentTimeMillis()
        for (e in entries) {
            val ago = (now - e.timeMs) / 3_600_000f
            if (ago > 24f || ago < 0f) continue
            val x = gx + gw * (24 - ago) / 24
            for ((i, b) in Bands.ALL.withIndex()) {
                val y = gy + rowH * i + rowH / 2
                when (e.levels[b.first] ?: 0) {
                    2 -> lit(c, x, y, 4.2f * d, OPEN_LAMP)
                    1 -> lit(c, x, y, 3.6f * d, HEADS_LAMP)
                    0 -> c.drawLine(x - 2.5f * d, y, x + 2.5f * d, y, quiet)
                    else -> {
                        c.drawLine(x - 2.5f * d, y - 2.5f * d, x + 2.5f * d, y + 2.5f * d, fail)
                        c.drawLine(x + 2.5f * d, y - 2.5f * d, x - 2.5f * d, y + 2.5f * d, fail)
                    }
                }
            }
        }

        val k1 = gy + gh + 3 * d + key.textSize
        fitText(c, if (entries.isEmpty()) "HOURS AGO · CHECKS WILL BE LOGGED HERE" else "HOURS AGO", inner.centerX(), k1, key, inner.width() - 8 * d)
        if (entries.isNotEmpty()) fitText(c, "● OPEN   ● HEADS-UP   – QUIET   × NO DATA", inner.centerX(), k1 + key.textSize * 1.4f, key, inner.width() - 8 * d)

        // Old glass: yellowed, a glare across the top corner, dust, a few scratches, grimy edges.
        fill.color = Color.argb(22, 0xD8, 0xB0, 0x50); c.drawRect(inner, fill)
        if (Mood.on) { fill.color = Color.argb(36, 0xFF, 0x9A, 0x3C); c.drawRect(inner, fill) }
        c.drawRect(inner, glare)
        Wear.grime(c, inner, d, depth = 10f, alpha = 70)
        Wear.dust(c, inner, 33, 140, d, alpha = 65)
        Wear.scratches(c, inner, 34, 3, d, alpha = 40)
        c.restore()
    }

    /** A check mark that glows on the screen: a soft halo and a bright core. */
    private fun lit(c: Canvas, x: Float, y: Float, r: Float, col: Int) {
        halo.shader = RadialGradient(x, y, r * 2.4f, Color.argb(0x90, Color.red(col), Color.green(col), Color.blue(col)),
            Color.argb(0, Color.red(col), Color.green(col), Color.blue(col)), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r * 2.4f, halo)
        dot.color = col; c.drawCircle(x, y, r, dot)
        dot.color = Color.argb(0xB0, 255, 0xF4, 0xD8); c.drawCircle(x - r * 0.25f, y - r * 0.25f, r * 0.4f, dot)
    }

    private companion object {
        val PALE_GOLD = Color.rgb(0xFF, 0xE2, 0xA6)
        val OPEN_LAMP = Color.rgb(0x6C, 0xF0, 0x8A)
        val HEADS_LAMP = Color.rgb(0xFF, 0xB0, 0x40)
    }
}

/**
 * The station log in the lid: a cream-on-black world chart with the station pinned
 * on it, and the reading below. Dims and warms up when retuned; a cursor sweeps the
 * chart while a fix is coming in.
 */
class StationLogView(ctx: Context) : CabinetView(ctx), MoodGlow {
    private var glow = 1f
    private var sweep = 0f
    private var pulse = 0f
    private var locating = false
    private var lines: List<String> = emptyList()   // city, country, lat/lon, locator, footnote
    private var message = "TUNING IN…"
    private var pinLat = Double.NaN
    private var pinLon = Double.NaN

    private val ribbon = paint(Radio.CREAM)
    private val ribbonText = paint(Radio.INK) { typeface = Radio.SERIF; textSize = 10 * t; letterSpacing = 0.45f; textAlign = Paint.Align.CENTER }
    private val sea = paint(Color.rgb(0x0B, 0x0A, 0x09))
    private val grid = paint { strokeWidth = 0.6f * d }
    private val land = paint()
    private val coast = paint { style = Paint.Style.STROKE; strokeWidth = 0.7f * d }
    private val frame = paint { style = Paint.Style.STROKE; strokeWidth = 1f * d }
    private val pin = paint(Color.rgb(0xE0, 0x3A, 0x2E))
    private val halo = paint { style = Paint.Style.STROKE; strokeWidth = 1.5f * d }
    private val sweepPaint = paint(Radio.LAMP_ALERT) { strokeWidth = 1.6f * d }
    private val big = paint { typeface = Radio.SERIF; textSize = 20 * t; letterSpacing = 0.05f; textAlign = Paint.Align.CENTER }
    private val mid = paint { typeface = Radio.SERIF; textSize = 12.5f * t; letterSpacing = 0.25f; textAlign = Paint.Align.CENTER }
    private val mono = paint { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textSize = 12.5f * t; textAlign = Paint.Align.CENTER }
    private val note = paint { typeface = Typeface.MONOSPACE; textSize = 9 * t; letterSpacing = 0.05f; textAlign = Paint.Align.CENTER }
    private val mapRect = RectF()
    private val crease = paint { strokeWidth = 0.8f * d }

    private val LOG_GREEN = Color.rgb(0x5A, 0xE0, 0x7A)

    /** The red station pin and the nameplate's green lamp glow in the dark. */
    override fun glows(): List<Glow> {
        val out = ArrayList<Glow>()
        out += Glow(RectF(6 * d, ribbonH / 2 - 6 * d, 18 * d, ribbonH / 2 + 6 * d), LOG_GREEN, 0.8f)
        if (!pinLat.isNaN() && !locating) {
            val x = mapX(pinLon); val y = mapY(pinLat)
            out += Glow(RectF(x - 5 * d, y - 5 * d, x + 5 * d, y + 5 * d), Color.rgb(0xFF, 0x50, 0x30), 0.7f)
        }
        return out
    }
    private val shapes = ArrayList<Path>()

    private val ribbonH get() = 10 * t + 9 * d
    private val textH get() = 96 * t + 8 * d

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
        while (p.measureText(t) > width - 16 * d && p.textSize > 9 * d) p.textSize *= 0.93f
        c.drawText(t, width / 2f, y, p)
        p.textSize = size
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val cream = Radio.mix(Color.rgb(0x40, 0x3B, 0x33), Radio.CREAM, glow)
        // Title ribbon, like the lid's nameplate band.
        c.drawRoundRect(0f, 0f, w, ribbonH, 2 * d, 2 * d, ribbon)
        c.drawText("STATION LOG", w / 2, ribbonH / 2 + ribbonText.textSize * 0.36f, ribbonText)
        Hardware.jewel(c, 12 * d, ribbonH / 2, 3.4f * d, LOG_GREEN, d)   // a green lamp on the nameplate

        // World chart: cream continents on black.
        sea.shader = null; sea.color = Color.rgb(0x0B, 0x0A, 0x09)
        c.drawRect(mapRect, sea)
        grid.color = Radio.mix(Color.rgb(0x1E, 0x1B, 0x17), Color.rgb(0x3A, 0x34, 0x2A), glow)
        land.color = Radio.mix(Color.rgb(0x3A, 0x35, 0x2C), Color.rgb(0xD8, 0xCB, 0xA6), glow)
        coast.color = Radio.mix(Color.rgb(0x50, 0x48, 0x3A), Radio.CREAM, glow)
        for (lon in -150..150 step 30) c.drawLine(mapX(lon.toDouble()), mapRect.top, mapX(lon.toDouble()), mapRect.bottom, grid)
        for (lat in -30..60 step 30) c.drawLine(mapRect.left, mapY(lat.toDouble()), mapRect.right, mapY(lat.toDouble()), grid)
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
        // A chart that's been folded and unfolded many times: crease lines and grubby edges.
        c.save(); c.clipRect(mapRect)
        crease.color = Color.argb(36, 255, 255, 255)
        for (f in listOf(1f / 3, 2f / 3)) c.drawLine(mapRect.left + mapRect.width() * f, mapRect.top, mapRect.left + mapRect.width() * f, mapRect.bottom, crease)
        c.drawLine(mapRect.left, mapRect.centerY(), mapRect.right, mapRect.centerY(), crease)
        Wear.grime(c, mapRect, d, depth = 10f, alpha = 50)
        c.restore()
        frame.color = cream
        c.drawRect(mapRect, frame)

        // The reading.
        listOf(big, mid, mono, note).forEach { it.color = cream }
        val top = mapRect.bottom + 6 * d
        if (locating || lines.isEmpty()) {
            fit(c, message, top + textH * 0.42f, mid)
        } else {
            fit(c, lines[0], top + 20 * t, big)
            fit(c, lines[1], top + 37.5f * t, mid)
            fit(c, lines[2], top + 59 * t, mono)
            fit(c, lines[3], top + 76.5f * t, mono)
            fit(c, lines[4], top + 91 * t, note)
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
