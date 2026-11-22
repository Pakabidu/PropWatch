package uk.dx.propwatch

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The WW2 aircraft wireless set: a black stove-enamel panel with chrome grab handles
 * and brass engraved plates, two square meters, a cream semicircular tuning dial with
 * colour-coded scales above a big knurled knob, a station-record card, colour-coded
 * lever switches and a band switch with coloured sectors. Original artwork; type and
 * serial markings are PropWatch's own.
 *
 * Working controls: the big knurled knob runs a check; the band switch picks the
 * waveband; the blue, red and yellow levers are SOUND, MONITOR and GPS. The red and
 * yellow knurled knobs are for show.
 */
class AircraftPanelView(ctx: Context) : CabinetView(ctx), FrontPanel, MoodGlow {
    override var mood = false
        set(v) { field = v; invalidate() }
    override var onMood: ((Boolean) -> Unit)? = null

    override var bands: List<String> = emptyList()
        set(v) { field = v; invalidate() }
    override var bandLamps: List<Int?> = emptyList()
        set(v) { field = v; invalidate() }
    override var selected = 0
        set(v) { field = v; animateSwitch() }
    override var toggles: List<Pair<String, Boolean>> = emptyList()
        set(v) { field = v; animateLevers() }
    override var onCheck: (() -> Unit)? = null
    override var onSelect: ((Int) -> Unit)? = null
    override var onToggle: ((Int, Boolean) -> Unit)? = null

    /** Lines for the station-record card, e.g. locator and build. */
    var record: List<String> = emptyList()
        set(v) { field = v; invalidate() }

    private var glow = 1f
    private var pointer = 0.15f
    private var sNeedle = 0.3f
    private var kpNeedle = 0f
    private var eye = 0.9f          // magic-eye shadow, radians half-angle: wide when quiet, closes when open
    private var switchAngle = 0f
    private val leverAngle = FloatArray(3) { -150f }
    private var knobSpin = 0f
    private var spinner: ValueAnimator? = null

    private val panel = paint()
    private val fill = paint()
    private val stroke = paint { style = Paint.Style.STROKE }
    private val text = paint(Radio.CREAM) { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textAlign = Paint.Align.CENTER }
    private val serif = paint(Radio.INK) { typeface = Radio.SERIF; textAlign = Paint.Align.CENTER }
    private val mono = paint(Radio.INK) { typeface = Typeface.MONOSPACE; textAlign = Paint.Align.LEFT }
    private val speck = paint(Color.argb(0x40, 0, 0, 0))
    private val path = Path()
    private val rect = RectF()

    // Layout.
    private val sMeter = RectF(); private val kpMeter = RectF()
    private var eyeX = 0f; private var eyeY = 0f
    private var cx = 0f; private var cy = 0f; private var dialR = 0f
    private var knobX = 0f; private var knobY = 0f; private var knobR = 0f
    private val card = RectF()
    private var leverY = 0f; private var leverR = 0f
    private val leverX = FloatArray(3)
    private var swX = 0f; private var swY = 0f; private var swR = 0f
    private val plateH get() = 6.5f * t + 5 * d

    override fun onMeasure(w: Int, h: Int) {
        val W = MeasureSpec.getSize(w).toFloat()
        layout(W)
        setMeasuredDimension(W.toInt(), (swY + swR * 2.3f + plateH + 26 * d).toInt())
    }

    private fun layout(W: Float) {
        val top = 18 * d
        val mw = W * 0.36f; val my = top + 14 * d + plateH + 4 * d
        sMeter.set(18 * d, my, 18 * d + mw, my + mw * 0.9f)
        kpMeter.set(W - 18 * d - mw, my, W - 18 * d, my + mw * 0.9f)
        eyeX = W / 2; eyeY = my + mw * 0.55f
        dialR = W * 0.42f; cx = W / 2; cy = sMeter.bottom + 22 * d + dialR
        knobR = dialR * 0.42f; knobX = cx; knobY = cy + 20 * d
        val lineH = 5 * t + 6 * d
        card.set(14 * d, cy + 10 * d, 14 * d + W * 0.27f, cy + 10 * d + 8 * d + 6 * t + 3 * lineH)
        leverR = 20 * d
        leverY = maxOf(knobY + knobR + 7 * t + 14 * d, card.bottom) + plateH + leverR + 16 * d
        leverX[0] = W * 0.2f; leverX[1] = W * 0.5f; leverX[2] = W * 0.8f
        swR = 14 * d; swX = W / 2; swY = leverY + leverR + 6 * t + 34 * d + swR * 2.2f
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        layout(w.toFloat())
        panel.shader = BitmapShader(Radio.blackEnamel, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        for (i in toggles.indices.take(3)) leverAngle[i] = if (toggles[i].second) -30f else -150f
    }

    override fun onDraw(c: Canvas) {
        val W = width.toFloat(); val H = height.toFloat()
        // Rubber carrying handle across the top, then the panel.
        fill.shader = LinearGradient(0f, 0f, 0f, 14 * d, Color.rgb(0x3A, 0x3A, 0x3A), Color.rgb(0x0A, 0x0A, 0x0A), Shader.TileMode.CLAMP)
        rect.set(W * 0.22f, 0f, W * 0.78f, 14 * d); c.drawRoundRect(rect, 7 * d, 7 * d, fill); fill.shader = null
        c.drawRect(0f, 16 * d, W, H, panel)
        stroke.color = Color.BLACK; stroke.strokeWidth = 3 * d
        c.drawRect(1.5f * d, 16 * d, W - 1.5f * d, H - 1.5f * d, stroke)
        for ((x, y) in listOf(10 * d to 26 * d, W - 10 * d to 26 * d, 10 * d to H - 10 * d, W - 10 * d to H - 10 * d)) screw(c, x, y, 4f)

        // Meters with their brass plates, and the tuning eye between them.
        plate(c, sMeter.centerX(), sMeter.top - plateH - 4 * d, sMeter.width() * 0.8f, "SIGNAL STRENGTH")
        plate(c, kpMeter.centerX(), kpMeter.top - plateH - 4 * d, kpMeter.width() * 0.8f, "Kp INDEX")
        meter(c, sMeter, "SIGNAL", listOf("1", "3", "5", "7", "9", "+20"), sNeedle, 0.8f)
        meter(c, kpMeter, "Kp INDEX", listOf("0", "3", "6", "9"), kpNeedle, 0.66f)
        tuningEye(c)

        // Grab handles beside the dial.
        handle(c, 3 * d, cy - dialR * 0.95f, dialR * 1.1f, -1)
        handle(c, W - 3 * d, cy - dialR * 0.95f, dialR * 1.1f, 1)
        mainDial(c)
        knurledKnob(c, knobX, knobY, knobR, Color.rgb(0x12, 0x12, 0x12), null, knobSpin)
        text.textSize = 5.6f * t; text.letterSpacing = 0.08f; text.color = Radio.CREAM
        c.drawText("CHECK NOW", knobX, knobY + knobR + 7 * t, text)
        recordCard(c)

        // Colour-coded lever switches.
        val colours = listOf(Radio.CODE_BLUE, Radio.CODE_RED, Radio.CODE_YELLOW)
        for ((i, tg) in toggles.withIndex().take(3)) lever(c, leverX[i], leverY, leverR, colours[i], leverAngle[i], tg.first)

        // Band switch between two coloured knurled knobs.
        // The red knob is the mood light (the set's AERIAL knob in real life).
        knurledKnob(c, W * 0.17f, swY, 15 * d, Radio.CODE_RED, Radio.DIAL_CREAM, if (mood) 90f else 0f)
        drawJewel(c, fill, W * 0.17f, swY, 3.2f * d, mood)
        text.textSize = 5 * t; c.drawText("MOOD LIGHT", W * 0.17f, swY + 15 * d * 1.35f + 7 * t, text)
        knurledKnob(c, W * 0.83f, swY, 15 * d, Radio.CODE_YELLOW, Radio.DIAL_CREAM, 0f)
        c.drawText("ANODE", W * 0.83f, swY + 15 * d * 1.35f + 7 * t, text)
        bandSwitch(c)
        text.letterSpacing = 0f
        // Service life in a bomber: scratched enamel, chipped edges, grime, rubbed plates.
        rect.set(0f, 16 * d, W, H)
        Wear.panel(c, rect, 61, d, heavy = true)
        Wear.chips(c, rect, 61, 45, d, Color.argb(170, 0x8A, 0x86, 0x7C))
    }

    // ---- parts

    private fun screw(c: Canvas, x: Float, y: Float, r: Float = 2.8f) {
        fill.shader = RadialGradient(x - d, y - d, r * 2 * d, Color.rgb(0xF0, 0xF0, 0xEA), Color.rgb(0x5A, 0x5A, 0x56), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r * d, fill); fill.shader = null
        stroke.color = Color.rgb(0x2A, 0x2A, 0x28); stroke.strokeWidth = 0.9f * d
        val a = ((x * 7 + y * 13) % 314) / 100f   // each screw slot at its own angle
        c.drawLine(x - r * 0.75f * d * cos(a), y - r * 0.75f * d * sin(a), x + r * 0.75f * d * cos(a), y + r * 0.75f * d * sin(a), stroke)
    }

    /** Engraved brass label plate, centred on [x], top at [y]. */
    private fun plate(c: Canvas, x: Float, y: Float, w: Float, label: String) {
        rect.set(x - w / 2, y, x + w / 2, y + plateH)
        fill.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(Radio.PLATE_LIGHT, Radio.PLATE, Radio.PLATE_DARK), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, 2 * d, 2 * d, fill); fill.shader = null
        var i = 0f
        while (i < w) { c.drawPoint(rect.left + i, rect.top + (i * 7.3f) % plateH, speck); i += 2.3f * d }   // worn spots
        text.color = Color.rgb(0x15, 0x12, 0x0C); text.textSize = 5.4f * t; text.letterSpacing = 0.06f
        fitText(c, label, x, rect.centerY() + text.textSize * 0.36f, text, w - 6 * d)
        text.color = Radio.CREAM
    }

    private fun meter(c: Canvas, r: RectF, label: String, labels: List<String>, value: Float, redFrom: Float) {
        fill.shader = LinearGradient(r.left, r.top, r.right, r.bottom, Color.rgb(0x3A, 0x35, 0x28), Color.rgb(0x14, 0x12, 0x10), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 4 * d, 4 * d, fill)
        val f = RectF(r.left + 7 * d, r.top + 7 * d, r.right - 7 * d, r.bottom - 7 * d)
        fill.shader = RadialGradient(f.centerX(), f.bottom, f.width(), Radio.mix(Color.rgb(0x5A, 0x52, 0x40), Color.rgb(0xF4, 0xE7, 0xBE), glow),
            Radio.mix(Color.rgb(0x40, 0x38, 0x28), Color.rgb(0xC9, 0xB4, 0x7A), glow), Shader.TileMode.CLAMP)
        c.drawRoundRect(f, 4 * d, 4 * d, fill); fill.shader = null
        c.save(); c.clipRect(f)
        val px = f.centerX(); val py = f.top + f.height() * 0.95f; val rr = f.height() * 0.72f
        val a0 = -124f; val a1 = -56f   // narrow enough that the end numbers stay on the face
        fun ang(v: Float) = Math.toRadians((a0 + (a1 - a0) * v).toDouble())
        stroke.color = Radio.INK; stroke.strokeWidth = d
        rect.set(px - rr, py - rr, px + rr, py + rr); c.drawArc(rect, a0, a1 - a0, false, stroke)
        stroke.color = Radio.CODE_RED; stroke.strokeWidth = 2 * d
        rect.set(px - rr - 1.3f * d, py - rr - 1.3f * d, px + rr + 1.3f * d, py + rr + 1.3f * d)
        c.drawArc(rect, a0 + (a1 - a0) * redFrom, (a1 - a0) * (1 - redFrom), false, stroke)
        stroke.color = Radio.INK; stroke.strokeWidth = 0.8f * d
        for (k in 0..20) {
            val a = ang(k / 20f); val len = (if (k % 5 == 0) 4.5f else 2.5f) * d
            c.drawLine(px + rr * cos(a).toFloat(), py + rr * sin(a).toFloat(), px + (rr - len) * cos(a).toFloat(), py + (rr - len) * sin(a).toFloat(), stroke)
        }
        serif.textSize = 5.8f * t
        for ((i, l) in labels.withIndex()) {
            val v = i / (labels.size - 1f); val a = ang(v); val tr = rr + 3 * d + serif.textSize * 0.6f
            serif.color = if (v >= redFrom - 0.01f) Radio.CODE_RED else Radio.INK
            c.drawText(l, px + tr * cos(a).toFloat(), py + tr * sin(a).toFloat() + serif.textSize * 0.36f, serif)
        }
        serif.color = Radio.INK
        text.color = Radio.INK; text.textSize = 5.4f * t; text.letterSpacing = 0.08f
        fitText(c, label, px, py - rr * 0.38f, text, f.width() - 8 * d)
        text.color = Radio.CREAM
        val a = ang(value.coerceIn(0f, 1f))
        stroke.color = Color.rgb(0x11, 0x11, 0x11); stroke.strokeWidth = 1.3f * d
        c.drawLine(px, py, px + (rr + d) * cos(a).toFloat(), py + (rr + d) * sin(a).toFloat(), stroke)
        c.restore()
        fill.color = Color.argb(0x1F, 255, 255, 255)
        rect.set(f.left, f.top, f.right, f.top + f.height() * 0.45f); c.drawRoundRect(rect, 4 * d, 4 * d, fill)
        for ((x, y) in listOf(r.left + 3.5f * d to r.top + 3.5f * d, r.right - 3.5f * d to r.top + 3.5f * d,
            r.left + 3.5f * d to r.bottom - 3.5f * d, r.right - 3.5f * d to r.bottom - 3.5f * d)) screw(c, x, y, 2.2f)
    }

    /** Green "magic eye" tuning indicator: its dark wedge closes as conditions open up. */
    private fun tuningEye(c: Canvas) {
        plate(c, eyeX, eyeY - 32 * d, 52 * d, "TUNING")
        fill.color = Color.rgb(0x11, 0x11, 0x11); c.drawCircle(eyeX, eyeY, 13 * d, fill)
        fill.shader = RadialGradient(eyeX, eyeY, 10 * d, intArrayOf(Radio.mix(Color.rgb(0x20, 0x30, 0x24), Color.rgb(0xC8, 0xFF, 0xD0), glow),
            Radio.mix(Color.rgb(0x10, 0x20, 0x14), Color.rgb(0x3C, 0xD0, 0x70), glow), Color.rgb(0x0A, 0x40, 0x20)), null, Shader.TileMode.CLAMP)
        c.drawCircle(eyeX, eyeY, 9 * d, fill); fill.shader = null
        fill.color = Color.rgb(0x11, 0x11, 0x11)
        rect.set(eyeX - 9.5f * d, eyeY - 9.5f * d, eyeX + 9.5f * d, eyeY + 9.5f * d)
        val sweep = Math.toDegrees(eye.toDouble()).toFloat()
        c.drawArc(rect, -90f - sweep, sweep * 2, true, fill)
    }

    private fun handle(c: Canvas, x: Float, y: Float, h: Float, side: Int) {
        stroke.shader = LinearGradient(x - 4 * d, 0f, x + 4 * d, 0f, intArrayOf(Color.rgb(0x77, 0x77, 0x77), Color.rgb(0xF4, 0xF4, 0xF0), Color.rgb(0x66, 0x66, 0x66)), null, Shader.TileMode.CLAMP)
        stroke.strokeWidth = 5 * d; stroke.strokeCap = Paint.Cap.ROUND; stroke.strokeJoin = Paint.Join.ROUND
        val o = side * 12 * d
        path.reset(); path.moveTo(x, y); path.lineTo(x + o, y + 8 * d); path.lineTo(x + o, y + h - 8 * d); path.lineTo(x, y + h)
        c.drawPath(path, stroke)
        stroke.shader = null; stroke.strokeCap = Paint.Cap.BUTT
        fill.color = Color.rgb(0x22, 0x22, 0x22)
        c.drawCircle(x, y, 4 * d, fill); c.drawCircle(x, y + h, 4 * d, fill)
    }

    private data class Scale(val name: String, val labels: List<String>, val colour: Int, val frac: Float)

    private fun mainDial(c: Canvas) {
        val r0 = dialR
        fill.color = Color.rgb(0x0A, 0x0A, 0x0A)
        path.reset()
        rect.set(cx - r0 - 8 * d, cy - r0 - 8 * d, cx + r0 + 8 * d, cy + r0 + 8 * d)
        path.arcTo(rect, 180f, 180f, false); path.lineTo(cx + r0 + 8 * d, cy + 10 * d); path.lineTo(cx - r0 - 8 * d, cy + 10 * d); path.close()
        c.drawPath(path, fill)
        fill.shader = RadialGradient(cx, cy, r0, Radio.mix(Color.rgb(0x6A, 0x64, 0x54), Color.rgb(0xFB, 0xF3, 0xDA), glow),
            Radio.mix(Color.rgb(0x5A, 0x54, 0x44), Color.rgb(0xE8, 0xDD, 0xBC), glow), Shader.TileMode.CLAMP)
        rect.set(cx - r0, cy - r0, cx + r0, cy + r0)
        c.drawArc(rect, 180f, 180f, true, fill); fill.shader = null
        val scales = listOf(
            Scale("", listOf("QUIET", "HEADS-UP", "OPEN"), Color.rgb(0x3A, 0x6E, 0xB0), 0.92f),
            Scale("HF", listOf("80", "40", "20", "15", "10"), Radio.CODE_ORANGE, 0.79f),
            Scale("6M", listOf("50", "51", "52", "53", "54"), Color.rgb(0xC8, 0x9A, 0x00), 0.67f),
            Scale("2M", listOf("144", "145", "146"), Radio.INK, 0.55f),
            Scale("70", listOf("430", "435", "440"), Radio.CODE_BLUE, 0.43f),
        )
        val a0 = 180f * 1.04f; val a1 = 180f * 1.96f
        fun ang(v: Float) = Math.toRadians((a0 + (a1 - a0) * v).toDouble())
        for ((i, sc) in scales.withIndex()) {
            val r = r0 * sc.frac
            stroke.color = sc.colour; stroke.strokeWidth = (if (i == 0) 2.2f else 1.8f) * d
            rect.set(cx - r, cy - r, cx + r, cy + r); c.drawArc(rect, a0, a1 - a0, false, stroke)
            stroke.strokeWidth = 0.8f * d
            for (k in 0..40) {
                val a = ang(k / 40f); val len = (if (k % 10 == 0) 5f else if (k % 5 == 0) 3.5f else 2f) * d
                c.drawLine(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat(), cx + (r - len) * cos(a).toFloat(), cy + (r - len) * sin(a).toFloat(), stroke)
            }
            text.color = sc.colour; text.textSize = (if (i == 0) 5.6f else 5.8f) * t; text.letterSpacing = if (i == 0) 0.06f else 0f
            for ((k, l) in sc.labels.withIndex()) {
                if (i > 0 && k == 0) continue   // the band name sits at the left end instead
                val v = if (i == 0) listOf(0.15f, 0.5f, 0.85f)[k] else k / (sc.labels.size - 1f)
                val a = ang(0.04f + v * 0.92f); val tr = r - r0 * 0.065f
                c.drawText(l, cx + tr * cos(a).toFloat(), cy + tr * sin(a).toFloat() + text.textSize * 0.36f, text)
            }
            if (sc.name.isNotEmpty()) {
                // Coloured dot and band name at the left end of the scale, like the set's range dots.
                val a = ang(0.04f); val tr = r - r0 * 0.065f
                val x = cx + tr * cos(a).toFloat(); val y = cy + tr * sin(a).toFloat()
                fill.color = sc.colour; c.drawCircle(x - 7 * d, y, 2.6f * d, fill)
                text.textAlign = Paint.Align.LEFT; c.drawText(sc.name, x - 3 * d, y + text.textSize * 0.36f, text); text.textAlign = Paint.Align.CENTER
            }
        }
        text.color = Radio.CREAM; text.letterSpacing = 0f
        // Hairline pointer.
        val a = ang(0.04f + pointer * 0.92f)
        stroke.color = Color.rgb(0x11, 0x11, 0x11); stroke.strokeWidth = 1.2f * d
        c.drawLine(cx + r0 * 0.3f * cos(a).toFloat(), cy + r0 * 0.3f * sin(a).toFloat(), cx + r0 * 0.99f * cos(a).toFloat(), cy + r0 * 0.99f * sin(a).toFloat(), stroke)
        // Glass sheen, then the black boss the tuning knob sits on.
        fill.color = Color.argb(0x1A, 255, 255, 255)
        rect.set(cx - r0, cy - r0, cx + r0, cy + r0); c.drawArc(rect, 180f, 90f, true, fill)
        fill.color = Color.rgb(0x0E, 0x0E, 0x0E)
        rect.set(cx - r0 * 0.33f, cy - r0 * 0.33f, cx + r0 * 0.33f, cy + r0 * 0.33f); c.drawArc(rect, 180f, 180f, true, fill)
    }

    /** Knurled knob; [ring] adds a cream scale ring behind it; [spin] turns its screws. */
    private fun knurledKnob(c: Canvas, x: Float, y: Float, r: Float, colour: Int, ring: Int?, spin: Float) {
        if (ring != null) {
            fill.color = ring; c.drawCircle(x, y, r * 1.35f, fill)
            stroke.color = Color.rgb(0x8A, 0x20, 0x20); stroke.strokeWidth = 0.6f * d
            for (k in 0 until 60) {
                val a = k / 60.0 * Math.PI * 2; val inner = if (k % 5 == 0) 1.18f else 1.25f
                c.drawLine(x + r * 1.33f * cos(a).toFloat(), y + r * 1.33f * sin(a).toFloat(), x + r * inner * cos(a).toFloat(), y + r * inner * sin(a).toFloat(), stroke)
            }
        }
        path.reset()
        for (k in 0..48) {
            val a = k / 48.0 * Math.PI * 2; val rr = r * if (k % 2 == 1) 0.93f else 1f
            val px = x + rr * cos(a).toFloat(); val py = y + rr * sin(a).toFloat()
            if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        fill.color = colour; c.drawPath(path, fill)
        fill.shader = RadialGradient(x - r * 0.3f, y - r * 0.3f, r * 1.1f, Color.argb(0x40, 255, 255, 255), Color.argb(0x4D, 0, 0, 0), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r * 0.8f, fill); fill.shader = null
        fill.color = Color.rgb(0xB8, 0xB8, 0xB0)
        c.drawCircle(x, y, r * 0.16f, fill)
        for (k in 0..2) {
            val a = Math.toRadians(k * 120.0 - 90 + spin)
            c.drawCircle(x + r * 0.45f * cos(a).toFloat(), y + r * 0.45f * sin(a).toFloat(), r * 0.08f, fill)
        }
        stroke.color = Color.rgb(0x33, 0x33, 0x33); stroke.strokeWidth = 0.8f * d
        val s = Math.toRadians(spin.toDouble())
        c.drawLine(x - r * 0.12f * cos(s).toFloat(), y - r * 0.12f * sin(s).toFloat(), x + r * 0.12f * cos(s).toFloat(), y + r * 0.12f * sin(s).toFloat(), stroke)
    }

    private fun recordCard(c: Canvas) {
        fill.color = Color.rgb(0xF4, 0xF1, 0xE6); c.drawRoundRect(card, 3 * d, 3 * d, fill)
        stroke.color = Color.rgb(0x22, 0x22, 0x22); stroke.strokeWidth = 0.8f * d; c.drawRoundRect(card, 3 * d, 3 * d, stroke)
        text.color = Radio.INK; text.textSize = 4.6f * t; text.letterSpacing = 0.06f
        val headY = card.top + 4 * d + text.textSize
        fitText(c, "STATION RECORD", card.centerX(), headY, text, card.width() - 6 * d)
        c.drawLine(card.left + 4 * d, headY + 3 * d, card.right - 4 * d, headY + 3 * d, stroke)
        mono.textSize = 4.8f * t
        val lineH = 5 * t + 6 * d
        stroke.color = Color.rgb(0x8A, 0xA0, 0xC8); stroke.strokeWidth = 0.5f * d
        for ((i, l) in record.withIndex().take(3)) {
            val y = headY + 3 * d + lineH * (i + 1) - 3 * d
            fitText(c, l, card.left + 6 * d, y, mono, card.width() - 10 * d)
            c.drawLine(card.left + 4 * d, y + 2.5f * d, card.right - 4 * d, y + 2.5f * d, stroke)
        }
        text.color = Radio.CREAM; text.letterSpacing = 0f
    }

    /** Colour-coded lever on a numbered black disc, like a transmitter's tuning switch. */
    private fun lever(c: Canvas, x: Float, y: Float, r: Float, colour: Int, angle: Float, name: String) {
        plate(c, x, y - r - plateH - 5 * d, r * 2.4f, name)
        fill.color = Color.rgb(0x0C, 0x0C, 0x0C); c.drawCircle(x, y, r, fill)
        stroke.color = Color.rgb(0x33, 0x33, 0x33); stroke.strokeWidth = d; c.drawCircle(x, y, r, stroke)
        text.color = Radio.CREAM; text.textSize = 3.6f * t
        for (k in 0..8) {
            val a = Math.toRadians(-200.0 + 220.0 * k / 8)
            c.drawText("${k + 1}", x + r * 0.82f * cos(a).toFloat(), y + r * 0.82f * sin(a).toFloat() + text.textSize * 0.36f, text)
        }
        text.textSize = 3.8f * t
        c.drawText("OFF", x - r * 0.55f, y + r * 0.62f, text)
        c.drawText("ON", x + r * 0.55f, y + r * 0.62f, text)
        c.save(); c.rotate(angle, x, y)
        fill.color = Radio.mix(colour, Color.BLACK, 0.35f)
        rect.set(x - r * 0.25f, y - r * 0.15f, x + r * 0.7f, y + r * 0.15f); c.drawRoundRect(rect, r * 0.15f, r * 0.15f, fill)
        fill.color = colour
        rect.set(x - r * 0.25f, y - r * 0.13f, x + r * 0.67f, y + r * 0.09f); c.drawRoundRect(rect, r * 0.11f, r * 0.11f, fill)
        c.restore()
        fill.color = Color.rgb(0xBB, 0xBB, 0xBB); c.drawCircle(x, y, r * 0.12f, fill)
    }

    private fun switchAngleFor(i: Int) = if (bands.size < 2) -90f else -170f + 160f * i / (bands.size - 1)

    private fun bandSwitch(c: Canvas) {
        val sector = listOf(Radio.CREAM, Radio.CODE_ORANGE, Radio.CODE_YELLOW, Color.rgb(0x3A, 0x3A, 0x3A), Radio.CODE_BLUE)
        stroke.strokeWidth = 7 * d
        rect.set(swX - swR * 1.55f, swY - swR * 1.55f, swX + swR * 1.55f, swY + swR * 1.55f)
        for (i in bands.indices) {
            stroke.color = sector[i % sector.size]
            c.drawArc(rect, switchAngleFor(i) - 11f, 22f, false, stroke)
        }
        text.color = Radio.CREAM; text.textSize = 5.8f * t
        for ((i, b) in bands.withIndex()) {
            val a = Math.toRadians(switchAngleFor(i).toDouble())
            val lx = swX + swR * 2.4f * cos(a).toFloat(); val ly = swY + swR * 2.4f * sin(a).toFloat()
            c.drawText(b, lx, ly + text.textSize * 0.36f, text)
            bandLamps.getOrNull(i)?.let { col ->
                fill.shader = RadialGradient(lx, ly - text.textSize, 3 * d, Color.WHITE, col, Shader.TileMode.CLAMP)
                c.drawCircle(lx, ly - text.textSize * 0.95f, 2.4f * d, fill); fill.shader = null
            }
        }
        fill.color = Color.rgb(0x0A, 0x0A, 0x0A); c.drawCircle(swX, swY, swR, fill)
        c.save(); c.rotate(switchAngle, swX, swY)
        fill.shader = LinearGradient(0f, swY - swR * 0.25f, 0f, swY + swR * 0.25f,
            intArrayOf(Color.rgb(0x3A, 0x3A, 0x3A), Color.rgb(0x11, 0x11, 0x11), Color.BLACK), null, Shader.TileMode.CLAMP)
        rect.set(swX - swR * 0.4f, swY - swR * 0.24f, swX + swR * 1.5f, swY + swR * 0.24f)
        c.drawRoundRect(rect, swR * 0.2f, swR * 0.2f, fill); fill.shader = null
        fill.color = Radio.CREAM
        path.reset(); path.moveTo(swX + swR * 1.45f, swY); path.lineTo(swX + swR * 1.2f, swY - swR * 0.12f); path.lineTo(swX + swR * 1.2f, swY + swR * 0.12f); path.close()
        c.drawPath(path, fill)
        c.restore()
        plate(c, swX, swY + swR * 1.55f + 10 * d, 74 * d, "WAVEBAND")
    }

    // ---- state

    private fun levelPos(level: Int) = when (level) { 2 -> 0.85f; 1 -> 0.5f; else -> 0.15f }
    private fun sPos(level: Int) = when (level) { 2 -> 0.9f; 1 -> 0.66f; 0 -> 0.34f; else -> 0.04f }
    private fun eyeFor(level: Int) = when (level) { 2 -> 0.08f; 1 -> 0.4f; else -> 0.9f }

    override fun show(level: Int, kp: Float?, animated: Boolean) {
        val p = levelPos(level.coerceAtLeast(0)); val s = sPos(level); val k = (kp ?: 0f) / 9f; val e = eyeFor(level)
        if (!animated) { pointer = p; sNeedle = s; kpNeedle = k; eye = e; invalidate(); return }
        animate(pointer, p, 800, 0, OvershootInterpolator(0.6f)) { pointer = it }
        animate(sNeedle, s, 900, 80, OvershootInterpolator(1.2f)) { sNeedle = it }
        animate(kpNeedle, k, 900, 160, OvershootInterpolator(1.2f)) { kpNeedle = it }
        animate(eye, e, 700, 0) { eye = it }
    }

    override fun warmUp(level: Int, kp: Float?) {
        glow = 0f; pointer = 1f; sNeedle = 0f; kpNeedle = 0f; eye = 1.2f; invalidate()
        animate(0f, 1f, 1600, 150) { glow = it }
        animate(1f, levelPos(level.coerceAtLeast(0)), 1100, 1000, OvershootInterpolator(0.6f)) { pointer = it }
        animate(0f, sPos(level), 1000, 1200, OvershootInterpolator(1.2f)) { sNeedle = it }
        animate(0f, (kp ?: 0f) / 9f, 1000, 1300, OvershootInterpolator(1.2f)) { kpNeedle = it }
        animate(1.2f, eyeFor(level), 1200, 1000) { eye = it }
    }

    /** The big tuning knob turns while a check is running. */
    override fun setChecking(on: Boolean) {
        spinner?.cancel(); spinner = null
        if (on) spinner = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 2400; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
            addUpdateListener { knobSpin = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun animateSwitch() {
        val to = switchAngleFor(selected)
        if (!isLaidOut) { switchAngle = to; invalidate(); return }
        animate(switchAngle, to, 350, 0, OvershootInterpolator(1.5f)) { switchAngle = it }
    }

    private fun animateLevers() {
        for (i in toggles.indices.take(3)) {
            val to = if (toggles[i].second) -30f else -150f
            if (!isLaidOut) leverAngle[i] = to
            else if (leverAngle[i] != to) animate(leverAngle[i], to, 300, 0, OvershootInterpolator(1.2f)) { leverAngle[i] = it }
        }
        invalidate()
    }

    override fun glows(): List<Glow> {
        val out = arrayListOf(
            Glow(RectF(cx - dialR, cy - dialR, cx + dialR, cy), Mood.AMBER, 1f),
            Glow(RectF(sMeter), Mood.AMBER, 0.7f), Glow(RectF(kpMeter), Mood.AMBER, 0.7f),
            Glow(RectF(eyeX - 10 * d, eyeY - 10 * d, eyeX + 10 * d, eyeY + 10 * d), Color.rgb(0x3C, 0xFF, 0x80), 0.8f),
        )
        val ts = 5.8f * t
        for ((i, col) in bandLamps.withIndex()) if (col != null) {
            val a = Math.toRadians(switchAngleFor(i).toDouble())
            val lx = swX + swR * 2.4f * cos(a).toFloat(); val ly = swY + swR * 2.4f * sin(a).toFloat() - ts * 0.95f
            out += Glow(RectF(lx - 5 * d, ly - 5 * d, lx + 5 * d, ly + 5 * d), col, 0.7f)
        }
        if (mood) out += Glow(RectF(width * 0.17f - 7 * d, swY - 7 * d, width * 0.17f + 7 * d, swY + 7 * d), Mood.LAMP, 1f)
        return out
    }

    override fun onDetachedFromWindow() { spinner?.cancel(); super.onDetachedFromWindow() }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN) return true
        if (e.action != MotionEvent.ACTION_UP) return super.onTouchEvent(e)
        val x = e.x; val y = e.y
        when {
            hypot(x - knobX, y - knobY) < knobR * 1.1f -> onCheck?.invoke()
            hypot(x - swX, y - swY) < swR * 3f && bands.isNotEmpty() -> {
                val dist = hypot(x - swX, y - swY)
                val i = if (dist > swR * 1.3f) {
                    val a = Math.toDegrees(atan2((y - swY).toDouble(), (x - swX).toDouble())).toFloat()
                    bands.indices.minByOrNull { abs(switchAngleFor(it) - a) } ?: selected
                } else (selected + 1) % bands.size
                if (i != selected) { selected = i; onSelect?.invoke(i) }
            }
            hypot(x - width * 0.17f, y - swY) < 15 * d * 1.5f -> { mood = !mood; onMood?.invoke(mood) }
            abs(y - leverY) < leverR * 1.3f -> {
                val i = leverX.indices.firstOrNull { abs(x - leverX[it]) < leverR * 1.4f }
                if (i != null && i < toggles.size) {
                    val on = !toggles[i].second
                    toggles = toggles.toMutableList().also { it[i] = it[i].first to on }
                    onToggle?.invoke(i, on)
                }
            }
        }
        performClick()
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}
