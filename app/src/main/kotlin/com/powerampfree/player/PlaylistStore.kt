package com.powerampfree.player

import android.content.Context
import com.powerampfree.audio.playlist.PlaylistNames
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Saved playlists (sprint 10 — block 4 "Add to Playlist" + block 6 lists).
 *
 * One JSON file per playlist under `filesDir/playlists/<file>.json` — the
 * same layout as EQ presets — so create/rename/delete is file-level and each
 * list only rewrites on edit. Entries snapshot the `MediaTrack` fields the
 * queue needs (incl. `albId`/`dir`/`trk` for art + ordering, the lesson from
 * sprint 8) plus `at` (added-at) for stable ordering of additions.
 *
 * File schema:
 * ```json
 * {"name":"Mix","tracks":[
 *   {"uri":"content://…","title":"…","artist":"…","album":"…",
 *    "durationMs":1234,"albId":123,"trk":3,"dir":"Music/…","at":1700000}]}
 * ```
 *
 * Name handling (`PlaylistNames` from `:core:audio`) keeps file names safe and
 * user-visible names unique before any write happens here.
 */
object PlaylistStore {

    private const val DIR = "playlists"

    private fun dir(context: Context) =
        File(context.filesDir, DIR).apply { mkdirs() }

    private fun fileFor(context: Context, storedName: String) =
        File(dir(context), PlaylistNames.sanitize(storedName) + ".json")

    private fun load(context: Context, name: String): Pair<String, List<MediaTrack>> {
        val f = fileFor(context, name)
        if (!f.exists()) return name to emptyList()
        return runCatching {
            val o = JSONObject(f.readText())
            val out = ArrayList<MediaTrack>()
            val arr = o.optJSONArray("tracks") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val t = arr.getJSONObject(i)
                out += MediaTrack(
                    uri = t.optString("uri"),
                    title = t.optString("title", "?").ifBlank { "?" },
                    artist = t.optString("artist"),
                    album = t.optString("album"),
                    durationMs = t.optLong("durationMs"),
                    albumId = t.optLong("albId"),
                    trackNo = t.optInt("trk"),
                    folder = t.optString("dir"),
                )
            }
            o.optString("name", name) to out
        }.getOrDefault(name to emptyList())
    }

    private fun write(context: Context, name: String, tracks: List<MediaTrack>) {
        val arr = JSONArray()
        for (t in tracks) {
            arr.put(
                JSONObject()
                    .put("uri", t.uri)
                    .put("title", t.title)
                    .put("artist", t.artist)
                    .put("album", t.album)
                    .put("durationMs", t.durationMs)
                    .put("albId", t.albumId)
                    .put("trk", t.trackNo)
                    .put("dir", t.folder)
                    .put("at", System.currentTimeMillis()),
            )
        }
        val tmp = File(dir(context), fileFor(context, name).name + ".tmp")
        tmp.writeText(JSONObject().put("name", name).put("tracks", arr).toString())
        if (!tmp.renameTo(fileFor(context, name))) {
            fileFor(context, name).writeText(
                JSONObject().put("name", name).put("tracks", arr).toString(),
            )
            tmp.delete()
        }
    }

    /** Saved playlist names, alphabetical, WITHOUT the trailing `.json`. */
    fun list(context: Context): List<String> =
        dir(context).listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.map { it.name.removeSuffix(".json") }
            ?.sortedWith(String.CASE_INSENSITIVE_ORDER)
            ?: emptyList()

    fun tracks(context: Context, name: String): List<MediaTrack> = load(context, name).second

    /** Creates an empty playlist; returns the stored (unique) name. */
    fun create(context: Context, rawName: String): String {
        val name = PlaylistNames.unique(PlaylistNames.sanitize(rawName), list(context).toSet())
        write(context, name, emptyList())
        return name
    }

    /** Removes the playlist file. Returns true when something was deleted. */
    fun delete(context: Context, name: String): Boolean = fileFor(context, name).delete()

    /** Renames the playlist file; collisions get a unique suffix. */
    fun rename(context: Context, oldName: String, newRawName: String): String {
        val tracks = load(context, oldName).second
        val newName = PlaylistNames.unique(PlaylistNames.sanitize(newRawName), list(context).toSet())
        if (!delete(context, oldName) && tracks.isEmpty()) return oldName
        write(context, newName, tracks)
        return newName
    }

    /**
     * Appends [track] unless its URI is already present in the list.
     * Returns true when the track was really added.
     */
    fun addTrack(context: Context, name: String, track: MediaTrack): Boolean {
        val (_, tracks) = load(context, name)
        if (tracks.any { it.uri == track.uri }) return false
        write(context, name, tracks + track)
        return true
    }

    /** Removes the track with [uri]; returns true when the list changed. */
    fun removeTrack(context: Context, name: String, uri: String): Boolean {
        val (_, tracks) = load(context, name)
        val pruned = tracks.filterNot { it.uri == uri }
        if (pruned.size == tracks.size) return false
        write(context, name, pruned)
        return true
    }
}
