package com.powerampfree.player

import com.powerampfree.audio.dsp.SpectrumAnalyzer
import java.util.concurrent.atomic.AtomicReference

/**
 * Audio tap for the visualizer UI.
 *
 * A narrow, bounded copy of the post-EQ output: the DSP processor accumulates a
 * mono analysis window and publishes the magnitude spectrum here for the UI to
 * poll (≈ every 50 ms). Reading is lock-free via [AtomicReference]; nothing in
 * this class ever influences the audible signal path.
 */
object VisualizerTap {

    /** Analysis window: 1024 samples ≈ 21 ms of 48 kHz audio. */
    private const val WINDOW = 1024

    private val analyzer = SpectrumAnalyzer(WINDOW)
    private val latest = AtomicReference<FloatArray>(FloatArray(analyzer.bins))

    /** Bin count of the published spectrum. */
    val bins: Int get() = analyzer.bins

    /** Publishes the spectrum of a complete mono analysis window (DSP thread). */
    fun publish(window: FloatArray) {
        val s = analyzer.spectrum(if (window.size == WINDOW) window else window.copyOf(WINDOW))
        latest.set(s)
    }

    /** Latest spectrum snapshot (bins magnitudes; allocate-cheap poll). */
    fun spectrum(): FloatArray = latest.get()
}
