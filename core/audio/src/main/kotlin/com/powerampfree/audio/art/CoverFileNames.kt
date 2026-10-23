package com.powerampfree.audio.art

/**
 * Pure album-art filename logic (block 12, sprint 8).
 *
 * When a track's own folder contains a cover image, the scan order matters:
 * the entries of [candidates] are ordered from MOST to LEAST canonical
 * (cover.jpg precedes album artwork, which precedes folder.jpg...). Within
 * the same base, the native extension preference is jpg > jpeg > png.
 *
 * All comparisons are lowercase canonical names; the caller (AlbumArtLoader)
 * normalizes the names of the folder listing to lowercase before calling
 * [pick], and re-finds the real file with the actual case afterwards.
 */
object CoverFileNames {
    val bases = listOf("cover", "albumart", "album_art", "folder", "front")
    val exts = listOf("jpg", "jpeg", "png")

    /** Candidate cover names, absolute priority order (most canonical first). */
    val candidates: List<String> =
        bases.flatMap { b -> exts.map { "$b.$it" } }

    /**
     * Chooses the cover name present in the folder from the set of folder
     * names already in lowercase. Returns null when no candidate is present.
     */
    fun pick(existingLower: Set<String>): String? =
        candidates.firstOrNull { it in existingLower }

    /** Verifies if a name (already lowercase, with complete extension) is a cover. */
    fun isCoverLower(nameLower: String): Boolean = nameLower in candidates
}
