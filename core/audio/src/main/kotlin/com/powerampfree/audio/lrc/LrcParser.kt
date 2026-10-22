package com.powerampfree.audio.lrc

/**
 * Minimal LRC lyrics parser (original implementation).
 *
 * Supports `[mm:ss]` and `[mm:ss.cc]`/`[mm:ss.ccc]` timestamp tags, several
 * tags pointing at the same text line (common in synced files), and sorts the
 * result. Metadata tags like `[ti:...]` carry no text and are ignored.
 */
data class LrcLine(val atMs: Long, val text: String)

object LrcParser {

    private val TAG = Regex("""\[(\d+):(\d+)(?:[.:](\d{1,3}))?\]""")

    /** Parses LRC text into timed lines sorted ascending by timestamp. */
    fun parse(raw: String): List<LrcLine> {
        val out = ArrayList<LrcLine>()
        raw.lineSequence().forEach { line ->
            val tags = TAG.findAll(line).toList()
            if (tags.isEmpty()) return@forEach
            val text = line.substring(tags.last().range.last + 1).trim()
            if (text.isEmpty()) return@forEach
            for (t in tags) {
                val min = t.groupValues[1].toLong()
                val sec = t.groupValues[2].toLong()
                val fracRaw = t.groupValues[3]
                val frac = when (fracRaw.length) {
                    0 -> 0L
                    1 -> fracRaw.toLong() * 100
                    2 -> fracRaw.toLong() * 10
                    else -> fracRaw.take(3).toLong()
                }
                out += LrcLine(min * 60_000 + sec * 1000 + frac, text)
            }
        }
        return out.sortedBy { it.atMs }
    }

    /**
     * Index of the line highlighted at [posMs]; -1 before the first line.
     * Binary search over the sorted list.
     */
    fun activeIndex(lines: List<LrcLine>, posMs: Long): Int {
        var lo = 0
        var hi = lines.size - 1
        var ans = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].atMs <= posMs) {
                ans = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return ans
    }
}
