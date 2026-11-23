package uk.dx.propwatch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** A station on the little broadcast set: what's printed on its button, and where it streams from. */
data class RadioStation(val name: String, val label: String, val url: String?)

/** The broadcast set's stations: two old-time stations and one the user sets up. */
object Stations {
    const val CUSTOM = 2

    fun all(ctx: Context): List<RadioStation> = listOf(
        RadioStation("The 1920s Radio Network", "1920s", "http://65.108.124.70:8181/stream"),
        RadioStation("Old Time Radio Service", "OLD TIME", "http://65.108.105.26:8516/stream"),
        RadioStation(Store.customStationName(ctx) ?: "My station", "MY STATION", Store.customStationUrl(ctx)),
    )

    /** A stream address worth trying: http or https with a host. */
    fun valid(url: String): Boolean = Regex("^https?://[^\\s/:]+(:\\d+)?(/\\S*)?$", RegexOption.IGNORE_CASE).matches(url.trim())
}

/**
 * Plays the broadcast set in the background, so the music carries on while the phone
 * does other things. Shows a notification with a Stop button while it plays. Uses
 * Media3 ExoPlayer, which copes with Shoutcast/Icecast MP3 and AAC streams, takes
 * audio focus (pausing for calls and other apps) and stops if headphones are pulled out.
 * It also measures how loud the stream is, for the meter on the broadcast set.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class StreamService : Service() {

    /** What the set is doing, for the dial on screen. */
    enum class State { OFF, TUNING, PLAYING, ERROR }

    companion object {
        private const val ACTION_PLAY = "uk.dx.propwatch.radio.PLAY"
        private const val ACTION_STOP = "uk.dx.propwatch.radio.STOP"
        private const val ACTION_SOUND = "uk.dx.propwatch.radio.SOUND"
        private const val EXTRA_INDEX = "index"
        private const val EXTRA_URL = "url"
        private const val EXTRA_NAME = "name"
        private const val CHANNEL = "radio"
        private const val NOTE_ID = 7

        // Shared with the UI (main thread only).
        var state = State.OFF; private set
        var station = -1; private set
        var error: String? = null; private set
        private val listeners = mutableSetOf<() -> Unit>()
        fun listen(l: () -> Unit) { listeners += l }
        fun unlisten(l: () -> Unit) { listeners -= l }
        private fun set(s: State, index: Int = station, err: String? = null) {
            state = s; station = index; error = err
            listeners.toList().forEach { it() }
        }

        fun play(ctx: Context, index: Int, s: RadioStation) {
            val url = s.url ?: return
            set(State.TUNING, index)
            ContextCompat.startForegroundService(ctx, Intent(ctx, StreamService::class.java)
                .setAction(ACTION_PLAY).putExtra(EXTRA_INDEX, index).putExtra(EXTRA_URL, url).putExtra(EXTRA_NAME, s.name))
        }

        /**
         * The SOUND switch changed: mute or unmute the set. It keeps playing silently while
         * muted, so it comes straight back without re-tuning.
         */
        fun soundChanged(ctx: Context) {
            if (state == State.OFF || state == State.ERROR) return
            ctx.startService(Intent(ctx, StreamService::class.java).setAction(ACTION_SOUND))
        }

        // The meter: loudness (RMS, 0..1) of the decoded audio, by stream position in ms.
        // Written on the playback thread, read on the main thread.
        private const val METER_SIZE = 512
        private val meterLock = Any()
        private val meterMs = LongArray(METER_SIZE)
        private val meterLevel = FloatArray(METER_SIZE)
        private var meterHead = 0
        private var meterCount = 0
        private var current: StreamService? = null

        private fun record(ms: Long, level: Float) = synchronized(meterLock) {
            meterMs[meterHead] = ms; meterLevel[meterHead] = level
            meterHead = (meterHead + 1) % METER_SIZE
            meterCount = minOf(meterCount + 1, METER_SIZE)
        }

        private fun clearMeter() = synchronized(meterLock) { meterCount = 0; meterHead = 0 }

        /**
         * How loud the stream is right now (RMS, 0..1), before the SOUND switch's volume.
         * Audio is decoded ahead of the speaker, so this looks up the level at the position
         * actually playing. Main thread only.
         */
        fun level(): Float {
            val p = current?.player ?: return 0f
            if (!p.isPlaying) return 0f
            val pos = p.currentPosition
            synchronized(meterLock) {
                for (k in 1..meterCount) {
                    val i = (meterHead - k + METER_SIZE) % METER_SIZE
                    if (meterMs[i] <= pos) return meterLevel[i]
                }
            }
            return 0f
        }

        fun stop(ctx: Context) {
            if (state == State.OFF) return
            ctx.startService(Intent(ctx, StreamService::class.java).setAction(ACTION_STOP))
        }
    }

    private var player: ExoPlayer? = null
    private var name = ""
    private val main = Handler(Looper.getMainLooper())

    /** Measures each decoded buffer on its way to the speaker. */
    private class Meter : TeeAudioProcessor.AudioBufferSink {
        private var rate = 44_100
        private var channels = 2
        private var encoding = C.ENCODING_PCM_16BIT
        private var frames = 0L

        override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
            rate = sampleRateHz.coerceAtLeast(1); channels = channelCount.coerceAtLeast(1); this.encoding = encoding
            frames = 0L
            clearMeter()
        }

        override fun handleBuffer(buffer: ByteBuffer) {
            val b = buffer.duplicate().order(ByteOrder.nativeOrder())
            var sum = 0.0; var samples = 0; var n = 0
            when (encoding) {
                C.ENCODING_PCM_16BIT -> {
                    n = b.remaining() / 2
                    var i = 0
                    while (i < n) { val v = b.getShort(b.position() + i * 2) / 32768.0; sum += v * v; samples++; i += 4 }
                }
                C.ENCODING_PCM_FLOAT -> {
                    n = b.remaining() / 4
                    var i = 0
                    while (i < n) { val v = b.getFloat(b.position() + i * 4).toDouble(); sum += v * v; samples++; i += 4 }
                }
                else -> return
            }
            if (samples == 0) return
            record(frames * 1000 / rate, sqrt(sum / samples).toFloat())
            frames += n / channels
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() { super.onCreate(); current = this }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                name = intent.getStringExtra(EXTRA_NAME) ?: "Radio"
                val index = intent.getIntExtra(EXTRA_INDEX, -1)
                startInForeground("Tuning in…")
                tune(index, url)
            }
            ACTION_STOP -> shutDown()
            ACTION_SOUND -> { applySound(); update() }
            else -> shutDown()
        }
        return START_NOT_STICKY
    }

    private fun tune(index: Int, url: String) {
        val p = player ?: ExoPlayer.Builder(this, metered()).build().also { p ->
            p.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)   // handle audio focus
            p.setHandleAudioBecomingNoisy(true)                              // headphones out: pause
            p.setWakeMode(C.WAKE_MODE_NETWORK)                               // keep streaming with the screen off
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(s: Int) = update()
                override fun onIsPlayingChanged(playing: Boolean) = update()
                override fun onPlayerError(e: PlaybackException) {
                    main.post {
                        set(State.ERROR, err = "Couldn't tune in: " + (e.cause?.message ?: e.errorCodeName))
                        shutDown(keepError = true)
                    }
                }
            })
            player = p
        }
        set(State.TUNING, index)
        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
        p.playWhenReady = true
        applySound()
    }

    /** ExoPlayer's usual renderers, with the [Meter] tapped into the audio path. */
    private fun metered() = object : DefaultRenderersFactory(this) {
        override fun buildAudioSink(context: Context, enableFloatOutput: Boolean,
                                    enableAudioTrackPlaybackParams: Boolean): AudioSink =
            DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf(TeeAudioProcessor(Meter())))
                .build()
    }

    private fun update() {
        val p = player ?: return
        when {
            p.isPlaying -> { set(State.PLAYING); startInForeground(if (Store.sounds(this)) "Playing" else "Muted — SOUND is off") }
            p.playbackState == Player.STATE_BUFFERING -> { set(State.TUNING); startInForeground("Tuning in…") }
            // Paused for a phone call or another app: keep the set "on" so it resumes.
        }
    }

    /** Follows the SOUND switch: full volume when on, silent when off. */
    private fun applySound() {
        player?.volume = if (Store.sounds(this)) 1f else 0f
    }

    private fun shutDown(keepError: Boolean = false) {
        player?.release(); player = null
        if (!keepError) set(State.OFF, -1)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (current === this) current = null
        player?.release(); player = null
        if (state != State.ERROR) set(State.OFF, -1)
        super.onDestroy()
    }

    private fun startInForeground(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Broadcast radio", NotificationManager.IMPORTANCE_LOW)
            .apply { description = "The little broadcast set while it's playing" })
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 2, Intent(this, StreamService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(name)
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "Stop", stop)
            .build()
        ServiceCompat.startForeground(this, NOTE_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }
}
