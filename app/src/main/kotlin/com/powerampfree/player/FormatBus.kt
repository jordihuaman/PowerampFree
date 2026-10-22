package com.powerampfree.player

/**
 * Same-process channel for the real decoder format of the current track.
 *
 * The service's session player reads TrackGroup formats natively (the session
 * side of the binder does not surface them reliably — observed groups=0).
 * MainActivity lives in the same process, so it consumes the last published
 * value; fall back to Player.currentTracks on other processes.
 */
object FormatBus {
    @Volatile
    var current: String = ""

    /** "48 kHz · 128 kbps · OPUS" style chip text (empty when unknown). */
    fun formatString(sRate: Int, bps: Int, mime: String): String {
        val codec = when {
            mime.contains("opus", true) -> "OPUS"
            mime.contains("mpeg", true) -> "MP3"
            mime.contains("aac", true) -> "AAC"
            mime.contains("flac", true) -> "FLAC"
            mime.contains("vorbis", true) -> "VORBIS"
            mime.contains("wav", true) -> "WAV"
            mime.startsWith("audio/") -> mime.removePrefix("audio/").uppercase()
            else -> ""
        }
        return buildString {
            if (sRate > 0) append("${sRate / 1000} kHz")
            if (bps > 0) { if (isNotEmpty()) append(" · "); append("${bps / 1000} kbps") }
            if (codec.isNotEmpty()) { if (isNotEmpty()) append(" · "); append(codec) }
        }
    }
}
