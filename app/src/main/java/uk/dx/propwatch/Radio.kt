package uk.dx.propwatch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The 1940s portable shortwave look: black alligator-grain case, leather handle,
 * chrome trim, a black dial with gold scales, and a lid with a cream-on-black
 * log chart and operating instructions.
 */
object Radio {
    /**
     * Cabinet styles. PORTABLE: the 1940s portable shortwave set. RECEIVER: a 1940s
     * communications receiver: black crackle panel, amber backlit dials, chrome trim.
     * Chosen in Settings; the activity is recreated when it changes.
     */
    enum class Skin { PORTABLE, RECEIVER, AIRCRAFT }
    var skin = Skin.PORTABLE
    val receiver get() = skin == Skin.RECEIVER
    /** WW2 aircraft wireless set: black panel, cream semicircular dial, brass label plates, colour-coded controls. */
    val aircraft get() = skin == Skin.AIRCRAFT

    val LEATHER = Color.rgb(0x16, 0x13, 0x11)
    val PANEL get() = when (skin) {   // lid panel
        Skin.RECEIVER -> Color.rgb(0x0C, 0x0D, 0x10); Skin.AIRCRAFT -> Color.rgb(0x13, 0x13, 0x13); else -> Color.rgb(0x0E, 0x0D, 0x0C)
    }
    /** Scale and label accent: gold on the portable, warm amber on the receiver, brass on the aircraft set. */
    val GOLD get() = when (skin) {
        Skin.RECEIVER -> Color.rgb(0xF2, 0xA8, 0x48); Skin.AIRCRAFT -> Color.rgb(0xD9, 0xB3, 0x6A); else -> Color.rgb(0xD9, 0xB5, 0x6A)
    }
    // Aircraft set: brass plates and colour-coded controls.
    val PLATE_LIGHT = Color.rgb(0xE6, 0xC8, 0x77)
    val PLATE = Color.rgb(0xA8, 0x82, 0x3A)
    val PLATE_DARK = Color.rgb(0x6E, 0x54, 0x20)
    val CODE_BLUE = Color.rgb(0x5E, 0x8F, 0xC8)
    val CODE_RED = Color.rgb(0xD8, 0x34, 0x2A)
    val CODE_YELLOW = Color.rgb(0xF2, 0xC2, 0x1E)
    val CODE_ORANGE = Color.rgb(0xE8, 0x86, 0x2A)
    val DIAL_CREAM = Color.rgb(0xF6, 0xEE, 0xD2)
    val BRASS get() = GOLD
    val AMBER = Color.rgb(0xF0, 0xA0, 0x3A)           // receiver dial backlight
    val AMBER_LIGHT = Color.rgb(0xFF, 0xD2, 0x7A)
    val AMBER_DARK = Color.rgb(0xA8, 0x5A, 0x14)
    val DIAL_INK = Color.rgb(0x2A, 0x16, 0x06)        // printing on the amber dials
    val PANEL_RED = Color.rgb(0xC8, 0x35, 0x2A)       // receiver nameplate red
    val BRASS_DARK = Color.rgb(0x8A, 0x6A, 0x2E)
    val CHROME = Color.rgb(0xC8, 0xC8, 0xC2)
    val CHROME_DARK = Color.rgb(0x6E, 0x6E, 0x68)
    /** Engraved lettering: cream on the portable, white on the receiver's black crackle. */
    val CREAM get() = when (skin) {
        Skin.RECEIVER -> Color.rgb(0xEC, 0xE8, 0xDC); Skin.AIRCRAFT -> Color.rgb(0xF2, 0xEE, 0xE2); else -> Color.rgb(0xF0, 0xE4, 0xC4)
    }
    val PAPER get() = when (skin) {   // log-chart paper
        Skin.RECEIVER -> AMBER_LIGHT; Skin.AIRCRAFT -> Color.rgb(0xF4, 0xF1, 0xE6); else -> Color.rgb(0xE8, 0xDC, 0xB8)
    }
    val COPPER = Color.rgb(0xB0, 0x7A, 0x4A)       // aged handle brackets and latches
    val COPPER_DARK = Color.rgb(0x5E, 0x3A, 0x20)
    val INK = Color.rgb(0x1A, 0x16, 0x12)

    // Pilot-lamp colours. Green is the "magic eye" tuning-indicator glow.
    val LAMP_ALERT = Color.rgb(0x7C, 0xFF, 0x9B)
    val LAMP_HEADSUP = Color.rgb(0xFF, 0xB3, 0x47)
    val LAMP_ERROR = Color.rgb(0xFF, 0x5A, 0x4A)
    val LAMP_OFF = Color.rgb(0x5E, 0x58, 0x4E)

    val SERIF: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    val SERIF_ITALIC: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
    val MONO: Typeface = Typeface.MONOSPACE

    fun dp(ctx: Context, v: Float) = v * ctx.resources.displayMetrics.density

    /**
     * Black alligator-grain leatherette: irregular scales (a jittered Voronoi pattern)
     * with fine, slightly brown cracks between them. Tiles seamlessly.
     */
    fun leatherette(ctx: Context): BitmapDrawable =
        BitmapDrawable(ctx.resources, grain).apply { setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }

    /** Built once per process: alligator scales with worn brown cracks. */
    val grain: Bitmap by lazy {
        cells(160, 9, 7, Color.rgb(0x17, 0x14, 0x12), Color.rgb(0x7A, 0x52, 0x2E), 0.9f, 1.6f, 6, 6f)
    }

    /** Receiver front panel: black crackle finish with fine light-grey cracks. */
    val blackCrackle: Bitmap by lazy {
        cells(144, 15, 21, Color.rgb(0x12, 0x13, 0x17), Color.rgb(0x8C, 0x92, 0x9A), 0.75f, 1.1f, 3, 2f)
    }

    /** Receiver cabinet and speaker: dark grey wrinkle finish. */
    val greyCrackle: Bitmap by lazy {
        cells(128, 18, 33, Color.rgb(0x3A, 0x3C, 0x3D), Color.rgb(0x1E, 0x20, 0x21), 0.6f, 1.2f, 4, 3f)
    }

    fun tile(ctx: Context, bmp: Bitmap): BitmapDrawable =
        BitmapDrawable(ctx.resources, bmp).apply { setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }

    /** Aircraft set: worn black stove-enamel panel, fine speckle and light scratches. */
    val blackEnamel: Bitmap by lazy {
        val n = 160
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(0x15, 0x15, 0x15))
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = Random(44)
        repeat(900) {
            p.color = if (r.nextBoolean()) Color.argb(r.nextInt(8, 22), 255, 255, 255) else Color.argb(r.nextInt(10, 30), 0, 0, 0)
            c.drawRect(r.nextFloat() * n, r.nextFloat() * n, r.nextFloat() * n + 1, r.nextFloat() * n + 1, p)
        }
        p.strokeWidth = 0.6f
        repeat(14) {   // light scratches, drawn wrapped so the tile repeats cleanly
            p.color = Color.argb(r.nextInt(14, 30), 255, 255, 255)
            val x = r.nextFloat() * n; val y = r.nextFloat() * n
            val dx = r.nextFloat() * 40 - 20; val dy = r.nextFloat() * 8 - 4
            for (ox in listOf(-n, 0, n)) for (oy in listOf(-n, 0, n)) c.drawLine(x + ox, y + oy, x + ox + dx, y + oy + dy, p)
        }
        bmp
    }

    /** The whole-screen background for the current cabinet. */
    fun background(ctx: Context) = tile(ctx, when (skin) {
        Skin.RECEIVER -> greyCrackle; Skin.AIRCRAFT -> blackEnamel; else -> grain
    })

    /**
     * A seamless tile of irregular cells (a jittered Voronoi pattern): [base] cells with
     * [crack]-coloured lines between them. [width] is the crack width in pixels, [shadeVar]
     * the per-cell brightness variation and [dome] how much lighter the cell centres are.
     */
    private fun cells(n: Int, cells: Int, seed: Int, base: Int, crack: Int, strength: Float, width: Float,
                      shadeVar: Int, dome: Float): Bitmap {
        val step = n / cells.toFloat()
        val r = Random(seed)
        val px = Array(cells) { FloatArray(cells) }
        val py = Array(cells) { FloatArray(cells) }
        val sh = Array(cells) { FloatArray(cells) }
        for (gy in 0 until cells) for (gx in 0 until cells) {
            px[gy][gx] = (gx + 0.15f + r.nextFloat() * 0.7f) * step
            py[gy][gx] = (gy + 0.15f + r.nextFloat() * 0.7f) * step
            sh[gy][gx] = r.nextInt(-shadeVar, shadeVar + 1).toFloat()
        }
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        for (y in 0 until n) for (x in 0 until n) {
            val cx = (x / step).toInt(); val cy = (y / step).toInt()
            var d1 = Float.MAX_VALUE; var d2 = Float.MAX_VALUE; var shade = 0f
            for (oy in -1..1) for (ox in -1..1) {
                // Wrap around the tile so the texture repeats without seams.
                val gx = cx + ox; val gy = cy + oy
                val wx = ((gx % cells) + cells) % cells; val wy = ((gy % cells) + cells) % cells
                val sx = px[wy][wx] + (gx - wx) * step; val sy = py[wy][wx] + (gy - wy) * step
                val dx = x - sx; val dy = y - sy
                val dd = dx * dx + dy * dy
                if (dd < d1) { d2 = d1; d1 = dd; shade = sh[wy][wx] } else if (dd < d2) d2 = dd
            }
            val k = (1f - (sqrt(d2) - sqrt(d1)) / width).coerceIn(0f, 1f)
            val dm = (sqrt(d1) / step).coerceIn(0f, 1f) * dome
            val v = (shade - dm + r.nextInt(-3, 4)).toInt()
            val b = Color.rgb((Color.red(base) + v).coerceIn(0, 255), (Color.green(base) + v).coerceIn(0, 255),
                (Color.blue(base) + v).coerceIn(0, 255))
            bmp.setPixel(x, y, mix(b, crack, k * strength))
        }
        return bmp
    }

    /** Translucent light and dark blotches, tiled over the chrome plates to look etched and aged. */
    val mottle: Bitmap by lazy {
        val n = 128
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = Random(5)
        repeat(260) {
            val light = r.nextBoolean()
            p.color = if (light) Color.argb(r.nextInt(14, 34), 0xFF, 0xFF, 0xFA) else Color.argb(r.nextInt(14, 30), 0x5A, 0x58, 0x50)
            val x = r.nextFloat() * n; val y = r.nextFloat() * n; val rad = 1.5f + r.nextFloat() * 4.5f
            // Draw wrapped copies so the tile repeats without seams.
            for (ox in listOf(-n, 0, n)) for (oy in listOf(-n, 0, n)) {
                c.drawOval(x + ox - rad, y + oy - rad * 0.7f, x + ox + rad, y + oy + rad * 0.7f, p)
            }
        }
        bmp
    }

    fun mix(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt())

    /** Black square push-button with a chrome rim; darkens when pressed. */
    private var buttonSeed = 0

    /** Black push-button with a chrome rim, darkening when pressed, and worn from years of use. */
    fun buttonBg(ctx: Context, big: Boolean = true): Drawable =
        WornDrawable(plainButtonBg(ctx, big), 97 + buttonSeed++ * 13, dp(ctx, 1f), button = true)

    private fun plainButtonBg(ctx: Context, big: Boolean): StateListDrawable {
        val radius = dp(ctx, 4f)
        val stroke = dp(ctx, if (big) 2f else 1f).toInt()
        fun g(top: Int, bottom: Int, rim: Int) = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)
        ).apply { cornerRadius = radius; setStroke(stroke, rim) }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed),
                g(Color.rgb(0x05, 0x05, 0x05), Color.rgb(0x1E, 0x1C, 0x1A), CREAM))
            addState(intArrayOf(-android.R.attr.state_enabled),
                g(Color.rgb(0x1A, 0x18, 0x16), Color.rgb(0x12, 0x11, 0x10), CHROME_DARK))
            addState(intArrayOf(),
                g(Color.rgb(0x34, 0x31, 0x2E), Color.rgb(0x0C, 0x0B, 0x0A), if (big) CHROME else CHROME_DARK))
        }
    }

    /**
     * The open lid: a black instruction panel with a double cream rule, set in a
     * leatherette frame, with a screw in each corner.
     */
    class LidDrawable(private val ctx: Context) : Drawable() {
        private val d = ctx.resources.displayMetrics.density
        private val framePaint = Paint().apply {
            shader = android.graphics.BitmapShader(when (skin) {
                Skin.RECEIVER -> greyCrackle; Skin.AIRCRAFT -> blackEnamel; else -> grain
            }, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            if (receiver) shader = android.graphics.BitmapShader(blackCrackle, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            else color = PANEL
        }
        private val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = CREAM }
        private val screw = Paint(Paint.ANTI_ALIAS_FLAG)
        private val slot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x30, 0x30, 0x2C); strokeWidth = 1.2f * d }
        private val gap = Paint(Paint.ANTI_ALIAS_FLAG).apply { alpha = 200 }

        override fun draw(c: Canvas) {
            val b = RectF(bounds)
            c.drawRoundRect(b, 10 * d, 10 * d, framePaint)
            val inner = RectF(b.left + 9 * d, b.top + 9 * d, b.right - 9 * d, b.bottom - 9 * d)
            c.drawRoundRect(inner, 3 * d, 3 * d, fill)
            rule.strokeWidth = 1.4f * d
            c.drawRect(inner.left + 5 * d, inner.top + 5 * d, inner.right - 5 * d, inner.bottom - 5 * d, rule)
            rule.strokeWidth = 0.6f * d
            c.drawRect(inner.left + 8 * d, inner.top + 8 * d, inner.right - 8 * d, inner.bottom - 8 * d, rule)
            // A lid that's been opened and closed for decades: scuffs, scratches, a mug ring,
            // rules worn away in places, grime in the corners.
            c.save(); c.clipRect(inner)
            Wear.panel(c, inner, 81, d, heavy = true)
            Wear.stain(c, inner.right - 70 * d, inner.top + 150 * d, 28 * d, d)
            gap.color = PANEL
            val rnd = Random(82)
            repeat(10) {   // rubbed-off stretches of the cream rule
                val horiz = rnd.nextBoolean()
                val f = rnd.nextFloat(); val len = (8 + rnd.nextFloat() * 30) * d
                if (horiz) {
                    val y = if (rnd.nextBoolean()) inner.top + 5 * d else inner.bottom - 5 * d
                    val x = inner.left + f * inner.width()
                    c.drawRect(x, y - 1.5f * d, x + len, y + 4.5f * d, gap)
                } else {
                    val x = if (rnd.nextBoolean()) inner.left + 5 * d else inner.right - 5 * d
                    val y = inner.top + f * inner.height()
                    c.drawRect(x - 1.5f * d, y, x + 4.5f * d, y + len, gap)
                }
            }
            c.restore()
            c.save(); c.clipRect(b)
            Wear.chips(c, b, 83, 30, d, Color.argb(120, 0x6A, 0x50, 0x38))   // frame leather rubbed through
            c.restore()
            for ((x, y) in listOf(inner.left to inner.top, inner.right to inner.top,
                inner.left to inner.bottom, inner.right to inner.bottom)) {
                Hardware.rustRing(c, x, y, d, 4.2f)
                screw.shader = android.graphics.RadialGradient(x - 1.5f * d, y - 1.5f * d, 5 * d,
                    CHROME, CHROME_DARK, Shader.TileMode.CLAMP)
                c.drawCircle(x, y, 4.2f * d, screw)
                c.drawLine(x - 2.6f * d, y + 1.2f * d, x + 2.6f * d, y - 1.2f * d, slot)
            }
        }

        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(cf: ColorFilter?) {}
        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
