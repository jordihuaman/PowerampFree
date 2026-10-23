package com.powerampfree.player

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Per-track state (block 11, sprint 9): remembered playhead positions
 * (podcasts / long sets) and played stats (count + last played), persisted to
 * `filesDir/track_state.json` after `QueueStore`'s style:
 * writes happen on the main thread upon transitions/ticks, the file is small
 * (288 tracks worst case ≈ tens of KB).
 *
 * DOM shape:
 * ```
 * {"tracks":{"<uri>":{"pos":1234567,"plays":3,"last":1697900000000}}}
 * ```
 *
 * Callers apply the policies (`TrackProgressPolicy`/`CountAsPlayedPolicy`)
 * BEFORE writing here: a track that never qualifies leaves no entry at all.
 */
object TrackStateStore {

    private const val FILE = "track_state.json"

    /** One entry per URI durably remembered. */
    data class TrackState(
        val positionMs: Long = 0L,
        val playedCount: Int = 0,
        val lastPlayedMs: Long = 0L,
    )

    @Volatile
    private var cache: HashMap<String, TrackState>? = null

    private fun file(context: Context) = File(context.filesDir, FILE)

    private fun load(context: Context): HashMap<String, TrackState> {
        cache?.let { return it }
        val map = HashMap<String, TrackState>()
        runCatching {
            val text = file(context).takeIf { it.exists() }?.readText()
            if (text != null) {
                val root = JSONObject(text)
                val tracks = root.optJSONObject("tracks")
                if (tracks != null) {
                    for (uri in tracks.keys()) {
                        val o = tracks.optJSONObject(uri) ?: continue
                        map[uri] = TrackState(
                            positionMs = o.optLong("pos"),
                            playedCount = o.optInt("plays"),
                            lastPlayedMs = o.optLong("last"),
                        )
                    }
                }
            }
        }
        cache = map
        return map
    }

    /** File rewrite wrapped in runCatching (save is best-effort). */
    private fun write(context: Context, map: Map<String, TrackState>) {
        runCatching {
            val tracks = JSONObject()
            for ((uri, s) in map) {
                tracks.put(
                    uri,
                    JSONObject()
                        .put("pos", s.positionMs)
                        .put("plays", s.playedCount)
                        .put("last", s.lastPlayedMs),
                )
            }
            val tmp = File(context.filesDir, "$FILE.tmp")
            tmp.writeText(JSONObject().put("tracks", tracks).toString())
            if (!tmp.renameTo(file(context))) {
                file(context).writeText(JSONObject().put("tracks", tracks).toString())
                tmp.delete()
            }
        }
    }

    /** Remembered playhead for [uri], 0 when none. */
    fun positionFor(context: Context, uri: String): Long = load(context)[uri]?.positionMs ?: 0L

    fun saveProgress(context: Context, uri: String, positionMs: Long) {
        val map = load(context)
        val old = map[uri] ?: TrackState()
        map[uri] = old.copy(positionMs = positionMs.coerceAtLeast(0L))
        write(context, map)
    }

    /** Stats commit: plays++ + last=now; keeps the playhead fresh too. */
    fun commitCount(context: Context, uri: String, positionMs: Long) {
        val map = load(context)
        val old = map[uri] ?: TrackState()
        map[uri] = old.copy(
            positionMs = positionMs.coerceAtLeast(0L),
            playedCount = old.playedCount + 1,
            lastPlayedMs = System.currentTimeMillis(),
        )
        write(context, map)
    }

    /** (playedDistinct, totalPlays) for the settings summary line. */
    fun statsSummary(context: Context): Pair<Int, Int> {
        val map = load(context)
        var totalPlays = 0
        for (s in map.values) totalPlays += s.playedCount
        return map.count { it.value.playedCount > 0 } to totalPlays
    }

    /** Reset stats row (played times + last played; positions are kept). */
    fun resetStats(context: Context) {
        val map = load(context)
        for ((uri, s) in map) map[uri] = s.copy(playedCount = 0, lastPlayedMs = 0L)
        write(context, map)
    }

    /** Only for Export/Import. */
    fun snapshotJson(context: Context): Map<String, TrackState> = load(context).toMap()

    /** Only for Export/Import (replaces entries found in the export). */
    fun importAll(context: Context, entries: Map<String, TrackState>) {
        val map = load(context)
        map.putAll(entries)
        write(context, map)
    }
}
