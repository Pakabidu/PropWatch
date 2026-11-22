package uk.dx.propwatch

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.DrawableWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Wear and tear for a radio that's been used for decades: hairline scratches, scuffs,
 * grime worked into the edges and corners, dust, chipped paint, rubbed-off lettering
 * and the odd coffee ring. Everything is seeded, so each part always shows the same
 * marks instead of new ones every frame.
 */
object Wear {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    /** Hairline scratches, mostly bright (bare metal), mostly running with the panel. */
    fun scratches(c: Canvas, r: RectF, seed: Int, count: Int, d: Float, alpha: Int = 38, colour: Int = Color.WHITE) {
        val rnd = Random(seed)
        p.style = Paint.Style.STROKE; p.shader = null; p.strokeCap = Paint.Cap.ROUND
        repeat(count) {
            val x = r.left + rnd.nextFloat() * r.width(); val y = r.top + rnd.nextFloat() * r.height()
            val len = (5f + rnd.nextFloat() * rnd.nextFloat() * 50f) * d
            val a = (rnd.nextFloat() - 0.5f) * 0.9f + if (rnd.nextInt(5) == 0) 1.4f else 0f
            p.strokeWidth = (0.35f + rnd.nextFloat() * 0.55f) * d
            p.color = if (rnd.nextInt(6) == 0) Color.argb(alpha, 0, 0, 0)
                else Color.argb(alpha, Color.red(colour), Color.green(colour), Color.blue(colour))
            c.drawLine(x, y, x + len * cos(a), y + len * sin(a), p)
        }
    }

    /** Soft scuffs: patches where hands and sleeves have rubbed the finish. */
    fun scuffs(c: Canvas, r: RectF, seed: Int, count: Int, d: Float, alpha: Int = 20, colour: Int = Color.WHITE) {
        val rnd = Random(seed xor 0x5C0FF)
        p.style = Paint.Style.FILL
        repeat(count) {
            val x = r.left + rnd.nextFloat() * r.width(); val y = r.top + rnd.nextFloat() * r.height()
            val rad = (6f + rnd.nextFloat() * 22f) * d
            p.shader = RadialGradient(x, y, rad, Color.argb(alpha, Color.red(colour), Color.green(colour), Color.blue(colour)),
                Color.argb(0, Color.red(colour), Color.green(colour), Color.blue(colour)), Shader.TileMode.CLAMP)
            c.save(); c.scale(1.8f, 0.7f, x, y); c.drawCircle(x, y, rad, p); c.restore()
        }
        p.shader = null
    }

    /** Grime worked into the edges and corners. [depth] is how far it reaches in. */
    fun grime(c: Canvas, r: RectF, d: Float, depth: Float = 14f, alpha: Int = 70) {
        val g = Color.argb(alpha, 0x1A, 0x10, 0x08); val none = Color.argb(0, 0x1A, 0x10, 0x08)
        val k = depth * d
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, r.top, 0f, r.top + k, g, none, Shader.TileMode.CLAMP)
        c.drawRect(r.left, r.top, r.right, r.top + k, p)
        p.shader = LinearGradient(0f, r.bottom, 0f, r.bottom - k, g, none, Shader.TileMode.CLAMP)
        c.drawRect(r.left, r.bottom - k, r.right, r.bottom, p)
        p.shader = LinearGradient(r.left, 0f, r.left + k, 0f, g, none, Shader.TileMode.CLAMP)
        c.drawRect(r.left, r.top, r.left + k, r.bottom, p)
        p.shader = LinearGradient(r.right, 0f, r.right - k, 0f, g, none, Shader.TileMode.CLAMP)
        c.drawRect(r.right - k, r.top, r.right, r.bottom, p)
        p.shader = null
    }

    /** Dust: tiny pale specks, thicker towards the bottom where it settles. */
    fun dust(c: Canvas, r: RectF, seed: Int, count: Int, d: Float, alpha: Int = 70) {
        val rnd = Random(seed xor 0xD057)
        p.style = Paint.Style.FILL; p.shader = null
        repeat(count) {
            val x = r.left + rnd.nextFloat() * r.width()
            val y = r.top + (1f - rnd.nextFloat() * rnd.nextFloat()) * r.height()
            p.color = Color.argb(rnd.nextInt(alpha / 3, alpha), 0xD8, 0xD0, 0xC0)
            c.drawCircle(x, y, (0.3f + rnd.nextFloat() * 0.8f) * d, p)
        }
    }

    /** Chipped paint: small irregular flakes showing what's underneath, mostly near the edges. */
    fun chips(c: Canvas, r: RectF, seed: Int, count: Int, d: Float, colour: Int) {
        val rnd = Random(seed xor 0xC41)
        val path = Path()
        p.style = Paint.Style.FILL; p.shader = null; p.color = colour
        repeat(count) {
            // Pull most chips towards an edge.
            val edge = rnd.nextInt(4)
            val t = rnd.nextFloat()
            val inset = rnd.nextFloat() * rnd.nextFloat() * 16 * d
            val x = when (edge) { 0 -> r.left + inset; 1 -> r.right - inset; else -> r.left + t * r.width() }
            val y = when (edge) { 2 -> r.top + inset; 3 -> r.bottom - inset; else -> r.top + t * r.height() }
            val s = (0.8f + rnd.nextFloat() * 2.2f) * d
            path.reset()
            for (k in 0 until 6) {
                val a = k / 6.0 * Math.PI * 2; val rr = s * (0.5f + rnd.nextFloat())
                val px = x + rr * cos(a).toFloat(); val py = y + rr * sin(a).toFloat()
                if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
            }
            path.close()
            c.drawPath(path, p)
        }
    }

    /** A faint coffee-cup ring: someone always puts a mug on the radio. */
    fun stain(c: Canvas, cx: Float, cy: Float, radius: Float, d: Float) {
        p.shader = null
        p.style = Paint.Style.FILL; p.color = Color.argb(14, 0x8A, 0x5A, 0x28)
        c.drawCircle(cx, cy, radius, p)
        p.style = Paint.Style.STROKE; p.color = Color.argb(46, 0x8A, 0x5A, 0x28)
        p.strokeWidth = 2.2f * d
        c.drawArc(cx - radius, cy - radius, cx + radius, cy + radius, 20f, 290f, false, p)
        p.strokeWidth = 1f * d; p.color = Color.argb(30, 0x8A, 0x5A, 0x28)
        c.drawArc(cx - radius * 0.93f, cy - radius * 0.93f, cx + radius * 0.93f, cy + radius * 0.93f, 200f, 120f, false, p)
    }

    /** Rubbed-off lettering: a few streaks of the background colour across engraved text. */
    fun rub(c: Canvas, r: RectF, seed: Int, d: Float, colour: Int, count: Int = 3) {
        val rnd = Random(seed xor 0x7B)
        p.style = Paint.Style.STROKE; p.shader = null; p.strokeCap = Paint.Cap.ROUND
        repeat(count) {
            p.color = Color.argb(rnd.nextInt(60, 140), Color.red(colour), Color.green(colour), Color.blue(colour))
            p.strokeWidth = (0.8f + rnd.nextFloat() * 1.5f) * d
            val y = r.top + rnd.nextFloat() * r.height(); val x = r.left + rnd.nextFloat() * r.width() * 0.7f
            c.drawLine(x, y, x + (8 + rnd.nextFloat() * 20) * d, y + (rnd.nextFloat() - 0.5f) * 3 * d, p)
        }
    }

    /** Standard treatment for a metal or painted panel. */
    fun panel(c: Canvas, r: RectF, seed: Int, d: Float, heavy: Boolean = false) {
        val area = r.width() * r.height() / (d * d)
        scuffs(c, r, seed, (area / 9000).toInt().coerceIn(2, 14), d)
        scratches(c, r, seed, (area / (if (heavy) 900 else 1600)).toInt().coerceIn(4, 70), d)
        dust(c, r, seed, (area / 250).toInt().coerceIn(10, 400), d)
        grime(c, r, d)
    }
}

/**
 * Ages any background: draws it, then worn spots over it (a thumb-polished centre on
 * buttons, scratches, chips on the rim and grime at the edges). State changes such as
 * "pressed" pass straight through to the wrapped drawable.
 */
class WornDrawable(base: Drawable, private val seed: Int, private val d: Float, private val button: Boolean) : DrawableWrapper(base) {
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun draw(c: Canvas) {
        super.draw(c)
        r.set(bounds)
        if (r.isEmpty) return
        c.save(); c.clipRect(r)
        if (button) {
            // Polished where thumbs have pressed it for years.
            shine.shader = RadialGradient(r.centerX(), r.centerY(), r.height() * 0.9f,
                Color.argb(26, 255, 240, 220), Color.argb(0, 255, 240, 220), Shader.TileMode.CLAMP)
            c.save(); c.scale(2.2f, 1f, r.centerX(), r.centerY()); c.drawCircle(r.centerX(), r.centerY(), r.height() * 0.9f, shine); c.restore()
            Wear.chips(c, r, seed, 5, d, Color.argb(160, 0x2A, 0x26, 0x22))   // nicks in the chrome rim
            Wear.scratches(c, r, seed, 5, d, alpha = 30)
            Wear.grime(c, r, d, depth = 6f, alpha = 60)
        } else {
            Wear.scratches(c, r, seed, 6, d, alpha = 24)
            Wear.grime(c, r, d, depth = 10f, alpha = 50)
        }
        c.restore()
    }
}

/** A warm glow a part gives off when the mood light is on: a rect in the part's own coordinates. */
data class Glow(val rect: RectF, val colour: Int = Mood.AMBER, val strength: Float = 1f)

/** A part with lit screens, dials or lamps that glow when the mood light is on. */
interface MoodGlow {
    fun glows(): List<Glow>
}

/** Mood-light state and the warm palette used for it. */
object Mood {
    val AMBER = Color.rgb(0xFF, 0x9A, 0x3C)
    val LAMP = Color.rgb(0xFF, 0xB8, 0x60)
    var on = false

    /** Applies or clears the backlit look on ordinary buttons and text (amber lettering with a glow). */
    fun styleText(root: View, d: Float) {
        if (root is ViewGroup) for (i in 0 until root.childCount) styleText(root.getChildAt(i), d)
        if (root is Button) {
            if (on) { root.setShadowLayer(9 * d, 0f, 0f, AMBER); root.setTextColor(LAMP) }
            else { root.setShadowLayer(0f, 0f, 0f, 0); root.setTextColor(Radio.CREAM) }
        }
    }

    /** Redraws every part so draw-time mood changes (warmer dials and lamps) show at once. */
    fun invalidateAll(v: View) {
        v.invalidate()
        if (v is ViewGroup) for (i in 0 until v.childCount) invalidateAll(v.getChildAt(i))
    }
}

/**
 * The mood light: laid over the whole screen and fixed to it (it doesn't scroll), it
 * darkens the room as if the only light were a table lamp just off the top corner, then
 * adds the warm orange glow of every lit dial, meter, lamp and button underneath.
 * It never takes touches.
 */
class MoodLightView(ctx: Context, private val content: View) : View(ctx) {
    private val d = resources.displayMetrics.density
    private var fade = if (Mood.on) 1f else 0f
    private var anim: ValueAnimator? = null
    private val dark = Paint()
    private val spill = Paint()
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val here = IntArray(2)
    private val there = IntArray(2)
    private val gr = RectF()

    init { isClickable = false; isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    /** Fades the room light down (on) or back up (off). */
    fun set(on: Boolean) {
        anim?.cancel()
        anim = ValueAnimator.ofFloat(fade, if (on) 1f else 0f).apply {
            duration = 700
            addUpdateListener { fade = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(c: Canvas) {
        if (fade <= 0.01f) return
        val w = width.toFloat(); val h = height.toFloat()
        val lx = w * 0.9f; val ly = -h * 0.06f
        // The dark room, warmest and brightest near the lamp.
        dark.shader = RadialGradient(lx, ly, h * 1.15f,
            intArrayOf(Color.argb((40 * fade).toInt(), 0x30, 0x18, 0x06), Color.argb((150 * fade).toInt(), 0x14, 0x0A, 0x04),
                Color.argb((205 * fade).toInt(), 0x06, 0x03, 0x01)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, dark)
        spill.shader = RadialGradient(lx, ly, w * 0.95f, Color.argb((70 * fade).toInt(), 0xFF, 0xB0, 0x60),
            Color.argb(0, 0xFF, 0xB0, 0x60), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, spill)

        // Glow from everything lit, wherever it currently is on screen.
        getLocationInWindow(here)
        drawGlows(c, content, w, h)
    }

    private fun drawGlows(c: Canvas, v: View, w: Float, h: Float) {
        if (v.visibility != VISIBLE) return
        if (v is MoodGlow || v is Button) {
            v.getLocationInWindow(there)
            val ox = (there[0] - here[0]).toFloat(); val oy = (there[1] - here[1]).toFloat()
            if (oy < h && oy + v.height > 0) {
                val glows = if (v is MoodGlow) v.glows()
                    else listOf(Glow(RectF(0f, 0f, v.width.toFloat(), v.height.toFloat()), Mood.AMBER, 0.45f))
                for (g in glows) {
                    gr.set(g.rect); gr.offset(ox, oy)
                    val rad = maxOf(gr.width(), gr.height()) * 0.75f + 10 * d
                    val a = (110 * g.strength * fade).toInt().coerceIn(0, 255)
                    glow.shader = RadialGradient(gr.centerX(), gr.centerY(), rad,
                        intArrayOf(Color.argb(a, Color.red(g.colour), Color.green(g.colour), Color.blue(g.colour)),
                            Color.argb(a / 3, Color.red(g.colour), Color.green(g.colour), Color.blue(g.colour)),
                            Color.argb(0, Color.red(g.colour), Color.green(g.colour), Color.blue(g.colour))),
                        floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
                    c.save(); c.scale(1f, (gr.height() / gr.width()).coerceIn(0.35f, 1f) + 0.25f, gr.centerX(), gr.centerY())
                    c.drawCircle(gr.centerX(), gr.centerY(), rad, glow)
                    c.restore()
                }
            }
        }
        if (v is ViewGroup) for (i in 0 until v.childCount) drawGlows(c, v.getChildAt(i), w, h)
    }
}
