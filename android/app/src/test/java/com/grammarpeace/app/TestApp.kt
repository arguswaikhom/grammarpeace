package com.grammarpeace.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.grammarpeace.app.core.AiSettings
import com.grammarpeace.app.core.Provider
import com.grammarpeace.app.core.WritingAction
import com.grammarpeace.app.core.WritingEngine
import com.grammarpeace.app.data.SecretCipher
import com.grammarpeace.app.data.SettingsRepository
import java.io.File
import kotlinx.coroutines.runBlocking

/** Used by Robolectric instead of the real application: no network, no Keystore. */
class TestApp : GrammarPeaceApp() {
    lateinit var engine: FakeEngine
    lateinit var settingsFile: File

    override fun onCreate() {
        // Deliberately not calling through to the real container setup.
        engine = FakeEngine()
        settingsFile = File(cacheDir, "settings-${System.nanoTime()}.preferences_pb")
        val repository = SettingsRepository(PreferenceDataStoreFactory.create { settingsFile }, FakeCipher)
        runBlocking { repository.save(AiSettings(apiKeys = mapOf(Provider.CLAUDE to "sk-ant-test"))) }
        container = AppContainer(repository, engine)
    }
}

object FakeCipher : SecretCipher {
    override fun encrypt(plain: String) = "enc:" + plain.reversed()
    override fun decrypt(encoded: String) = encoded.removePrefix("enc:").reversed().takeIf { encoded.startsWith("enc:") }
}

class FakeEngine : WritingEngine {
    var onRun: suspend (WritingAction, String) -> String = { _, text -> text }
    var onAlternatives: suspend () -> List<String> = { emptyList() }
    var onExplain: suspend () -> String = { "" }
    var onTest: suspend (AiSettings) -> Unit = {}
    val runs = mutableListOf<Pair<WritingAction, String>>()

    override suspend fun run(action: WritingAction, text: String): String {
        runs += action to text
        return onRun(action, text)
    }

    override suspend fun alternatives(original: String, corrected: String) = onAlternatives()
    override suspend fun explain(original: String, corrected: String) = onExplain()
    override suspend fun test(settings: AiSettings) = onTest(settings)
}
