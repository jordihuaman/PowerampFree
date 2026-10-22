package com.powerampfree.player

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Track source for the queue.
 *
 * Original implementation (no reference-app resources or intents used):
 *  - system audio library (MediaStore) as primary source,
 *  - SAF folder import for user-picked directories (SD card, etc.).
 */
data class MediaTrack(
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("uri", uri)
        put("title", title)
        put("artist", artist)
        put("album", album)
        put("dur", durationMs)
    }

    companion object {
        /** Compact own-format serialization used by the SET_QUEUE session command. */
        fun listToJson(tracks: List<MediaTrack>): String {
            val arr = JSONArray()
            tracks.forEach { arr.put(it.toJson()) }
            return arr.toString()
        }

        fun listFromJson(json: String): List<MediaTrack> {
            val arr = JSONArray(json)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                MediaTrack(
                    uri = o.optString("uri"),
                    title = o.optString("title", "?"),
                    artist = o.optString("artist"),
                    album = o.optString("album"),
                    durationMs = o.optLong("dur"),
                )
            }
        }
    }

    fun toMediaItem(): androidx.media3.common.MediaItem =
        androidx.media3.common.MediaItem.Builder()
            .setUri(uri)
            .setMediaId(uri)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .apply {
                        if (durationMs > 0) {
                            setExtras(android.os.Bundle().apply {
                                putLong("durationMs", durationMs)
                            })
                        }
                    }
                    .build(),
            )
            .build()
}

object MediaStoreLibrary {

    /**
     * All system audio, in a stable musical order (same album → track number → title,
     * like any standard library browser). Requires READ_MEDIA_AUDIO runtime grant.
     */
    fun query(context: Context): List<MediaTrack> {
        val out = ArrayList<MediaTrack>(128)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TRACK,
        )
        val sortOrder = "${MediaStore.Audio.Media.ALBUM_ID} ASC, " +
            "${MediaStore.Audio.Media.TRACK} ASC, ${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder,
            )?.use { c ->
                val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val dur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (c.moveToNext()) {
                    val uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getLong(id),
                    )
                    out += MediaTrack(
                        uri = uri.toString(),
                        title = c.getString(title) ?: "?",
                        artist = c.getString(artist) ?: "",
                        album = c.getString(album) ?: "",
                        durationMs = c.getLong(dur),
                    )
                }
            }
        } catch (_: SecurityException) {
            // permission denied → empty library; UI shows the grant button
        } catch (_: IllegalArgumentException) {
            // malformed row → skip
        }
        return out
    }
}

/** SAF import for tree URIs picked with ACTION_OPEN_DOCUMENT_TREE descendants. */
object SafLibrary {

    /** Recursively collects audio documents under [treeUri]. */
    fun collectAudio(context: Context, treeUri: Uri): List<MediaTrack> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val out = ArrayList<MediaTrack>()
        fun walk(dir: DocumentFile) {
            for (f in dir.listFiles()) {
                if (f.isDirectory) {
                    walk(f)
                } else {
                    val name = f.name ?: continue
                    val mime = f.type ?: ""
                    if (name.suggestsAudio() || mime.startsWith("audio/")) {
                        out += MediaTrack(
                            uri = f.uri.toString(),
                            title = name.substringBeforeLast('.'),
                            artist = dir.name ?: "",
                            album = "SAF",
                            durationMs = 0L,
                        )
                    }
                }
            }
        }
        walk(root)
        return out
    }

    private fun String.suggestsAudio(): Boolean {
        val e = lowercase().substringAfterLast('.')
        return e in setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "oga", "wma", "wv", "ape", "mka")
    }
}
