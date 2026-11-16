package com.grammarpeace.app.core

enum class Provider(
    val id: String,
    val displayName: String,
    val keyPlaceholder: String,
    val keyUrl: String,
) {
    CLAUDE("claude", "Claude", "sk-ant-...", "https://console.anthropic.com/settings/keys"),
    OPENAI("openai", "OpenAI", "sk-...", "https://platform.openai.com/api-keys"),
    OPENROUTER("openrouter", "OpenRouter (free)", "sk-or-...", "https://openrouter.ai/keys");

    companion object {
        fun fromId(id: String?): Provider = entries.firstOrNull { it.id == id } ?: CLAUDE
    }
}

enum class Tone(
    val id: String,
    val displayName: String,
    val promptDescription: String,
    val promptDetails: String,
) {
    PROFESSIONAL(
        "professional",
        "Professional",
        "professional and business-appropriate",
        "Use formal language, avoid slang, maintain clarity and professionalism suitable for business communication.",
    ),
    CASUAL(
        "casual",
        "Casual",
        "casual and conversational",
        "Use everyday language, contractions are fine, write as you would speak to a friend while keeping it clear.",
    ),
    FRIENDLY(
        "friendly",
        "Friendly",
        "friendly and warm",
        "Use welcoming and approachable language, show empathy and warmth while maintaining professionalism.",
    ),
    FORMAL(
        "formal",
        "Formal",
        "formal and academic",
        "Use sophisticated vocabulary, avoid contractions, maintain academic rigor and formal structure.",
    ),
    CONCISE(
        "concise",
        "Concise",
        "concise and to-the-point",
        "Remove unnecessary words, use direct language, keep sentences short and impactful.",
    ),

    /** "Improve" in the UI: better wording, same tone. */
    CLEAR(
        "clear",
        "Improve",
        "improved while preserving the original style",
        "Improve clarity, flow, and word choice WITHOUT changing the tone or formality level. Keep the same level of casualness/formality as the original. If the original uses contractions, keep using contractions. If the original is enthusiastic, stay enthusiastic. Only fix awkward phrasing and unclear sentences - do NOT flatten the personality or make it more generic.",
    );

    companion object {
        /** Tones offered in the Tone menu. [CLEAR] has its own Improve button. */
        val menuTones: List<Tone> = listOf(PROFESSIONAL, CASUAL, FRIENDLY, FORMAL, CONCISE)
    }
}

enum class Language(val code: String, val displayName: String) {
    AUTO("auto", "Auto-detect"),
    EN("en", "English"),
    ES("es", "Spanish"),
    FR("fr", "French"),
    DE("de", "German"),
    IT("it", "Italian"),
    PT("pt", "Portuguese"),
    RU("ru", "Russian"),
    JA("ja", "Japanese"),
    KO("ko", "Korean"),
    ZH("zh", "Chinese"),
    AR("ar", "Arabic"),
    HI("hi", "Hindi"),
    TR("tr", "Turkish"),
    NL("nl", "Dutch"),
    PL("pl", "Polish");

    companion object {
        /** Languages you can translate into (everything except auto). */
        val targets: List<Language> = entries.filter { it != AUTO }

        fun fromCode(code: String?, default: Language): Language =
            entries.firstOrNull { it.code == code } ?: default
    }
}

/** What the user asked for. */
sealed interface WritingAction {
    val title: String

    data object Fix : WritingAction {
        override val title = "Fix grammar"
    }

    data class Rewrite(val tone: Tone) : WritingAction {
        override val title = tone.displayName
    }

    data class Translate(val target: Language) : WritingAction {
        override val title = "Translate to ${target.displayName}"
    }
}

/** Everything the engine needs to make a request. A snapshot, so one request sees one consistent configuration. */
data class AiSettings(
    val provider: Provider = Provider.CLAUDE,
    val apiKeys: Map<Provider, String> = emptyMap(),
    val claudeModel: String = DEFAULT_CLAUDE_MODEL,
    val openAiBaseUrl: String = DEFAULT_OPENAI_BASE_URL,
    val openAiModel: String = DEFAULT_OPENAI_MODEL,
    val grammarLanguage: Language = Language.AUTO,
    val translateTo: Language = Language.EN,
    val customDictionary: List<String> = emptyList(),
) {
    val apiKey: String get() = apiKeys[provider].orEmpty()
    val hasApiKey: Boolean get() = apiKey.isNotBlank()

    companion object {
        const val DEFAULT_CLAUDE_MODEL = "claude-haiku-5-5"
        const val DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_OPENAI_MODEL = "gpt-5-nano"
    }
}
