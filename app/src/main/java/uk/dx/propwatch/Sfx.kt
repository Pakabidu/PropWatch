package uk.dx.propwatch

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * WW2-era radio sounds: a bakelite toggle snap for main buttons, a lighter rotary
 * detent for link buttons, and a valve-set warm-up for app start. All sounds are
 * original, synthesised for PropWatch. Respects the "Sounds" setting.
 */
object Sfx {
    private var pool: SoundPool? = null
    private var click = 0
    private var detent = 0
    private var startup = 0
    private val loaded = mutableSetOf<Int>()
    private var pendingStartup = false

    fun init(ctx: Context) {
        if (pool != null) return
        val p = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            ).build()
        p.setOnLoadCompleteListener { sp, id, status ->
            if (status == 0) {
                loaded += id
                if (id == startup && pendingStartup) {
                    pendingStartup = false
                    sp.play(id, 0.8f, 0.8f, 1, 0, 1f)
                }
            }
        }
        val app = ctx.applicationContext
        click = p.load(app, R.raw.click, 1)
        detent = p.load(app, R.raw.detent, 1)
        startup = p.load(app, R.raw.startup, 1)
        pool = p
    }

    private fun play(ctx: Context, id: Int, vol: Float) {
        if (!Store.sounds(ctx)) return
        if (id in loaded) pool?.play(id, vol, vol, 1, 0, 1f)
    }

    fun click(ctx: Context) = play(ctx, click, 1f)
    fun detent(ctx: Context) = play(ctx, detent, 0.8f)

    /** Plays once the sample has loaded (it may still be loading on first launch). */
    fun startup(ctx: Context) {
        if (!Store.sounds(ctx)) return
        if (startup in loaded) pool?.play(startup, 0.8f, 0.8f, 1, 0, 1f) else pendingStartup = true
    }
}
