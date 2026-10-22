package com.powerampfree.audio.lrc

import org.junit.Assert.assertEquals
import org.junit.Test

/** LRC parse + active-line coverage (sprint 6). */
class LrcParserTest {

    @Test
    fun `parses two-digit hundredths timestamps`() {
        val lines = LrcParser.parse(
            "[ti:demo]\n[00:01.20]first\n[01:02.03]second\njunk line\n[00:00.50]zero",
        )
        assertEquals(
            listOf(
                LrcLine(500, "zero"),
                LrcLine(1200, "first"),
                LrcLine(62_030, "second"),
            ),
            lines,
        )
    }

    @Test
    fun `parses multiple tags per line and millisecond fractions`() {
        val lines = LrcParser.parse("[00:05.000][00:15.250]chorus")
        assertEquals(
            listOf(LrcLine(5000, "chorus"), LrcLine(15_250, "chorus")),
            lines,
        )
    }

    @Test
    fun `ignores empty text and metadata tags`() {
        val lines = LrcParser.parse("[ar:x]\n[00:10.00]\n[00:12.50]hello\n")
        assertEquals(1, lines.size)
        assertEquals("hello", lines[0].text)
    }

    @Test
    fun `activeLine boundaries`() {
        val lines = LrcParser.parse("[00:10.00]a\n[00:20.00]b\n[00:30.00]c")
        assertEquals(-1, LrcParser.activeIndex(lines, 0L))
        assertEquals(-1, LrcParser.activeIndex(lines, 9_999L))
        assertEquals(0, LrcParser.activeIndex(lines, 10_000L))
        assertEquals(1, LrcParser.activeIndex(lines, 20_001L))
        assertEquals(2, LrcParser.activeIndex(lines, 300_000L))
    }

    @Test
    fun `activeLine on empty list`() {
        assertEquals(-1, LrcParser.activeIndex(emptyList(), 1000L))
    }
}
