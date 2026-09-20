package com.goalmaker.app.domain.share

import org.junit.Assert.assertEquals
import org.junit.Test

/** What a share from another app becomes before the composer reads it (spec, story 10; M5-05). */
class SharedCaptureTest {

    @Test
    fun `a shared link keeps its URL in the notes and names itself`() {
        val capture = SharedCapture.of(subject = null, text = "https://example.com/an-article")
        assertEquals("example.com/an-article", capture.line)
        assertEquals("https://example.com/an-article", capture.notes)
    }

    @Test
    fun `a link shared with its title becomes the title, with the URL behind it`() {
        val capture = SharedCapture.of(
            subject = "Why sleep matters",
            text = "Why sleep matters https://example.com/sleep",
        )
        assertEquals("Why sleep matters", capture.line)
        assertEquals("https://example.com/sleep", capture.notes)
    }

    @Test
    fun `a share with only a subject and a link takes the subject as the line`() {
        val capture = SharedCapture.of(subject = "A talk to watch", text = "https://example.com/talk")
        assertEquals("A talk to watch", capture.line)
        assertEquals("https://example.com/talk", capture.notes)
    }

    @Test
    fun `a shared passage keeps its first line and puts the rest in the notes`() {
        val capture = SharedCapture.of(
            subject = null,
            text = "Try the new oven recipe\n\n200 degrees, 40 minutes\nRest for ten minutes",
        )
        assertEquals("Try the new oven recipe", capture.line)
        assertEquals("200 degrees, 40 minutes\nRest for ten minutes", capture.notes)
    }

    @Test
    fun `a passage with a link keeps both, the link first`() {
        val capture = SharedCapture.of(
            subject = null,
            text = "Book the tickets\nhttps://example.com/tickets\nBefore Friday",
        )
        assertEquals("Book the tickets", capture.line)
        assertEquals("https://example.com/tickets\n\nBefore Friday", capture.notes)
    }

    @Test
    fun `punctuation after a link stays out of it`() {
        val capture = SharedCapture.of(subject = null, text = "Read this (https://example.com/post).")
        assertEquals("https://example.com/post", capture.notes)
    }

    @Test
    fun `an empty share has nothing to save`() {
        assertEquals(SharedCapture("", ""), SharedCapture.of(subject = null, text = "   "))
    }
}
