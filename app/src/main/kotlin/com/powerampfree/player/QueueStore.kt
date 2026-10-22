package com.powerampfree.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Queue persistence (survives process death) — original design:
 *  - queue snapshot: JSON file in filesDir (`queue.json`);
 *  - entry list after each transition + periodic position save while playing;
 *  - restore happens once at service creation, playback stays paused until the
 *    user resumes (no surprise autoplay on boot, mirroring the analyzed engine's
 *    explicit "open/create session" behavior).
 */
data class SavedQueue(val items: List<MediaTrack>, val startIndex: Int, val positionMs: Long)

object QueueStore {

    private const val FILE = "queue.json"

    fun save(context: Context, items: List<MediaTrack>, startIndex: Int, positionMs: Long) {
        runCatching {
            val arr = JSONArray()
            for (t in items) arr.put(
                JSONObject()
                    .put("uri", t.uri)
                    .put("title", t.title)
                    .put("artist", t.artist)
                    .put("album", t.album)
                    .put("durationMs", t.durationMs),
            )
            val doc = JSONObject()
                .put("index", startIndex)
                .put("positionMs", positionMs)
                .put("items", arr)
            val tmp = File(context.filesDir, "$FILE.tmp")
            tmp.writeText(doc.toString())
            tmp.renameTo(File(context.filesDir, FILE))
        }
    }

    fun load(context: Context): SavedQueue? {
        val f = File(context.filesDir, FILE)
        if (!f.exists()) return null
        return runCatching {
            val doc = JSONObject(f.readText())
            val arr = doc.getJSONArray("items")
            val items = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        MediaTrack(
                            uri = o.getString("uri"),
                            title = o.optString("title", "?"),
                            artist = o.optString("artist"),
                            album = o.optString("album"),
                            durationMs = o.optLong("durationMs", 0L),
                        ),
                    )
                }
            }
            if (items.isEmpty()) null else
                SavedQueue(
                    items = items,
                    startIndex = doc.optInt("index", 0),
                    positionMs = doc.optLong("positionMs", 0L),
                )
        }.getOrNull()
    }
}
