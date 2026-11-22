package uk.dx.propwatch

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.MotionEvent
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.cos
import kotlin.math.sin

/**
 * The little broadcast set tucked under the speaker, in the style of the 1940s portable
 * shortwave sets: a black dial with gold arcs in a chrome bezel, its lamp warming up as
 * a station tunes in, and an etched chrome plate of black station push-buttons.
 *
 * Tap a station to play it; tap the lit one again to switch off. [onLongPress] fires for
 * a long press (used to change the custom station).
 */
class BroadcastSetView(ctx: Context) : CabinetView(ctx), MoodGlow {
    var stations: List<Station> = emptyList()
        set(v) { field = v; invalidate() }
    var onPress: ((Int) -> Unit)? = null
    var onLongPress: ((Int) -> Unit)? = null

    private var state = StreamService.State.OFF
    private var playing = -1
    private var needle = 0f        // 0..1 across the dial
    private var lampGlow = 0.12f   // dial lamp: dim when off, flickering while tuning, full when playing
    private var flicker: ValueAnimator? = null
    private var downAt = 0L
    private var downIndex = -1

    private val bezel = paint()
    private val face = paint()
    private val arc = paint { style = Paint.Style.STROKE; strokeWidth = 1.1f * d }
    private val tick = paint { strokeWidth = 0.9f * d }
    private val mark = paint { typeface = Radio.SERIF; textSize = 7f * t; textAlign = Paint.Align.CENTER }
    private val status = paint { typeface = Radio.SERIF; textSize = 7f * t; letterSpacing = 0.08f; textAlign = Paint.Align.CENTER }
    private val small = paint { typeface = Radio.SERIF; textSize = 5.5f * t; letterSpacing = 0.25f; textAlign = Paint.Align.CENTER }
    private val needlePaint = paint(Color.rgb(0xF4, 0xEC, 0xD8)) { strokeWidth = 1.6f * d; strokeCap = Paint.Cap.ROUND }
    private val hub = paint()
    private val card = paint()
    private val label = paint(Radio.INK) { typeface = Radio.SERIF; textSize = 7.5f * t; letterSpacing = 0.06f; textAlign = Paint.Align.CENTER }
    private val btn = paint()
    private val btnEdge = paint(Color.BLACK) { style = Paint.Style.STROKE; strokeWidth = 1f * d }
    private val well = paint(Color.argb(0x59, 0, 0, 0))
    private val shine = paint(Color.argb(0x2E, 255, 255, 255))
    private val jewel = paint()
    private val dial = RectF(); private val inner = RectF(); private val plate = RectF(); private val r = RectF()

    private val positions = floatArrayOf(0.18f, 0.5f, 0.82f)

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        dial.set(0f, 0f, w.toFloat(), h * 0.56f)
        inner.set(dial.left + 5 * d, dial.top + 5 * d, dial.right - 5 * d, dial.bottom - 5 * d)
        plate.set(0f, dial.bottom + 6 * d, w.toFloat(), h.toFloat())
    }

    override fun onDraw(c: Canvas) {
        drawDial(c)
        drawPlate(c)
    }

    private fun drawDial(c: Canvas) {
        bezel.shader = LinearGradient(dial.left, dial.top, dial.right, dial.bottom,
            intArrayOf(Color.rgb(0xF0, 0xF0, 0xEC), Radio.CHROME_DARK, Color.rgb(0xDA, 0xDA, 0xD4), Color.rgb(0x50, 0x50, 0x4C)),
            null, Shader.TileMode.CLAMP)
        c.drawRoundRect(dial, 9 * d, 9 * d, bezel)
        c.save(); c.clipRect(dial); Wear.chips(c, dial, 91, 8, d, Color.argb(170, 0xB8, 0x8A, 0x40)); c.restore()
        val g = lampGlow
        face.shader = RadialGradient(inner.centerX(), inner.bottom, inner.height() * 1.3f,
            Radio.mix(Color.rgb(0x10, 0x0E, 0x0C), Color.rgb(0x4A, 0x34, 0x18), g), Color.rgb(0x08, 0x08, 0x08), Shader.TileMode.CLAMP)
        c.drawRoundRect(inner, 6 * d, 6 * d, face)

        c.save(); c.clipRect(inner)
        val gold = Radio.mix(Color.rgb(0x5A, 0x48, 0x2A), Radio.GOLD, 0.35f + 0.65f * g)
        arc.color = gold; tick.color = gold; mark.color = gold; small.color = gold
        val px = inner.centerX(); val py = inner.bottom + inner.height() * 0.15f
        val a0 = -150f; val a1 = -30f
        fun ang(v: Float) = Math.toRadians((a0 + (a1 - a0) * v).toDouble())
        val radii = listOf(0.55f, 0.72f, 0.89f).map { it * inner.height() }
        for ((k, rr) in radii.withIndex()) {
            r.set(px - rr, py - rr, px + rr, py + rr)
            c.drawArc(r, a0, a1 - a0, false, arc)
            val n = if (k == 2) 20 else 10
            for (i in 0..n) {
                val a = ang(i / n.toFloat()); val len = (if (i % 5 == 0) 3.5f else 2f) * d
                c.drawLine(px + rr * cos(a).toFloat(), py + rr * sin(a).toFloat(), px + (rr - len) * cos(a).toFloat(), py + (rr - len) * sin(a).toFloat(), tick)
            }
        }
        // Station marks on the outer arc.
        val ro = radii.last()
        for ((i, v) in positions.withIndex()) {
            val a = ang(v); val tr = ro - 9 * d
            mark.color = if (i == playing && state != StreamService.State.OFF) Radio.mix(gold, Mood.LAMP, g) else gold
            c.drawText("${i + 1}", px + tr * cos(a).toFloat(), py + tr * sin(a).toFloat() + mark.textSize * 0.36f, mark)
        }
        c.drawText("BROADCAST", px, inner.top + small.textSize + 3 * d, small)
        // Needle (under the station card, so the name stays readable).
        val a = ang(needle)
        c.drawLine(px, py, px + ro * 1.02f * cos(a).toFloat(), py + ro * 1.02f * sin(a).toFloat(), needlePaint)
        // What the set is doing, in the middle of the dial.
        status.color = Radio.mix(Color.rgb(0x6A, 0x58, 0x3A), Radio.CREAM, 0.3f + 0.7f * g)
        val text = when (state) {
            StreamService.State.OFF -> "OFF"
            StreamService.State.TUNING -> "TUNING IN…"
            StreamService.State.ERROR -> "NO SIGNAL"
            StreamService.State.PLAYING -> stations.getOrNull(playing)?.name?.uppercase() ?: "ON THE AIR"
        }
        // On a dark card of its own so it reads clearly over the scales.
        val ty = inner.top + inner.height() * 0.62f
        val size = status.textSize
        while (status.measureText(text) > inner.width() * 0.8f && status.textSize > 8 * d) status.textSize *= 0.94f
        val tw = status.measureText(text)
        r.set(px - tw / 2 - 5 * d, ty - status.textSize, px + tw / 2 + 5 * d, ty + status.textSize * 0.35f)
        card.color = Color.argb(0xD8, 0x0A, 0x08, 0x06)
        c.drawRoundRect(r, 3 * d, 3 * d, card)
        c.drawText(text, px, ty, status)
        status.textSize = size
        c.restore()
        hub.shader = RadialGradient(px - d, inner.bottom - 2 * d, 6 * d, Color.rgb(0xF6, 0xF6, 0xF2), Radio.CHROME_DARK, Shader.TileMode.CLAMP)
        c.drawCircle(px, inner.bottom - 1 * d, 4 * d, hub)
        // Old glass: dust and a scratch.
        c.save(); c.clipRect(inner); Wear.dust(c, inner, 92, 60, d, alpha = 60); Wear.scratches(c, inner, 93, 2, d, alpha = 40); c.restore()
    }

    private fun drawPlate(c: Canvas) {
        Hardware.plate(c, plate, 4 * d, d, seed = 94)
        val colW = plate.width() / 3
        val bs = minOf(colW * 0.42f, plate.height() * 0.42f)
        for (i in 0 until 3) {
            val cx = plate.left + colW * (i + 0.5f)
            val down = i == playing && state != StreamService.State.OFF
            fitText(c, stations.getOrNull(i)?.label ?: "", cx, plate.top + 5 * d + label.textSize, label, colW - 4 * d)
            val by = plate.bottom - bs - 7 * d
            r.set(cx - bs / 2 - 2 * d, by - 1 * d, cx + bs / 2 + 2 * d, by + bs + 3 * d)
            c.drawRoundRect(r, 3 * d, 3 * d, well)
            r.set(cx - bs / 2, by, cx + bs / 2, by + bs)
            if (down) r.offset(0f, 1.5f * d)
            btn.shader = if (down) LinearGradient(0f, r.top, 0f, r.bottom, Color.rgb(0x03, 0x03, 0x03), Color.rgb(0x1E, 0x1C, 0x1A), Shader.TileMode.CLAMP)
                else LinearGradient(0f, r.top, 0f, r.bottom, intArrayOf(Color.rgb(0x4A, 0x47, 0x44), Color.rgb(0x15, 0x14, 0x13), Color.rgb(0x05, 0x05, 0x05)), null, Shader.TileMode.CLAMP)
            c.drawRoundRect(r, 3 * d, 3 * d, btn)
            c.drawRoundRect(r, 3 * d, 3 * d, btnEdge)
            if (!down) c.drawRoundRect(r.left + 3 * d, r.top + 2 * d, r.right - 3 * d, r.top + 5 * d, 2 * d, 2 * d, shine)
            // Jewel lamp in the pressed button.
            jewel.shader = RadialGradient(r.centerX(), r.centerY(), 3 * d,
                if (down) intArrayOf(Color.WHITE, Mood.LAMP, Color.rgb(0xB0, 0x50, 0x10))
                else intArrayOf(Color.rgb(0x50, 0x3A, 0x28), Color.rgb(0x30, 0x20, 0x14), Color.rgb(0x18, 0x10, 0x0A)),
                null, Shader.TileMode.CLAMP)
            c.drawCircle(r.centerX(), r.centerY(), 2.4f * d, jewel)
        }
        Wear.rub(c, RectF(plate.left, plate.top, plate.right, plate.top + 6 * d + label.textSize), 95, d, Color.rgb(0xC8, 0xC5, 0xBA), 2)
    }

    /** Follow the stream service: move the needle, warm or dim the lamp. */
    fun show(s: StreamService.State, index: Int) {
        val wasOff = state == StreamService.State.OFF || state == StreamService.State.ERROR
        state = s; playing = index
        flicker?.cancel(); flicker = null
        val target = if (index in 0..2 && s != StreamService.State.OFF) positions[index] else 0f
        if (isLaidOut && needle != target) animate(needle, target, if (wasOff) 900 else 500, 0, OvershootInterpolator(0.8f)) { needle = it }
        else needle = target
        when (s) {
            StreamService.State.TUNING -> {   // the valves warming up: an uneven, rising glow
                flicker = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 1400; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
                    addUpdateListener {
                        val v = it.animatedValue as Float
                        lampGlow = 0.35f + 0.25f * (0.5f + 0.5f * sin(v * 6.283f * 3).toFloat()) * (0.7f + 0.3f * sin(v * 6.283f * 7).toFloat())
                        invalidate()
                    }
                    start()
                }
            }
            StreamService.State.PLAYING -> animate(lampGlow, 1f, 600, 0) { lampGlow = it }
            else -> animate(lampGlow, 0.12f, 500, 0) { lampGlow = it }
        }
        invalidate()
    }

    override fun glows(): List<Glow> {
        if (state == StreamService.State.OFF || state == StreamService.State.ERROR) return emptyList()
        val out = arrayListOf(Glow(RectF(inner), Mood.AMBER, 0.6f + 0.4f * lampGlow, screen = true, radius = 6 * d))
        if (playing in 0..2) {
            val colW = plate.width() / 3
            val cx = plate.left + colW * (playing + 0.5f)
            val bs = minOf(colW * 0.42f, plate.height() * 0.42f)
            val cy = plate.bottom - bs / 2 - 7 * d
            out += Glow(RectF(cx - 6 * d, cy - 6 * d, cx + 6 * d, cy + 6 * d), Mood.LAMP, 0.9f)
        }
        return out
    }

    override fun onDetachedFromWindow() { flicker?.cancel(); super.onDetachedFromWindow() }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val i = if (e.y >= plate.top) ((e.x - plate.left) / (plate.width() / 3)).toInt().coerceIn(0, 2) else -1
        when (e.action) {
            MotionEvent.ACTION_DOWN -> { downAt = SystemClock.uptimeMillis(); downIndex = i; return true }
            MotionEvent.ACTION_UP -> {
                if (i >= 0 && i == downIndex) {
                    if (SystemClock.uptimeMillis() - downAt > 550) onLongPress?.invoke(i) else onPress?.invoke(i)
                }
                performClick()
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
