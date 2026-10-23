package com.powerampfree.audio.art

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoverFileNamesTest {

    @Test
    fun firstCandidateIsCoverJpg() {
        assertEquals("cover.jpg", CoverFileNames.candidates.first())
    }

    @Test
    fun basePriorityBeatsLaterBase() {
        // cover.* is more canonical than albumart.* even if only both exist
        val got = CoverFileNames.pick(setOf("albumart.jpg", "cover.jpg"))
        assertEquals("cover.jpg", got)
    }

    @Test
    fun extensionPriorityWithinSameBase() {
        // only cover.png present → chosen (jpg siblings don't exist)
        val got = CoverFileNames.pick(setOf("cover.png", "folder.jpg"))
        assertEquals("cover.png", got)
    }

    @Test
    fun pickIsCompareAgainstLowercaseInput() {
        val got = CoverFileNames.pick(setOf("cover.jpg"))
        assertEquals("cover.jpg", got)
    }

    @Test
    fun noCandidateReturnsNull() {
        val got = CoverFileNames.pick(setOf("back.jpg", "artwork.png"))
        assertNull(got)
    }

    @Test
    fun isCoverMatchesCanonicalNamesOnly() {
        assertTrue(CoverFileNames.isCoverLower("folder.jpeg"))
        assertFalse(CoverFileNames.isCoverLower("song.mp3"))
        assertFalse(CoverFileNames.isCoverLower("coverart.jpg"))
    }
}
