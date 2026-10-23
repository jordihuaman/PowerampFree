package com.powerampfree.player

import android.content.Context
import androidx.media3.common.audio.BaseAudioProcessor
import java.io.File
import com.powerampfree.audio.dsp.CrossfadeProcessor
import com.powerampfree.audio.dsp.EqPreset

/**
 * Playback DSP bridge for Media3.
 *
 * Integration point between Media3's render pipeline and the pure Kotlin
 * `:core:audio` DSP: Media3 hands us LinearPCM float buffers through its
 * [BaseAudioProcessor] contract; we forward them through the ordered chain
 * GainStage (preamp/volume) → ParametricEq → SoftLimiter.
 *
 *  - On format changes Media3 calls onConfigure(): the chain is rebuilt
 *    (no stale filter state across rate switches — analogous to the observed
 *    reconfigure-on-track-session-open behavior).
 *  - EQ preset changes while playing take the *live* path [applyPreset], which
 *    maps to the observed two update modes of the reference engine:
 *      a) full reconfigure  → ParametricEq.setBands()  (band list changed)
 *      b) incremental gain  → ParametricEq.modifyBandGain() (only gains changed)
 */
class DspAudioProcessor(private val appContext: Context) : BaseAudioProcessor() {

    private var chain: com.powerampfree.audio.dsp.DspChain? = null
    private var fmt: androidx.media3.common.audio.AudioProcessor.AudioFormat? = null

    /** Plan queued before onConfigure has run (crossfade durations are known earlier). */
    private data class PendingPlan(
        val durationsMs: List<Long>,
        val fadeMs: Long,
        val itemIndex: Int,
        val positionMs: Long,
        val forceFadeIn: Boolean,
    )
    private var pendingPlan: PendingPlan? = null

    // audio tap for the visualizer (mono accumulation of post-DSP samples)
    private val tapWindow = FloatArray(1024)
    private var tapPos = 0

    override fun onConfigure(
        inputAudioFormat: androidx.media3.common.audio.AudioProcessor.AudioFormat,
    ): androidx.media3.common.audio.AudioProcessor.AudioFormat {
        // Decoders deliver PCM 16-bit by default; Media3 only feeds float when
        // the pipeline negotiates it. Accept both, keep the same encoding out
        // so the audio sink sees a format it already accepted.
        val ok = inputAudioFormat.encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT ||
            inputAudioFormat.encoding == androidx.media3.common.C.ENCODING_PCM_16BIT
        if (!ok) {
            throw androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        fmt = inputAudioFormat
        val xf = CrossfadeProcessor(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
        pendingPlan?.let {
            xf.setPlan(it.durationsMs, it.fadeMs, it.itemIndex, it.positionMs, it.forceFadeIn)
            pendingPlan = null
        }
        chain = com.powerampfree.audio.dsp.DspChain.buildDefault(
            sampleRateHz = inputAudioFormat.sampleRate,
            channels = inputAudioFormat.channelCount,
            preset = PresetStore.loadCurrent(appContext),
            crossfade = xf,
            replayGainDb = AudioSettings.rgAppliedDb(appContext),
        )
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
        val f = fmt ?: run { inputBuffer.position(inputBuffer.limit()); return }
        val bytesPerSample = if (f.encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT) 4 else 2
        val totalSamples = inputBuffer.remaining() / bytesPerSample
        val frameCount = totalSamples / f.channelCount
        val j = frameCount * f.channelCount
        if (frameCount == 0) { replaceOutputBuffer(0).flip(); return }

        // decode to float processing buffer (our DSP core is float)
        val work = FloatArray(j)
        if (f.encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT) {
            inputBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(work, 0, j)
        } else {
            val shorts = ShortArray(j)
            inputBuffer.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts, 0, j)
            for (i in 0 until j) work[i] = shorts[i] / 32768.0f
        }
        inputBuffer.position(inputBuffer.limit())

        chain!!.process(work, frameCount, f.channelCount)

        // re-encode in the SAME encoding the sink expects
        val out = replaceOutputBuffer(j * bytesPerSample)
        if (f.encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT) {
            out.order(java.nio.ByteOrder.LITTLE_ENDIAN).asFloatBuffer().put(work, 0, j)
        } else {
            val sb = out.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            for (i in 0 until j) {
                val s = (work[i] * 32767.0f).toInt().coerceIn(-32768, 32767).toShort()
                sb.put(s)
            }
        }

        // visualizer tap: accumulate post-EQ mono frames (bounded, lock-free UI)
        val ch = f.channelCount
        for (i in 0 until frameCount) {
            val base = i * ch
            var mono = 0.0
            for (c in 0 until ch) mono += work[base + c]
            tapWindow[tapPos++] = (mono / ch).toFloat()
            if (tapPos == tapWindow.size) {
                tapPos = 0
                VisualizerTap.publish(tapWindow)
            }
        }
    }

    override fun onFlush() {
        chain?.reset()
        tapPos = 0
    }

    override fun onReset() {
        chain = null
        fmt = null
        pendingPlan = null
    }

    /**
     * Crossfade planning: item durations + fade length; snaps to (itemIndex, positionMs)
     * so the envelope stays sample-accurate across transitions and seeks.
     * [forceFadeIn] arms the fade-in at the plan start even without a real
     * predecessor item — used on the overlay (second) decoder of the real
     * overlap mix, whose audio must start silent and ramp up while the outgoing
     * decoder is still playing.
     * Can be called before the sink is configured (stored until then).
     */
    fun setCrossfadePlan(
        durationsMs: List<Long>,
        fadeMs: Long,
        itemIndex: Int,
        positionMs: Long,
        forceFadeIn: Boolean = false,
    ) {
        val xf = chain?.processors
            ?.filterIsInstance<CrossfadeProcessor>()?.firstOrNull()
        if (xf != null) {
            xf.setPlan(durationsMs, fadeMs, itemIndex, positionMs, forceFadeIn)
        } else {
            pendingPlan = PendingPlan(durationsMs, fadeMs, itemIndex, positionMs, forceFadeIn)
        }
    }

    /** Seek re-sync: snaps the timeline without rebuilding the plan. */
    fun snapCrossfade(itemIndex: Int, positionMs: Long) {
        chain?.processors?.filterIsInstance<CrossfadeProcessor>()?.firstOrNull()
            ?.snapTo(itemIndex, positionMs)
    }

    /**
     * Live preset application (band structure reconfigure or incremental gain
     * modification; consistent with the observed push-blob atomic DSP protocol:
     * the new state takes effect as a whole, not band-by-band audibly).
     */
    fun applyPreset(preset: com.powerampfree.audio.dsp.EqPreset) {
        val ch = chain ?: return
        val eq = ch.processors.filterIsInstance<com.powerampfree.audio.dsp.ParametricEq>().firstOrNull()
            ?: return
        // Full resolution: parametric + graphic layer + tone shelves.
        val wanted = preset.resolvedBands()
        val current = (0 until eq.bandCount).mapNotNull { eq.bandAt(it) }
        val sameShape = current.size == wanted.size &&
            current.indices.all { i ->
                val a = current[i]; val b = wanted[i]
                a.type == b.type && a.freqHz == b.freqHz && a.q == b.q && a.enabled == b.enabled
            }
        if (sameShape) {
            // incremental path (analyzed native_modify_coefs_for_gain family)
            current.indices.forEach { i -> eq.modifyBandGain(i, wanted[i].gainDb) }
        } else {
            // full reconfigure path
            eq.setBands(wanted)
        }
        ch.processors.filterIsInstance<com.powerampfree.audio.dsp.GainStage>().firstOrNull()
            ?.contentGainDb = preset.preampDb
        ch.processors.filterIsInstance<com.powerampfree.audio.dsp.SoftLimiter>().firstOrNull()
            ?.enabled = preset.limitOn
    }

    /**
     * Live Replay Gain trim: the EQ preamp is owned by [applyPreset],
     * this is the separate RG term so both stack independently in GainStage.
     */
    fun setReplayGain(db: Float) {
        chain?.processors
            ?.filterIsInstance<com.powerampfree.audio.dsp.GainStage>()
            ?.firstOrNull()
            ?.replayGainDb = db
    }

    /**
     * Duck for a transient focus loss (block 8): lowers the GainStage volume to
     * a fixed fraction (and restores to unity). Absolute, not multiplicative,
     * so repeated duck/restore cycles cannot drift — safe because the user
     * volume is not wired to GainStage yet (always unity baseline).
     */
    fun setDuck(on: Boolean) {
        chain?.processors
            ?.filterIsInstance<com.powerampfree.audio.dsp.GainStage>()
            ?.firstOrNull()
            ?.volumeLinear = if (on) 0.3f else 1f
    }
}

/**
 * Preset persistence:
 *  - current live preset: serialized JSON in SharedPreferences;
 *  - named saved presets: one JSON file per preset in `filesDir/eq/`.
 * All in our own original JSON format (no "PaP" binary compat by design).
 */
object PresetStore {

    private const val PREFS = "dsp_presets"
    private const val KEY_CURRENT_JSON = "current_preset_json"
    private const val DIR = "eq"

    fun loadCurrent(context: Context): com.powerampfree.audio.dsp.EqPreset {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val txt = prefs.getString(KEY_CURRENT_JSON, null)
        if (txt != null) {
            runCatching { return com.powerampfree.audio.dsp.EqPreset.fromJson(txt) }
        }
        return defaultPreset()
    }

    fun storeCurrent(context: Context, preset: com.powerampfree.audio.dsp.EqPreset) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CURRENT_JSON, preset.toJson())
            .apply()
    }

    fun saveNamed(context: Context, preset: com.powerampfree.audio.dsp.EqPreset) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val safe = preset.name.ifBlank { "preset" }
            .map { if (it.isLetterOrDigit() || it in " _-") it else '_' }
            .joinToString("")
        File(dir, "$safe.json").writeText(preset.toJson())
    }

    fun listNamed(context: Context): List<com.powerampfree.audio.dsp.EqPreset> {
        val dir = File(context.filesDir, DIR)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.sortedBy { it.name }
            ?.mapNotNull { f ->
                runCatching { com.powerampfree.audio.dsp.EqPreset.fromJson(f.readText()) }.getOrNull()
            }
            ?: emptyList()
    }

    fun defaultPreset(): com.powerampfree.audio.dsp.EqPreset =
        com.powerampfree.audio.dsp.EqPreset(
            name = "Default",
            preampDb = 0.0f,
            bands = listOf(
                com.powerampfree.audio.dsp.Band(
                    com.powerampfree.audio.dsp.FilterType.LOW_SHELF, 90, 0.0f, 0.7f,
                ),
                com.powerampfree.audio.dsp.Band(
                    com.powerampfree.audio.dsp.FilterType.PEAKING, 1000, 0.0f, 0.9f,
                ),
                com.powerampfree.audio.dsp.Band(
                    com.powerampfree.audio.dsp.FilterType.HIGH_SHELF, 9000, 0.0f, 0.7f,
                ),
            ),
        )
}

/**
 * Playback settings (non-DSP, service-side): crossfade length in ms etc.
 * Persisted so the plan survives process death like the queue does.
 */
object PlaybackSettings {
    private const val PREFS = "playback_settings"
    private const val KEY_CROSSFADE_MS = "crossfade_ms"
    private const val KEY_REPEAT_MODE = "repeat_mode"
    private const val KEY_SHUFFLE_ON = "shuffle_on"

    fun crossfadeMs(context: Context): Long {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Tolerant reader: an imported backup may have stored Int (org.json
        // parses any number fitting Int as Integer).
        return when (val v = sp.all[KEY_CROSSFADE_MS]) {
            is Long -> v
            is Int -> v.toLong()
            else -> 0L
        }
    }

    fun setCrossfadeMs(context: Context, ms: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_CROSSFADE_MS, ms.coerceIn(0L, 12_000L)).apply()
    }

    // --- repeat / shuffle (player screen; survives process death) -----------

    const val REPEAT_OFF = "off"
    const val REPEAT_ONE = "one"
    const val REPEAT_ALL = "all"

    fun repeatMode(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_REPEAT_MODE, REPEAT_OFF) ?: REPEAT_OFF

    fun setRepeatMode(context: Context, mode: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_REPEAT_MODE, mode).apply()
    }

    fun shuffleEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHUFFLE_ON, false)

    fun setShuffleEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHUFFLE_ON, on).apply()
    }
}
