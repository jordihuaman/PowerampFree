package com.powerampfree.audio.playlist

import com.powerampfree.audio.playlist.PlaylistNames.sanitize
import com.powerampfree.audio.playlist.PlaylistNames.unique
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistNamesTest {

    @Test
    fun `plain names pass through`() {
        assertEquals("Mix 2026", sanitize("Mix 2026"))
        assertEquals("Rohn_Audio-1.mp3", sanitize("Rohn_Audio-1.mp3"))
    }

    @Test
    fun `unsafe characters become underscore and whitespace collapses`() {
        assertEquals("Mi_Lista_2026", sanitize("Mi\tLista/2026*"))
        assertEquals("A B", sanitize("  A   B  "))
    }

    @Test
    fun `blank input falls back to the default name`() {
        assertEquals(PlaylistNames.DEFAULT, sanitize(""))
        assertEquals(PlaylistNames.DEFAULT, sanitize("   "))
        assertEquals(PlaylistNames.DEFAULT, sanitize("**"))
    }

    @Test
    fun `free name returned as is`() {
        assertEquals("Mix", unique("Mix", setOf("Other")))
        assertEquals("Mix", unique("Mix", emptySet()))
    }

    @Test
    fun `collision gets the lowest free numeric suffix`() {
        assertEquals("Mix (2)", unique("Mix", setOf("Mix")))
        assertEquals("Mix (3)", unique("Mix", setOf("Mix", "Mix (2)")))
        assertTrue(unique("Mix", setOf("Mix", "Mix (2)", "Mix (5)")).endsWith("(3)"))
    }
}
