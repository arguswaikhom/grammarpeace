package com.grammarpeace.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptsTest {

    @Test
    fun `grammar prompt for auto-detect matches the extension`() {
        val prompt = Prompts.grammar("i has a apple", Language.AUTO, emptyList())

        assertTrue(prompt.startsWith("You are a grammar correction assistant. Your ONLY task is to:\n1. Detect the language"))
        assertTrue(prompt.contains("- Preserve the author's original voice and style\n\nText to correct:\ni has a apple\n\nCorrected text:"))
        assertTrue(prompt.endsWith("Corrected text:"))
    }

    @Test
    fun `grammar prompt names a fixed language`() {
        val prompt = Prompts.grammar("hola", Language.ES, emptyList())

        assertTrue(prompt.contains("The input text is in Spanish."))
        assertTrue(prompt.endsWith("Corrected text in Spanish:"))
    }

    @Test
    fun `custom dictionary is listed only when it has words`() {
        val with = Prompts.grammar("x", Language.AUTO, listOf("Kotlin", "GrammarPeace"))
        val without = Prompts.grammar("x", Language.AUTO, emptyList())

        assertTrue(with.contains("- These words are correct and should NOT be flagged as errors: Kotlin, GrammarPeace\n\nText to correct:"))
        assertFalse(without.contains("should NOT be flagged"))
    }

    @Test
    fun `tone prompt carries the tone's description and details`() {
        val prompt = Prompts.tone("hey", Tone.FORMAL)

        assertTrue(prompt.contains("with a formal and academic tone."))
        assertTrue(prompt.contains("2. Use sophisticated vocabulary, avoid contractions"))
        assertTrue(prompt.endsWith("Rewritten text with formal and academic tone:"))
    }

    @Test
    fun `translate prompt targets the chosen language`() {
        val prompt = Prompts.translate("hello", Language.JA)

        assertTrue(prompt.contains("2. Translate it accurately to Japanese"))
        assertTrue(prompt.endsWith("Text to translate:\nhello\n\nTranslation in Japanese:"))
    }

    @Test
    fun `text containing dollar signs and braces is passed through untouched`() {
        val text = "It costs \$5 or \${price} {ok}"

        assertTrue(Prompts.grammar(text, Language.AUTO, emptyList()).contains("Text to correct:\n$text\n"))
    }

    @Test
    fun `alternatives are parsed from a numbered list`() {
        assertEquals(
            listOf("First option.", "Second option."),
            Prompts.parseAlternatives("1. First option.\n\n2) Second option.\n3. Third option."),
        )
    }

    @Test
    fun `alternatives without numbering still parse`() {
        assertEquals(listOf("One", "Two"), Prompts.parseAlternatives("  One\nTwo  "))
        assertEquals(emptyList<String>(), Prompts.parseAlternatives("  \n "))
    }
}
