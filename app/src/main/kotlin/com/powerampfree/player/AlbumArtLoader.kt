package com.powerampfree.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import com.powerampfree.audio.art.CoverFileNames
import android.util.LruCache
import java.io.InputStream

/**
 * Album art resolver (blocks 12/13, sprint 8).
 *
 * Search order:
 *  1. MediaStore album art by `albumId` via the system albumart provider
 *     (content://media/external/audio/albumart/<id>) — covers every track
 *     scanned out of the system library.
 *  2. Sibling cover files next to a file-path track (legacy `file:`/absolute
 *     uris) using the [CoverFileNames] priority.
 *  3. SAF tree tracks (document-provider uris, albumId = -1) have no
 *     resolvable local path → no art; the UI falls back to a generic glyph
 *     (honest limitation, no networked download in this project).
 *
 * Images decode downsampled to the requested pixel size and live in a
 * memory-only LruCache; nothing is written to disk.
 */
object AlbumArtLoader {

    /** System provider uri for one album's artwork (framework MediaProvider). */
    fun artUri(albumId: Long): Uri =
        Uri.Builder()
            .scheme("content")
            .authority("media")
            .appendPath("external")
            .appendPath("audio")
            .appendPath("albumart")
            .appendPath(albumId.toString())
            .build()

    private val cache = object : LruCache<String, Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    /** Byte-based cache (32 MiB): 1024px tiles cost ~4 MiB, 48-entry cap was too small. */
    private const val CACHE_BYTES = 32 * 1024 * 1024

    /** Resolved bitmap or null; `px` is the target size for downsampling. */
    fun load(context: Context, albumId: Long, uri: String, folder: String, px: Int): Bitmap? {
        if (px <= 0) return null
        if (albumId < 0 && !uri.startsWith("file:") && !uri.startsWith("/")) return null
        val key = "$albumId|$uri|$px"
        cache.get(key)?.let { return it }
        val bmp = resolve(context, albumId, uri, folder, px) ?: return null
        cache.put(key, bmp)
        return bmp
    }

    fun clear() = cache.evictAll()

    @Suppress("UNUSED_PARAMETER")
    private fun resolve(
        context: Context,
        albumId: Long,
        uri: String,
        folder: String,
        px: Int,
    ): Bitmap? {
        if (albumId >= 0) {
            // System album art provider first.
            decode({ context.contentResolver.openInputStream(artUri(albumId)) }, px)?.let {
                return it
            }
        }
        // Fallback: sibling cover files (block 12 "Also Search By" spirit, no
        // network). Derivable local dirs only:
        //  1. file:/absolute uris → parent of the audio file itself;
        //  2. MediaStore tracks → DATA column (absolut path) parent;
        //  3. RELATIVE_PATH join with the primary storage root. SAF tree
        //     tracks only carry the folder NAME → not derivable → no art.
        val parent = when {
            uri.startsWith("file:") ->
                java.net.URI.create(uri).path?.let { java.io.File(it).parent }
            uri.startsWith("/") -> java.io.File(uri).parent
            albumId >= 0 -> queryDataParent(context, albumId) ?: (root() + folder)
            folder.isNotEmpty() -> root() + folder
            else -> null
        } ?: return null
        val dir = java.io.File(parent)
        if (!dir.isDirectory) return null
        val files = dir.listFiles() ?: return null
        val chosen = CoverFileNames.pick(files.map { it.name.lowercase() }.toSet()) ?: return null
        return files.firstOrNull { it.name.lowercase() == chosen }
            ?.let { f -> decode({ f.inputStream() }, px) }
    }

    private fun root(): String =
        android.os.Environment.getExternalStorageDirectory().path.let {
            if (it.endsWith("/")) it else "$it/"
        }

    /** DATA (absolut path) parent dir of one track of an album, null on failure. */
    private fun queryDataParent(context: Context, albumId: Long): String? = runCatching {
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media.RELATIVE_PATH, MediaStore.Audio.Media.DATA),
            "${MediaStore.Audio.Media.ALBUM_ID} = ?",
            arrayOf(albumId.toString()),
            null,
        )?.use { c ->
            if (c.moveToFirst()) {
                val rel = c.getString(0)
                val data = c.getString(1)
                when {
                    !data.isNullOrBlank() -> java.io.File(data).parent
                    !rel.isNullOrBlank() -> root() + rel
                    else -> null
                }
            } else null
        }
    }.getOrNull()


    /**
     * Two-pass decode (bounds, then sample): the supplier may be invoked twice
     * because content providers and files both reopen; failures → null.
     */
    private fun decode(open: () -> InputStream?, px: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= px && bounds.outHeight / (sample * 2) >= px) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        open()?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()
}
