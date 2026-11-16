package com.grammarpeace.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.grammarpeace.app.TestApp
import com.grammarpeace.app.core.AiSettings
import com.grammarpeace.app.core.Provider
import com.grammarpeace.app.core.Tone
import com.grammarpeace.app.core.WritingAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: TestApp get() = ApplicationProvider.getApplicationContext()

    private fun setClipboard(text: String) {
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("test", text))
    }

    @Test
    fun `typing text and tapping Fix shows the correction`() {
        app.engine.onRun = { _, _ -> "I have an apple." }

        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("✌️ Fix").assertIsNotEnabled()

            compose.onNodeWithTag("input").performTextInput("i has a apple")
            compose.onNodeWithText("3 words", substring = true).assertDoesNotExist()
            compose.onNodeWithText("4 words").assertIsDisplayed()
            compose.onNodeWithText("✌️ Fix").assertIsEnabled().performClick()

            compose.onNodeWithContentDescription("Result: I have an apple.").assertIsDisplayed()
            assertEquals(listOf(WritingAction.Fix to "i has a apple"), app.engine.runs)
        }
    }

    @Test
    fun `tone menu runs the picked tone`() {
        app.engine.onRun = { _, _ -> "Good day." }

        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithTag("input").performTextInput("hey")
            compose.onNodeWithText("Tone").performClick()
            compose.onNodeWithText("Formal").performClick()

            compose.onNodeWithContentDescription("Result: Good day.").assertIsDisplayed()
            assertEquals(WritingAction.Rewrite(Tone.FORMAL) to "hey", app.engine.runs.single())
        }
    }

    @Test
    fun `an alternative can be picked and becomes the result`() {
        app.engine.onRun = { _, _ -> "I have an apple." }
        app.engine.onAlternatives = { listOf("I've got an apple.", "There is an apple with me.") }
        app.engine.onExplain = { "- has -> have: subject-verb agreement" }

        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithTag("input").performTextInput("i has a apple")
            compose.onNodeWithText("✌️ Fix").performClick()

            compose.onNodeWithText("Alternatives").performScrollTo().performClick()
            compose.onNodeWithText("I've got an apple.").performScrollTo().performClick()
            compose.onNodeWithContentDescription("Result: I've got an apple.").assertExists()

            compose.onNodeWithText("Explain").performScrollTo().performClick()
            compose.onNodeWithText("- has -> have: subject-verb agreement").assertExists()

            compose.onNodeWithText("Keep editing").performScrollTo().performClick()
            compose.onNodeWithTag("input").assertTextContains("I've got an apple.")
        }
    }

    @Test
    fun `paste fills the input from the clipboard`() {
        setClipboard("copied text")

        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Paste").performClick()
            compose.onNodeWithTag("input").assertTextContains("copied text")
            assertTrue(app.engine.runs.isEmpty())
        }
    }

    @Test
    fun `opening from the tile checks the clipboard straight away`() {
        setClipboard("i has a apple")
        app.engine.onRun = { _, _ -> "I have an apple." }

        ActivityScenario.launch<MainActivity>(MainActivity.checkClipboardIntent(app)).use {
            compose.onNodeWithContentDescription("Result: I have an apple.").assertIsDisplayed()
            assertEquals(listOf(WritingAction.Fix to "i has a apple"), app.engine.runs)
        }
    }

    @Test
    fun `without a key the editor asks for one`() {
        runBlocking { app.container.settings.save(AiSettings()) }

        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Add an API key to get started").assertIsDisplayed()
        }
    }

    @Test
    fun `settings are saved as they are typed, with the key encrypted on disk`() {
        runBlocking { app.container.settings.save(AiSettings()) }

        ActivityScenario.launch<MainActivity>(MainActivity.settingsIntent(app)).use {
            compose.onNodeWithText("OpenAI").performClick()
            compose.onNodeWithTag("apiKey").performTextInput("sk-secret-123")
            compose.waitForIdle()

            val saved = runBlocking { app.container.settings.current() }
            assertEquals(Provider.OPENAI, saved.provider)
            assertEquals("sk-secret-123", saved.apiKey)
            assertFalse(app.settingsFile.readBytes().toString(Charsets.ISO_8859_1).contains("sk-secret-123"))
        }
    }

    @Test
    fun `the key can be tested from settings`() {
        var tested: AiSettings? = null
        app.engine.onTest = { tested = it }

        ActivityScenario.launch<MainActivity>(MainActivity.settingsIntent(app)).use {
            compose.onNodeWithText("Test").performClick()
            compose.onNodeWithText("It works.").assertIsDisplayed()
            assertEquals("sk-ant-test", tested?.apiKey)
        }
    }
}
