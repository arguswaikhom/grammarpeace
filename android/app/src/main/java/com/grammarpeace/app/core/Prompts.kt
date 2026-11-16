package com.grammarpeace.app.core

/** Same prompts as the Chrome extension (background.js) and the iOS keyboard, so results match across platforms. */
object Prompts {

    fun grammar(text: String, language: Language, dictionary: List<String>): String {
        val dictionaryInstruction =
            if (dictionary.isEmpty()) ""
            else "- These words are correct and should NOT be flagged as errors: ${dictionary.joinToString(", ")}\n"

        if (language == Language.AUTO) {
            return """You are a grammar correction assistant. Your ONLY task is to:
1. Detect the language of the input text
2. Fix ALL grammar, spelling, and punctuation errors in that SAME language
3. Maintain the EXACT same writing style, tone, and voice of the original text
4. Keep the text in its ORIGINAL language - DO NOT translate

CRITICAL RULES:
- Return ONLY the corrected text with errors fixed
- Do NOT change the tone or style
- Do NOT rewrite or rephrase unless fixing errors
- Do NOT translate to another language
- Do NOT add explanations, comments, or notes
- Do NOT use markdown formatting
- Do NOT use em dashes (—) - use hyphens (-) or commas instead
- Preserve the author's original voice and style
$dictionaryInstruction
Text to correct:
$text

Corrected text:"""
        }

        val name = language.displayName
        return """You are a grammar correction assistant. The input text is in $name. Your ONLY task is to:
1. Fix ALL grammar, spelling, and punctuation errors in $name
2. Maintain the EXACT same writing style, tone, and voice of the original text
3. Keep the text in $name - DO NOT change the language

CRITICAL RULES:
- Return ONLY the corrected text with errors fixed
- Do NOT change the tone or style
- Do NOT rewrite or rephrase unless fixing errors
- Do NOT translate to another language
- Do NOT add explanations, comments, or notes
- Do NOT use markdown formatting
- Do NOT use em dashes (—) - use hyphens (-) or commas instead
- The text must remain in $name
- Preserve the author's original voice and style
$dictionaryInstruction
Text to correct:
$text

Corrected text in $name:"""
    }

    fun tone(text: String, tone: Tone): String =
        """You are a professional writing assistant. Your task is to rewrite the following text with a ${tone.promptDescription} tone.

INSTRUCTIONS:
1. Rewrite the text to match the ${tone.promptDescription} tone
2. ${tone.promptDetails}
3. Fix any grammar, spelling, or punctuation errors
4. Keep the text in the SAME language as the input
5. Maintain the core message and meaning

CRITICAL RULES:
- Return ONLY the rewritten text
- Do NOT translate to another language
- Do NOT add explanations, comments, or notes
- Do NOT use markdown formatting
- Do NOT use em dashes (—) - use hyphens (-) or commas instead
- Write in correct, fluent English (or the original language if not English)

Original text:
$text

Rewritten text with ${tone.promptDescription} tone:"""

    fun translate(text: String, target: Language): String {
        val toLanguage = target.displayName
        return """You are a professional translator. Your task is to:
1. Detect the language of the input text
2. Translate it accurately to $toLanguage
3. Maintain the meaning, tone, and style of the original text

CRITICAL RULES:
- Return ONLY the translated text in $toLanguage
- Do NOT add explanations, comments, or notes
- Do NOT use markdown formatting
- Do NOT add phrases like "Here's the translation"
- Preserve the original formatting and structure

Text to translate:
$text

Translation in $toLanguage:"""
    }

    fun alternatives(original: String, corrected: String, language: Language): String {
        val languageName = if (language == Language.AUTO) "the detected language" else language.displayName
        return """You are a writing assistant. Given the original text and its correction, provide 2 alternative ways to express the same message in $languageName.

Each alternative should:
- Be grammatically correct
- Keep the same meaning as the original
- Have a slightly different style or word choice
- Be natural and fluent

CRITICAL RULES:
- Return EXACTLY 2 alternatives, numbered as 1. and 2.
- Each alternative should be on its own line
- Do NOT add explanations or comments
- Do NOT use markdown formatting
- Keep the same language as the original

Original text:
$original

Current correction:
$corrected

Provide 2 alternatives:"""
    }

    fun explain(original: String, corrected: String, language: Language): String {
        val languageName = if (language == Language.AUTO) "the detected language" else language.displayName
        return """You are a grammar teacher. Compare the original text with the corrected version and explain what errors were fixed.

For each correction, briefly explain:
1. What was wrong in the original
2. Why the correction is correct
3. The grammar rule that applies (if relevant)

Keep explanations concise and educational. Use simple language.

CRITICAL RULES:
- Format each correction as a bullet point
- Be brief but clear
- Focus on the most important corrections
- Do NOT use markdown headers or formatting
- Write in $languageName if the text is in that language, otherwise use English

Original text:
$original

Corrected text:
$corrected

Explain the corrections:"""
    }

    private val numbering = Regex("""^\d+[.)]\s*""")

    /** Pulls up to two alternatives out of a numbered-list reply. */
    fun parseAlternatives(response: String): List<String> =
        response.trim().lines()
            .map { it.trim().replace(numbering, "").trim() }
            .filter { it.isNotEmpty() }
            .take(2)
}
