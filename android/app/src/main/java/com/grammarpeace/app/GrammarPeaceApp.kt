package com.grammarpeace.app

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.grammarpeace.app.core.HttpWritingEngine
import com.grammarpeace.app.core.WritingEngine
import com.grammarpeace.app.data.KeystoreCipher
import com.grammarpeace.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** The app's long-lived objects. Tests swap this out through [GrammarPeaceApp.container]. */
class AppContainer(
    val settings: SettingsRepository,
    val engine: WritingEngine,
    /** For work that must finish even if the screen that started it goes away. Runs in call order. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
)

open class GrammarPeaceApp : Application() {
    @Volatile
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        val settings = SettingsRepository(settingsStore, KeystoreCipher())
        container = AppContainer(settings, HttpWritingEngine(settings = settings::current))
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as GrammarPeaceApp).container
