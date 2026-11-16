package com.grammarpeace.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.grammarpeace.app.core.AiSettings
import com.grammarpeace.app.core.Language
import com.grammarpeace.app.core.Provider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Reads and writes the user's settings. API keys are encrypted with [cipher] before they touch disk. */
class SettingsRepository(
    private val store: DataStore<Preferences>,
    private val cipher: SecretCipher,
) {
    val settings: Flow<AiSettings> = store.data
        // A corrupt or unreadable file should not crash the app, fall back to defaults.
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map(::toSettings)

    suspend fun current(): AiSettings = settings.first()

    suspend fun save(settings: AiSettings) {
        store.edit { prefs ->
            prefs[PROVIDER] = settings.provider.id
            prefs.putOrRemove(CLAUDE_MODEL, settings.claudeModel, AiSettings.DEFAULT_CLAUDE_MODEL)
            prefs.putOrRemove(OPENAI_BASE_URL, settings.openAiBaseUrl, AiSettings.DEFAULT_OPENAI_BASE_URL)
            prefs.putOrRemove(OPENAI_MODEL, settings.openAiModel, AiSettings.DEFAULT_OPENAI_MODEL)
            prefs[GRAMMAR_LANGUAGE] = settings.grammarLanguage.code
            prefs[TRANSLATE_TO] = settings.translateTo.code
            prefs[DICTIONARY] = settings.customDictionary.joinToString("\n")
            for (provider in Provider.entries) {
                val key = settings.apiKeys[provider].orEmpty().trim()
                if (key.isEmpty()) prefs.remove(apiKey(provider)) else prefs[apiKey(provider)] = cipher.encrypt(key)
            }
        }
    }

    suspend fun setTranslateTo(language: Language) {
        store.edit { it[TRANSLATE_TO] = language.code }
    }

    private fun toSettings(prefs: Preferences): AiSettings = AiSettings(
        provider = Provider.fromId(prefs[PROVIDER]),
        apiKeys = Provider.entries.mapNotNull { provider ->
            prefs[apiKey(provider)]?.let(cipher::decrypt)?.takeIf { it.isNotBlank() }?.let { provider to it }
        }.toMap(),
        claudeModel = prefs[CLAUDE_MODEL].orDefault(AiSettings.DEFAULT_CLAUDE_MODEL),
        openAiBaseUrl = prefs[OPENAI_BASE_URL].orDefault(AiSettings.DEFAULT_OPENAI_BASE_URL),
        openAiModel = prefs[OPENAI_MODEL].orDefault(AiSettings.DEFAULT_OPENAI_MODEL),
        grammarLanguage = Language.fromCode(prefs[GRAMMAR_LANGUAGE], Language.AUTO),
        translateTo = Language.fromCode(prefs[TRANSLATE_TO], Language.EN).takeIf { it != Language.AUTO } ?: Language.EN,
        customDictionary = prefs[DICTIONARY].orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() },
    )

    private fun String?.orDefault(default: String): String = this?.trim()?.takeIf { it.isNotEmpty() } ?: default

    private fun androidx.datastore.preferences.core.MutablePreferences.putOrRemove(
        key: Preferences.Key<String>,
        value: String,
        default: String,
    ) {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed == default) remove(key) else this[key] = trimmed
    }

    private companion object {
        // Names match the Chrome extension's storage keys where one exists.
        val PROVIDER = stringPreferencesKey("apiProvider")
        val CLAUDE_MODEL = stringPreferencesKey("claudeModel")
        val OPENAI_BASE_URL = stringPreferencesKey("openaiBaseUrl")
        val OPENAI_MODEL = stringPreferencesKey("openaiModel")
        val GRAMMAR_LANGUAGE = stringPreferencesKey("defaultGrammarLang")
        val TRANSLATE_TO = stringPreferencesKey("defaultToLang")
        val DICTIONARY = stringPreferencesKey("customDictionary")

        fun apiKey(provider: Provider) = stringPreferencesKey("apiKey.${provider.id}")
    }
}
