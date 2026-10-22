package com.powerampfree.audio.dsp

/**
 * Sequential crossfade envelope processor (clean-room design informed by the
 * observed `OVERLAP` performance state of the analyzed engine).
 *
 * Honest mechanism note: inside a single Media3 audio-sink session the decoded
 * streams are concatenated *in time* (track A's frames, then track B's frames);
 * two decoders cannot be mixed in real time here. The observed overlap behavior
 * is therefore reproduced as:
 *
 *   • fade-out over the last [fadeFrames] of track A,
 *   • fade-in over the first [fadeFrames] of track B,
 *
 * sample-accurate against planned per-item frame boundaries. A true two-decoder
 * real-time mix would require two output sinks and is documented as future work.
 *
 * The envelope is applied BEFORE gain/EQ/limiter so faded (attenuated) audio is
 * never re-amplified by preamp; the limiter still guards the seam.
 *
 * Gain law per frame `f` inside item [i]:
 *   local = f − start(i), dur = frames(i)
 *   g_in  = local / fadeFrames           (only when a real predecessor exists)
 *   g_out = (dur − local) / fadeFrames   (only when a real successor exists)
 *   gain  = min(g_in, g_out) ∈ [0,1]     → never amplifies, smooth boundary.
 */
class CrossfadeProcessor(
    override val sampleRateHz: Int,
    val channels: Int,
) : AudioProcessor {

    /** Cumulative *end* frame of each planned item: boundaries[i] = Σ frames(0..i). */
    private var boundaries: LongArray = LongArray(0)
    private var fadeFrames = 0

    /** per item i: [0]=fade-in allowed, [1]=fade-out allowed */
    private var fadeAllowed: Array<BooleanArray> = arrayOf()

    /** Current write position in the continuous frame timeline. */
    private var frame: Long = 0L
    private var index: Int = 0 // item index containing [frame]

    val isArmed: Boolean get() = fadeFrames > 0 && boundaries.isNotEmpty()

    /** Planned item frame ends, exposed for tests/diagnostics. */
    fun plannedBoundaries(): LongArray = boundaries.copyOf()

    fun plannedFadeFrames(): Int = fadeFrames

    /** Absolute frame the processor will consume next (for tests + snap debug). */
    fun currentFrame(): Long = frame

    fun itemFrames(itemIndex: Int): Long {
        if (itemIndex !in boundaries.indices) return 0L
        return boundaries[itemIndex] - (if (itemIndex == 0) 0L else boundaries[itemIndex - 1])
    }

    // ------------------------------------------------------------- plan/snap

    /**
     * Re-arms the plan. Items with duration ≤ 0 (unknown/streamed) get a
     * zero-length boundary and stay at unity gain (no fades applied to them);
     * their neighbours keep only the capped window against the other side.
     * [fadeMs] is capped at half the shortest known item so fade windows never
     * overlap and never amplify on short tracks.
     */
    fun setPlan(
        itemDurationsMs: List<Long>,
        fadeMs: Long,
        startIndex: Int,
        positionMs: Long,
        forceFadeIn: Boolean = false,
    ) {
        boundaries = LongArray(itemDurationsMs.size)
        var acc = 0L
        for ((i, dms) in itemDurationsMs.withIndex()) {
            if (dms > 0) acc += framesForMs(dms)
            boundaries[i] = acc
        }
        val known = itemDurationsMs.filter { it > 0 }
        val shortMs = known.minOrNull() ?: 0L
        val cap = if (shortMs > 0) framesForMs(shortMs) / 2 else framesForMs(1L)
        fadeFrames = when {
            fadeMs <= 0 -> 0
            else -> framesForMs(fadeMs).coerceAtLeast(1).coerceAtMost(cap.coerceAtLeast(1)).toInt()
        }

        val n = boundaries.size
        fadeAllowed = Array(n) { booleanArrayOf(false, false, false) }
        var prevKnown = -1
        for (i in 0 until n) {
            fadeAllowed[i][0] = (prevKnown in 0 until i) || // real predecessor strictly before i
                (forceFadeIn && i == startIndex)            // overlay decoder: fade-in from plan start
            if (itemFrames(i) > 0) prevKnown = i
        }
        var nextKnown = -1
        for (i in n - 1 downTo 0) {
            fadeAllowed[i][1] = nextKnown in (i + 1)..n // a real successor strictly after i
            if (itemFrames(i) > 0) nextKnown = i
        }
        snapTo(startIndex.coerceIn(0, (n - 1).coerceAtLeast(0)), positionMs)
    }

    /** Re-sync after seeks without rebuilding the plan (item index + position). */
    fun snapTo(itemIndex: Int, positionMs: Long) {
        if (boundaries.isEmpty()) return
        index = itemIndex.coerceIn(0, boundaries.size - 1)
        val start = startOf(index)
        val end = boundaries[index]
        frame = (start + framesForMs(positionMs)).coerceAtMost(end - 1).coerceAtLeast(start)
    }

    // ------------------------------------------------------------- envelope

    /** Envelope gain (0..1) for an absolute timeline frame (test-oriented). */
    fun envelopeAt(absoluteFrame: Long): Float {
        if (!isArmed) return 1f
        val i = indexOfFrame(absoluteFrame)
        if (i < 0 || itemFrames(i) <= 0L) return 1f
        val start = startOf(i)
        val end = boundaries[i]
        val f = absoluteFrame.coerceIn(start, end - 1)
        val local = f - start
        val dur = end - start
        val gIn = if (fadeAllowed[i][0]) (local / fadeFrames.toDouble()).coerceIn(0.0, 1.0) else 1.0
        val gOut = if (fadeAllowed[i][1]) ((dur - local) / fadeFrames.toDouble()).coerceIn(0.0, 1.0) else 1.0
        return (minOf(gIn, gOut).toFloat()).coerceIn(0f, 1f)
    }

    // ----------------------------------------------------------- processing

    override fun process(buffer: FloatArray, frames: Int, channels: Int) {
        if (!isArmed) return
        for (i in 0 until frames) {
            val g = envelopeAtStream(frame)
            if (g != 1f) {
                val base = i * channels
                for (ch in 0 until channels) buffer[base + ch] = buffer[base + ch] * g
            }
            frame++
        }
    }

    /** Envelope while streaming: uses/advances the incremental [index]. */
    private fun envelopeAtStream(f: Long): Float {
        while (index in boundaries.indices && f >= boundaries[index]) index++
        if (index !in boundaries.indices) {
            index = boundaries.size - 1
            if (f >= boundaries.last()) return 1f
        }
        return envelopeAt(f)
    }

    // ----------------------------------------------------------------- misc

    private fun startOf(i: Int): Long = if (i == 0) 0L else boundaries[i - 1]

    private fun indexOfFrame(f: Long): Int {
        for (i in boundaries.indices) if (f < boundaries[i]) return i
        return boundaries.size - 1
    }

    private fun framesForMs(ms: Long): Long = (ms * sampleRateHz + 500) / 1000

    override fun reset() {
        frame = 0L
        index = 0
    }
}
