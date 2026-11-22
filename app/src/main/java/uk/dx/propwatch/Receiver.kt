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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/*
 * The "communications receiver" cabinet: a 1940s ham-shack receiver with a black
 * crackle-finish front panel, amber backlit dial windows, chrome art-deco side trim,
 * bakelite knobs and a louvred speaker cabinet on top. Original artwork; no maker's
 * names or logos.
 */

/**
 * A drawn front panel with its own dials and controls (receiver and aircraft cabinets),
 * so the activity can drive either the same way.
 */
interface FrontPanel {
    var bands: List<String>
    var bandLamps: List<Int?>
    var selected: Int
    var toggles: List<Pair<String, Boolean>>
    var onCheck: (() -> Unit)?
    var onSelect: ((Int) -> Unit)?
    var onToggle: ((Int, Boolean) -> Unit)?
    /** [level] 0 quiet, 1 heads-up, 2 open, -1 no data; [kp] 0..9 or null. */
    fun show(level: Int, kp: Float?, animated: Boolean)
    fun warmUp(level: Int, kp: Float?)
    fun setChecking(on: Boolean)
}

/** Separate speaker cabinet that sits on top of the receiver: grey crackle box, chrome louvres. */
class SpeakerCabinetView(ctx: Context) : CabinetView(ctx) {
    private val box = paint()
    private val edge = paint(Color.rgb(0x1A, 0x1A, 0x1A)) { style = Paint.Style.STROKE; strokeWidth = 1.5f * d }
    private val recess = paint(Color.rgb(0x2A, 0x2C, 0x2C))
    private val hole = paint(Color.rgb(0x0C, 0x0C, 0x0C))
    private val bar = paint()
    private val strut = paint(Color.rgb(0x8A, 0x8A, 0x86))
    private val bolt = paint(Color.rgb(0x11, 0x11, 0x11))

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), (124 * d).toInt())

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height - 6 * d
        val l = w * 0.06f; val r = w * 0.94f
        box.shader = BitmapShader(Radio.greyCrackle, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        c.drawRect(l, 0f, r, h, box)
        c.drawRect(l, 0f, r, h, edge)
        val gx = w * 0.22f; val gw = w * 0.56f
        c.drawRect(gx - 6 * d, 8 * d, gx + gw + 6 * d, h - 8 * d, recess)
        for ((y0, y1) in listOf(12 * d to h / 2 - 4 * d, h / 2 + 4 * d to h - 12 * d)) {
            c.drawRect(gx, y0, gx + gw, y1, hole)
            var y = y0 + 2 * d
            while (y < y1 - 1 * d) {
                bar.shader = LinearGradient(0f, y, 0f, y + 2.6f * d, Color.rgb(0xC8, 0xC8, 0xC2), Color.rgb(0x6A, 0x6A, 0x66), Shader.TileMode.CLAMP)
                c.drawRect(gx, y, gx + gw, y + 2.6f * d, bar)
                y += 5.2f * d
            }
            for (f in listOf(0.33f, 0.66f)) c.drawRect(gx + gw * f - 0.8f * d, y0, gx + gw * f + 0.8f * d, y1, strut)
        }
        for ((x, y) in listOf(w * 0.12f to 14 * d, w * 0.88f to 14 * d, w * 0.12f to h - 14 * d, w * 0.88f to h - 14 * d,
            w / 2 to 5 * d, w / 2 to h / 2, w / 2 to h - 5 * d)) c.drawCircle(x, y, 2 * d, bolt)
        for (f in listOf(0.25f, 0.75f)) c.drawRect(w * f - 8 * d, h, w * f + 8 * d, h + 6 * d, bolt)   // rubber feet
    }
}

/**
 * The receiver's front panel, drawn as one piece so it can follow the real layout:
 * the main tuning dial in a raised surround, an S-meter and a Kp meter either side of
 * the nameplate, then the controls.
 *
 * Working controls: the gold-ringed vernier knob runs a check; the rotary switch picks
 * the waveband (with a pilot lamp beside each band); the toggles switch SOUND, MONITOR
 * and GPS. The other knobs are for show.
 */
class ReceiverPanelView(ctx: Context) : CabinetView(ctx), FrontPanel {
    override var bands: List<String> = emptyList()
        set(v) { field = v; invalidate() }
    override var bandLamps: List<Int?> = emptyList()
        set(v) { field = v; invalidate() }
    override var selected = 0
        set(v) { field = v; animateSwitch() }
    override var toggles: List<Pair<String, Boolean>> = emptyList()   // label to state
        set(v) { field = v; invalidate() }
    override var onCheck: (() -> Unit)? = null
    override var onSelect: ((Int) -> Unit)? = null
    override var onToggle: ((Int, Boolean) -> Unit)? = null

    // Animated state.
    private var glow = 1f           // dial lamps: 0 cold, 1 warm
    private var pointer = 0.18f     // main dial hairline, 0..1
    private var sNeedle = 0.3f      // S-meter, 0..1
    private var kpNeedle = 0f       // Kp meter, 0..1 (Kp / 9)
    private var switchAngle = 0f    // band switch handle, degrees
    private var ringSpin = 0f       // vernier ring, degrees
    private var spinner: ValueAnimator? = null

    private val panel = paint()
    private val rib = paint()
    private val screwPaint = paint()
    private val slot = paint(Color.rgb(0x33, 0x33, 0x33)) { strokeWidth = 0.8f * d }
    private val surround = paint(Color.argb(0xBF, 0, 0, 0)) { style = Paint.Style.STROKE; strokeWidth = 2.5f * d }
    private val bezelPaint = paint(Color.rgb(0x05, 0x05, 0x05))
    private val glass = paint()
    private val ink = paint(Radio.DIAL_INK) { style = Paint.Style.STROKE }
    private val red = paint(Radio.PANEL_RED) { style = Paint.Style.STROKE }
    private val dialText = paint(Radio.DIAL_INK) { typeface = Radio.SERIF; textAlign = Paint.Align.CENTER }
    private val sans = paint(Radio.DIAL_INK) { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textAlign = Paint.Align.CENTER }
    private val needle = paint(Color.rgb(0x11, 0x0A, 0x04)) { strokeWidth = 1.4f * d; strokeCap = Paint.Cap.ROUND }
    private val boss = paint(Color.rgb(0x11, 0x11, 0x11))
    private val bossRim = paint(Color.rgb(0x33, 0x33, 0x33)) { style = Paint.Style.STROKE; strokeWidth = d }
    private val sheen = paint(Color.argb(0x10, 255, 255, 255))
    private val white = paint(Radio.CREAM) { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textAlign = Paint.Align.CENTER }
    private val script = paint(Radio.PANEL_RED) { typeface = Radio.SERIF_ITALIC; textAlign = Paint.Align.CENTER }
    private val whiteScript = paint(Radio.CREAM) { typeface = Radio.SERIF_ITALIC; textAlign = Paint.Align.CENTER }
    private val fill = paint()
    private val stroke = paint { style = Paint.Style.STROKE }
    private val lamp = paint()
    private val path = Path()
    private val rect = RectF()

    // Layout, worked out in onSizeChanged.
    private val dial = RectF(); private val sMeter = RectF(); private val kpMeter = RectF()
    private var tuneX = 0f; private var tuneY = 0f; private var tuneR = 0f
    private var swX = 0f; private var swY = 0f; private var swR = 0f
    private var spreadX = 0f
    private var row2Y = 0f
    private val toggleX = FloatArray(3)

    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w).toFloat()
        val dialH = width * 0.74f * 0.64f
        val meterH = width * 0.30f * 0.92f
        setMeasuredDimension(width.toInt(), (16 * d + dialH + 22 * d + meterH + 178 * d + 20 * t).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val W = w.toFloat()
        val mw = W * 0.74f; val mh = mw * 0.64f
        dial.set((W - mw) / 2, 16 * d, (W + mw) / 2, 16 * d + mh)
        val sw = W * 0.30f; val sh = sw * 0.92f
        val my = dial.bottom + 22 * d
        sMeter.set(14 * d, my, 14 * d + sw, my + sh)
        kpMeter.set(W - 14 * d - sw, my, W - 14 * d, my + sh)
        tuneR = 27 * d
        tuneY = sMeter.bottom + 20 * d + tuneR * 1.45f
        tuneX = W * 0.2f; spreadX = W * 0.8f
        swX = W / 2; swY = tuneY - 2 * d; swR = 15 * d
        row2Y = tuneY + tuneR * 1.45f + 14 * t + 30 * d
        toggleX[0] = W * 0.30f; toggleX[1] = W * 0.5f; toggleX[2] = W * 0.70f
        panel.shader = BitmapShader(Radio.blackCrackle, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    override fun onDraw(c: Canvas) {
        val W = width.toFloat(); val H = height.toFloat()
        c.drawRect(0f, 0f, W, H, panel)
        // Chrome art-deco ribs on the cabinet edges, level with the main dial.
        for (x in listOf(0f, W - 9 * d)) for (k in 0 until 5) {
            val y = dial.top + 8 * d + k * 13 * d
            rib.shader = LinearGradient(0f, y, 0f, y + 12 * d, intArrayOf(Color.WHITE, Color.rgb(0xAA, 0xAA, 0xAA), Color.rgb(0x55, 0x55, 0x55)), null, Shader.TileMode.CLAMP)
            rect.set(x, y, x + 9 * d, y + 12 * d)
            c.drawRoundRect(rect, 3 * d, 3 * d, rib)
        }
        for ((x, y) in listOf(18 * d to 10 * d, W - 18 * d to 10 * d, 18 * d to H - 10 * d, W - 18 * d to H - 10 * d,
            18 * d to H * 0.55f, W - 18 * d to H * 0.55f)) screw(c, x, y)

        // Raised surround and the main dial.
        rect.set(dial.left - 10 * d, dial.top - 8 * d, dial.right + 10 * d, dial.bottom + 10 * d)
        c.drawRoundRect(rect, 14 * d, 14 * d, surround)
        mainDial(c)
        meter(c, sMeter, "S", "SIGNAL", listOf("1", "3", "5", "7", "9", "+20", "+40"), 5, sNeedle)
        meter(c, kpMeter, "Kp", "AURORA", listOf("0", "3", "6", "9"), 2, kpNeedle)
        nameplate(c, W / 2, sMeter.top, sMeter.bottom)

        vernier(c, tuneX, tuneY, tuneR, true, "CHECK NOW")
        bandSwitch(c)
        vernier(c, spreadX, tuneY, tuneR, false, "BAND SPREAD")

        smallKnob(c, W * 0.11f, row2Y, 11 * d, listOf("1", "2", "3", "4", "5"), "TONE")
        for ((i, tg) in toggles.withIndex()) toggle(c, toggleX[i], row2Y, tg.second, tg.first)
        smallKnob(c, W * 0.89f, row2Y, 11 * d, listOf("S", "", "M", "", "B"), "SELECTIVITY")
    }

    // ---- parts

    private fun screw(c: Canvas, x: Float, y: Float) {
        screwPaint.shader = RadialGradient(x - d, y - d, 5 * d, Color.rgb(0xEE, 0xEE, 0xEE), Color.rgb(0x55, 0x55, 0x55), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, 2.6f * d, screwPaint)
        c.drawLine(x - 1.8f * d, y - 0.8f * d, x + 1.8f * d, y + 0.8f * d, slot)
    }

    /** Black bezel and an amber backlit window with a curved bottom edge; returns the window. */
    private fun window(c: Canvas, r: RectF, curve: Float): RectF {
        c.drawRoundRect(r, 8 * d, 8 * d, bezelPaint)
        val w = RectF(r.left + 5 * d, r.top + 5 * d, r.right - 5 * d, r.bottom - 16 * d)
        path.reset()
        path.moveTo(w.left + 6 * d, w.top); path.lineTo(w.right - 6 * d, w.top)
        path.quadTo(w.right, w.top, w.right, w.top + 6 * d); path.lineTo(w.right, w.bottom)
        path.quadTo(w.centerX(), w.bottom - w.height() * curve, w.left, w.bottom)
        path.lineTo(w.left, w.top + 6 * d); path.quadTo(w.left, w.top, w.left + 6 * d, w.top); path.close()
        glass.shader = RadialGradient(w.centerX(), w.top + w.height() * 0.9f, w.width() * 0.9f,
            intArrayOf(Radio.mix(Color.rgb(0x50, 0x38, 0x18), Radio.AMBER_LIGHT, glow),
                Radio.mix(Color.rgb(0x3A, 0x26, 0x10), Radio.AMBER, glow),
                Radio.mix(Color.rgb(0x20, 0x14, 0x08), Radio.AMBER_DARK, glow)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawPath(path, glass)
        return w
    }

    /** Glass sheen and the round boss at the foot of a window. */
    private fun finishWindow(c: Canvas, r: RectF, w: RectF) {
        rect.set(w.left, w.top, w.right, w.top + w.height() * 0.42f)
        c.drawRoundRect(rect, 6 * d, 6 * d, sheen)
        c.drawCircle(r.centerX(), r.bottom - 9 * d, 6 * d, boss)
        c.drawCircle(r.centerX(), r.bottom - 9 * d, 6 * d, bossRim)
    }

    private fun mainDial(c: Canvas) {
        rect.set(dial.left - 3 * d, dial.top - 3 * d, dial.right + 3 * d, dial.bottom + 3 * d)
        c.drawRoundRect(rect, 10 * d, 10 * d, bezelPaint)
        val w = window(c, dial, 0.16f)
        c.save(); c.clipPath(path)
        val px = w.centerX(); val py = w.top + w.height() * 1.30f
        val a0 = -124f; val a1 = -56f
        fun ang(v: Float) = Math.toRadians((a0 + (a1 - a0) * v).toDouble())
        val r0 = w.height() * 1.21f; val step = w.height() * 0.152f
        val rows = listOf(
            "" to listOf("QUIET", "HEADS-UP", "OPEN"),
            "HF" to listOf("80", "40", "20", "10"),
            "6M" to listOf("50", "51", "52", "53", "54"),
            "2M" to listOf("144", "145", "146"),
            "70" to listOf("432", "435", "438"),
        )
        ink.color = Radio.mix(Color.rgb(0x10, 0x08, 0x02), Radio.DIAL_INK, glow)
        for ((i, row) in rows.withIndex()) {
            val r = r0 - i * step
            ink.strokeWidth = (if (i == 0) 1.2f else 0.9f) * d
            rect.set(px - r, py - r, px + r, py + r)
            c.drawArc(rect, a0, a1 - a0, false, ink)
            for (k in 0..24) {
                val a = ang(k / 24f); val len = (if (k % 6 == 0) 2.5f else 1.5f) * d
                c.drawLine(px + r * cos(a).toFloat(), py + r * sin(a).toFloat(), px + (r - len) * cos(a).toFloat(), py + (r - len) * sin(a).toFloat(), ink)
            }
            val labels = row.second
            dialText.textSize = (if (i == 0) 6.4f else 6.6f) * t
            dialText.letterSpacing = if (i == 0) 0.08f else 0f
            for ((k, lab) in labels.withIndex()) {
                val v = if (i == 0) listOf(0.18f, 0.5f, 0.82f)[k] else k / (labels.size - 1f)
                val a = ang(0.06f + v * 0.88f)
                c.drawLine(px + r * cos(a).toFloat(), py + r * sin(a).toFloat(), px + (r - 3.5f * d) * cos(a).toFloat(), py + (r - 3.5f * d) * sin(a).toFloat(), ink)
                val tr = r - step * 0.5f
                c.drawText(lab, px + tr * cos(a).toFloat(), py + tr * sin(a).toFloat() + dialText.textSize * 0.34f, dialText)
            }
            if (row.first.isNotEmpty()) {
                val tr = r - step * 0.5f; val a = ang(-0.035f)
                sans.color = Radio.PANEL_RED; sans.textSize = 5.8f * t; sans.textAlign = Paint.Align.RIGHT
                c.drawText(row.first, px + tr * cos(a).toFloat(), py + tr * sin(a).toFloat() + sans.textSize * 0.34f, sans)
                sans.textAlign = Paint.Align.CENTER
            }
        }
        // Hairline pointer.
        val a = ang(0.06f + pointer * 0.88f)
        c.drawLine(px + (r0 + 6 * d) * cos(a).toFloat(), py + (r0 + 6 * d) * sin(a).toFloat(),
            px + (r0 - 5 * step) * cos(a).toFloat(), py + (r0 - 5 * step) * sin(a).toFloat(), needle)
        c.restore()
        finishWindow(c, dial, w)
    }

    /** Small meter: arc scale, red zone at the top end, a needle pivoting from below. */
    private fun meter(c: Canvas, r: RectF, big: String, small: String, labels: List<String>, redFrom: Int, value: Float) {
        val w = window(c, r, 0.28f)
        c.save(); c.clipPath(path)
        val px = w.centerX(); val py = w.top + w.height() * 1.05f; val rr = w.height() * 0.74f
        val a0 = -126f; val a1 = -54f
        fun ang(v: Float) = Math.toRadians((a0 + (a1 - a0) * v).toDouble())
        ink.color = Radio.mix(Color.rgb(0x10, 0x08, 0x02), Radio.DIAL_INK, glow); ink.strokeWidth = d
        rect.set(px - rr, py - rr, px + rr, py + rr)
        c.drawArc(rect, a0, a1 - a0, false, ink)
        val redStart = redFrom / (labels.size - 1f)
        red.strokeWidth = 2 * d
        rect.set(px - rr - 1.2f * d, py - rr - 1.2f * d, px + rr + 1.2f * d, py + rr + 1.2f * d)
        c.drawArc(rect, a0 + (a1 - a0) * redStart, (a1 - a0) * (1 - redStart), false, red)
        dialText.textSize = 6 * t; dialText.letterSpacing = 0f
        for ((i, lab) in labels.withIndex()) {
            val v = i / (labels.size - 1f); val a = ang(v)
            c.drawLine(px + rr * cos(a).toFloat(), py + rr * sin(a).toFloat(), px + (rr - 4 * d) * cos(a).toFloat(), py + (rr - 4 * d) * sin(a).toFloat(), ink)
            dialText.color = if (i >= redFrom) Radio.PANEL_RED else Radio.DIAL_INK
            val tr = rr + 3 * d + dialText.textSize * 0.6f
            c.drawText(lab, px + tr * cos(a).toFloat(), py + tr * sin(a).toFloat() + dialText.textSize * 0.34f, dialText)
        }
        dialText.color = Radio.DIAL_INK
        dialText.textSize = 8 * t
        c.drawText(big, px, py - rr * 0.55f, dialText)
        sans.color = Radio.DIAL_INK; sans.textSize = 4.6f * t; sans.letterSpacing = 0.15f
        c.drawText(small, px, py - rr * 0.33f, sans)
        sans.letterSpacing = 0f
        val a = ang(value.coerceIn(0f, 1f))
        c.drawLine(px, py, px + (rr + 2 * d) * cos(a).toFloat(), py + (rr + 2 * d) * sin(a).toFloat(), needle)
        c.restore()
        finishWindow(c, r, w)
    }

    private fun nameplate(c: Canvas, x: Float, top: Float, bottom: Float) {
        val maxW = sMeter.let { kpMeter.left - it.right - 8 * d }
        val h = bottom - top
        sans.color = Radio.PANEL_RED; sans.textSize = 7 * t; sans.letterSpacing = 0.3f
        c.drawText("›››  ‹‹‹", x, top + h * 0.14f + sans.textSize * 0.5f, sans)
        script.textSize = 9.5f * t
        fitText(c, "PropWatch", x, top + h * 0.40f, script, maxW)
        sans.textSize = 5.6f * t; sans.letterSpacing = 0.12f
        fitText(c, "DX RECEIVER", x, top + h * 0.57f, sans, maxW)
        white.textSize = 4.8f * t; white.letterSpacing = 0.1f
        fitText(c, "MODEL PW-5", x, top + h * 0.73f, white, maxW)
        whiteScript.textSize = 6 * t
        fitText(c, "the propwatch co.", x, top + h * 0.90f, whiteScript, maxW)
        sans.letterSpacing = 0f; white.letterSpacing = 0f
    }

    /** Big bakelite tuning knob; with [ring], the gold vernier scale behind it turns while checking. */
    private fun vernier(c: Canvas, x: Float, y: Float, r: Float, ring: Boolean, label: String) {
        if (ring) {
            fill.shader = LinearGradient(x - r * 1.5f, y - r * 1.5f, x + r * 1.5f, y + r * 1.5f,
                intArrayOf(Color.rgb(0xE8, 0xD0, 0x8A), Color.rgb(0x8A, 0x6A, 0x2A), Color.rgb(0xD4, 0xB0, 0x60)), null, Shader.TileMode.CLAMP)
            c.drawCircle(x, y, r * 1.45f, fill)
            stroke.color = Color.rgb(0x3A, 0x2A, 0x10); stroke.strokeWidth = 0.6f * d
            dialText.textSize = 3.8f * t; dialText.color = Color.rgb(0x2A, 0x1A, 0x06); dialText.letterSpacing = 0f
            c.save(); c.rotate(ringSpin, x, y)
            for (k in 0 until 100) {
                val a = k / 100.0 * Math.PI * 2; val len = if (k % 10 == 0) r * 0.16f else r * 0.08f
                c.drawLine(x + r * 1.43f * cos(a).toFloat(), y + r * 1.43f * sin(a).toFloat(),
                    x + (r * 1.43f - len) * cos(a).toFloat(), y + (r * 1.43f - len) * sin(a).toFloat(), stroke)
            }
            for (k in 0 until 10) {
                val a = Math.toRadians(k * 36.0 - 90)
                c.drawText("${k * 10}", x + r * 1.17f * cos(a).toFloat(), y + r * 1.17f * sin(a).toFloat() + dialText.textSize * 0.34f, dialText)
            }
            c.restore()
            dialText.color = Radio.DIAL_INK
            // Index mark at the top of the ring.
            fill.shader = null; fill.color = Radio.CREAM
            c.drawRect(x - 0.7f * d, y - r * 1.6f, x + 0.7f * d, y - r * 1.42f, fill)
        }
        fill.shader = null; fill.color = Color.rgb(0x03, 0x03, 0x03)
        c.drawCircle(x, y, r * 1.05f, fill)
        fill.shader = RadialGradient(x - r * 0.35f, y - r * 0.35f, r * 1.4f, Color.rgb(0x3C, 0x3C, 0x3C), Color.rgb(0x06, 0x06, 0x06), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, fill)
        stroke.color = Color.argb(0xE6, 0, 0, 0); stroke.strokeWidth = 3 * d
        for (k in 0..2) {
            val a = Math.toRadians(k * 120.0 - 90)
            c.drawCircle(x + r * 0.62f * cos(a).toFloat(), y + r * 0.62f * sin(a).toFloat(), r * 0.28f, stroke)
        }
        fill.shader = RadialGradient(x - r * 0.15f, y - r * 0.15f, r * 0.5f, Color.rgb(0x33, 0x33, 0x33), Color.BLACK, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r * 0.36f, fill)
        fill.shader = null
        white.textSize = 5.6f * t; white.letterSpacing = 0.08f
        fitText(c, label, x, y + r * 1.5f + 7.5f * t, white, width * 0.32f)
        white.letterSpacing = 0f
    }

    private fun switchAngleFor(i: Int) = if (bands.size < 2) 0f else -150f + 300f * i / (bands.size - 1)

    private fun bandSwitch(c: Canvas) {
        val x = swX; val y = swY; val r = swR
        stroke.color = Radio.CREAM; stroke.strokeWidth = 1.1f * d
        white.textSize = 5.8f * t
        for ((i, b) in bands.withIndex()) {
            val a = Math.toRadians(switchAngleFor(i) - 90.0)
            c.drawLine(x + r * 1.25f * cos(a).toFloat(), y + r * 1.25f * sin(a).toFloat(), x + r * 1.5f * cos(a).toFloat(), y + r * 1.5f * sin(a).toFloat(), stroke)
            val lx = x + r * 2.1f * cos(a).toFloat(); val ly = y + r * 2.1f * sin(a).toFloat()
            c.drawText(b, lx, ly + white.textSize * 0.36f, white)
            bandLamps.getOrNull(i)?.let { col ->
                lamp.shader = RadialGradient(lx - 0.5f * d, ly - white.textSize - 0.5f * d, 3 * d, Color.WHITE, col, Shader.TileMode.CLAMP)
                c.drawCircle(lx, ly - white.textSize * 0.95f, 2.4f * d, lamp)
            }
        }
        fill.color = Color.rgb(0x0A, 0x0A, 0x0A); c.drawCircle(x, y, r * 1.15f, fill)
        fill.shader = RadialGradient(x - r * 0.3f, y - r * 0.3f, r * 1.3f, Color.rgb(0x3A, 0x3A, 0x3A), Color.rgb(0x05, 0x05, 0x05), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r, fill)
        c.save(); c.rotate(switchAngle, x, y)
        fill.shader = LinearGradient(x - r * 0.3f, 0f, x + r * 0.3f, 0f, intArrayOf(Color.rgb(0x11, 0x11, 0x11), Color.rgb(0x44, 0x44, 0x44), Color.rgb(0x11, 0x11, 0x11)), null, Shader.TileMode.CLAMP)
        rect.set(x - r * 0.28f, y - r, x + r * 0.28f, y + r)
        c.drawRoundRect(rect, r * 0.2f, r * 0.2f, fill)
        fill.shader = null; fill.color = Radio.CREAM
        path.reset(); path.moveTo(x, y - r * 0.95f); path.lineTo(x - r * 0.13f, y - r * 0.7f); path.lineTo(x + r * 0.13f, y - r * 0.7f); path.close()
        c.drawPath(path, fill)
        c.restore()
        white.textSize = 5.6f * t; white.letterSpacing = 0.1f
        c.drawText("WAVEBAND", x, y + r * 2.6f + 6 * d, white)
        white.letterSpacing = 0f
    }

    private fun smallKnob(c: Canvas, x: Float, y: Float, r: Float, nums: List<String>, label: String) {
        fill.shader = LinearGradient(x - r * 1.3f, y - r * 1.3f, x + r * 1.3f, y + r * 1.3f,
            intArrayOf(Color.rgb(0xF2, 0xF2, 0xEC), Color.rgb(0x6E, 0x6E, 0x68), Color.rgb(0xBD, 0xBD, 0xB6)), null, Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r * 1.32f, fill)
        fill.shader = null; fill.color = Color.rgb(0x0A, 0x0A, 0x0A)
        c.drawCircle(x, y, r * 1.22f, fill)
        white.textSize = 3.8f * t
        for ((i, n) in nums.withIndex()) {
            val a = Math.toRadians(-220.0 + 260.0 * i / (nums.size - 1))
            c.drawText(n, x + r * 1.05f * cos(a).toFloat(), y + r * 1.05f * sin(a).toFloat() + white.textSize * 0.36f, white)
        }
        fill.shader = RadialGradient(x - r * 0.3f, y - r * 0.3f, r * 1.2f, Color.rgb(0x3A, 0x3A, 0x3A), Color.rgb(0x05, 0x05, 0x05), Shader.TileMode.CLAMP)
        c.drawCircle(x, y, r * 0.8f, fill)
        fill.shader = null
        stroke.color = Radio.CREAM; stroke.strokeWidth = 1.1f * d
        c.drawLine(x + r * 0.35f * cos(-1.0).toFloat(), y + r * 0.35f * sin(-1.0).toFloat(), x + r * 0.75f * cos(-1.0).toFloat(), y + r * 0.75f * sin(-1.0).toFloat(), stroke)
        white.textSize = 5.4f * t; white.letterSpacing = 0.06f
        fitText(c, label, x, y - r * 1.32f - 4 * d, white, width * 0.2f)
        white.letterSpacing = 0f
    }

    private fun toggle(c: Canvas, x: Float, y: Float, on: Boolean, label: String) {
        white.textSize = 5.2f * t; white.letterSpacing = 0.06f
        fitText(c, label, x, y - 13 * d - 4.6f * t, white, width * 0.2f)
        white.textSize = 4.6f * t; white.letterSpacing = 0f
        c.drawText("ON", x, y - 8 * d, white)
        c.drawText("OFF", x, y + 14 * d + 4.6f * t, white)
        fill.shader = LinearGradient(x - 6 * d, y - 6 * d, x + 6 * d, y + 6 * d, Color.rgb(0xF4, 0xF4, 0xEE), Color.rgb(0x77, 0x77, 0x6F), Shader.TileMode.CLAMP)
        c.drawCircle(x, y + 3 * d, 6 * d, fill)
        fill.shader = null; fill.color = Color.rgb(0x22, 0x22, 0x22)
        c.drawCircle(x, y + 3 * d, 3.5f * d, fill)
        stroke.shader = LinearGradient(x - 2 * d, 0f, x + 2 * d, 0f, intArrayOf(Color.rgb(0x88, 0x88, 0x88), Color.WHITE, Color.rgb(0x88, 0x88, 0x88)), null, Shader.TileMode.CLAMP)
        stroke.strokeWidth = 3 * d; stroke.strokeCap = Paint.Cap.ROUND
        c.drawLine(x, y + 3 * d, x, y + 3 * d + (if (on) -9 else 9) * d, stroke)
        stroke.shader = null; stroke.strokeCap = Paint.Cap.BUTT
        if (on) {
            lamp.shader = RadialGradient(x + 9 * d, y - 6 * d, 3 * d, Color.WHITE, Radio.LAMP_ALERT, Shader.TileMode.CLAMP)
            c.drawCircle(x + 9 * d, y - 6 * d, 2 * d, lamp)
        }
    }

    // ---- state changes

    private fun levelPos(level: Int) = when (level) { 2 -> 0.82f; 1 -> 0.5f; else -> 0.18f }
    private fun sPos(level: Int) = when (level) { 2 -> 0.86f; 1 -> 0.64f; 0 -> 0.32f; else -> 0.04f }

    /** Points the dial at a reading: [level] 0 quiet, 1 heads-up, 2 open, -1 no data; [kp] 0..9 or null. */
    override fun show(level: Int, kp: Float?, animated: Boolean) {
        val p = levelPos(level.coerceAtLeast(0)); val s = sPos(level); val k = (kp ?: 0f) / 9f
        if (!animated) { pointer = p; sNeedle = s; kpNeedle = k; invalidate(); return }
        animate(pointer, p, 800, 0, OvershootInterpolator(0.6f)) { pointer = it }
        animate(sNeedle, s, 900, 80, OvershootInterpolator(1.2f)) { sNeedle = it }
        animate(kpNeedle, k, 900, 160, OvershootInterpolator(1.2f)) { kpNeedle = it }
    }

    /** Valve warm-up: the dial lamps glow up, then the pointer and needles swing onto the reading. */
    override fun warmUp(level: Int, kp: Float?) {
        glow = 0f; pointer = 1f; sNeedle = 0f; kpNeedle = 0f; invalidate()
        animate(0f, 1f, 1600, 150) { glow = it }
        animate(1f, levelPos(level.coerceAtLeast(0)), 1100, 1000, OvershootInterpolator(0.6f)) { pointer = it }
        animate(0f, sPos(level), 1000, 1200, OvershootInterpolator(1.2f)) { sNeedle = it }
        animate(0f, (kp ?: 0f) / 9f, 1000, 1300, OvershootInterpolator(1.2f)) { kpNeedle = it }
    }

    /** The vernier ring turns while a check is running. */
    override fun setChecking(on: Boolean) {
        spinner?.cancel(); spinner = null
        if (on) spinner = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 2400; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
            addUpdateListener { ringSpin = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun animateSwitch() {
        val to = switchAngleFor(selected)
        if (!isLaidOut) { switchAngle = to; invalidate(); return }
        animate(switchAngle, to, 350, 0, OvershootInterpolator(1.5f)) { switchAngle = it }
    }

    override fun onDetachedFromWindow() { spinner?.cancel(); super.onDetachedFromWindow() }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN) return true
        if (e.action != MotionEvent.ACTION_UP) return super.onTouchEvent(e)
        val x = e.x; val y = e.y
        when {
            hypot(x - tuneX, y - tuneY) < tuneR * 1.5f -> onCheck?.invoke()
            hypot(x - swX, y - swY) < swR * 2.6f && bands.isNotEmpty() -> {
                // Tap a band label to jump to it; tap the knob itself to step round.
                val dist = hypot(x - swX, y - swY)
                val i = if (dist > swR * 1.3f) {
                    val a = Math.toDegrees(atan2((y - swY).toDouble(), (x - swX).toDouble())) + 90.0
                    val norm = ((a + 540) % 360) - 180   // -180..180, 0 = straight up
                    bands.indices.minByOrNull { kotlin.math.abs(switchAngleFor(it) - norm) } ?: selected
                } else (selected + 1) % bands.size
                if (i != selected) { selected = i; onSelect?.invoke(i) }
            }
            kotlin.math.abs(y - row2Y) < 26 * d -> {
                val i = toggleX.indices.firstOrNull { kotlin.math.abs(x - toggleX[it]) < width * 0.09f }
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
