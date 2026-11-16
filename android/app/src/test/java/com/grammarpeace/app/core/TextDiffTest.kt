package com.grammarpeace.app.core

import com.grammarpeace.app.core.TextDiff.Kind.ADDED
import com.grammarpeace.app.core.TextDiff.Kind.REMOVED
import com.grammarpeace.app.core.TextDiff.Kind.SAME
import com.grammarpeace.app.core.TextDiff.Segment
import org.junit.Assert.assertEquals
import org.junit.Test

class TextDiffTest {

    @Test
    fun `tokens join back into the original text`() {
        val text = "  Hello,  world!\nNew line\t"
        assertEquals(text, TextDiff.tokenize(text).joinToString(""))
        assertEquals(emptyList<String>(), TextDiff.tokenize(""))
    }

    @Test
    fun `identical text is one unchanged segment`() {
        assertEquals(listOf(Segment("same text", SAME)), TextDiff.segments("same text", "same text"))
    }

    @Test
    fun `a replaced word shows as removed then added`() {
        assertEquals(
            listOf(
                Segment("I ", SAME),
                Segment("has", REMOVED),
                Segment("have", ADDED),
                Segment(" an apple", SAME),
            ),
            TextDiff.segments("I has an apple", "I have an apple"),
        )
    }

    @Test
    fun `the new text can always be rebuilt from the diff`() {
        val old = "their going to the store tomorow , and buyed some apple"
        val new = "They're going to the store tomorrow, and bought some apples."
        val segments = TextDiff.segments(old, new)

        assertEquals(new, segments.filter { it.kind != REMOVED }.joinToString("") { it.text })
        assertEquals(old, segments.filter { it.kind != ADDED }.joinToString("") { it.text })
    }

    @Test
    fun `empty sides are handled`() {
        assertEquals(listOf(Segment("new", ADDED)), TextDiff.segments("", "new"))
        assertEquals(listOf(Segment("old", REMOVED)), TextDiff.segments("old", ""))
        assertEquals(emptyList<Segment>(), TextDiff.segments("", ""))
    }

    @Test
    fun `very long text skips the diff instead of stalling`() {
        val long = (1..400).joinToString(" ") { "word$it" }
        assertEquals(listOf(Segment(long, SAME)), TextDiff.segments("something else", long, maxTokens = 600))
    }
}
