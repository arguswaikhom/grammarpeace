package com.grammarpeace.app.ui

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.grammarpeace.app.TestApp
import com.grammarpeace.app.core.AiException
import com.grammarpeace.app.core.Language
import com.grammarpeace.app.core.WritingAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuickActivityTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: TestApp get() = ApplicationProvider.getApplicationContext()

    private fun processText(text: String, readOnly: Boolean = false) =
        Intent(app, QuickActivity::class.java)
            .setAction(Intent.ACTION_PROCESS_TEXT)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_PROCESS_TEXT, text)
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, readOnly)

    @Test
    fun `selected text is fixed straight away and Replace hands the result back`() {
        app.engine.onRun = { _, _ -> "I have an apple." }

        val scenario = ActivityScenario.launchActivityForResult<QuickActivity>(processText("i has a apple"))

        compose.onNodeWithContentDescription("Result: I have an apple.").assertIsDisplayed()
        assertEquals(listOf(WritingAction.Fix to "i has a apple"), app.engine.runs)

        compose.onNodeWithText("Replace").performClick()

        assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
        assertEquals("I have an apple.", scenario.result.resultData.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT))
    }

    @Test
    fun `read-only text offers Copy but not Replace`() {
        app.engine.onRun = { _, _ -> "I have an apple." }

        val scenario = ActivityScenario.launchActivityForResult<QuickActivity>(processText("i has a apple", readOnly = true))

        compose.onNodeWithContentDescription("Result: I have an apple.").assertIsDisplayed()
        compose.onNodeWithText("Replace").assertDoesNotExist()

        compose.onNodeWithText("Copy").performClick()

        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("I have an apple.", clipboard.primaryClip!!.getItemAt(0).text.toString())
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
    }

    @Test
    fun `text that needs no fixing says so and has nothing to replace`() {
        val scenario = ActivityScenario.launchActivityForResult<QuickActivity>(processText("All good here."))

        compose.onNodeWithText("No grammar errors found.").assertIsDisplayed()
        compose.onNodeWithText("Replace").assertDoesNotExist()
        scenario.close()
    }

    @Test
    fun `another action can be run from the sheet and its result replaces the text`() {
        app.engine.onRun = { action, _ -> if (action is WritingAction.Translate) "Bonjour" else "Hello." }
        val scenario = ActivityScenario.launchActivityForResult<QuickActivity>(processText("hello"))
        compose.onNodeWithContentDescription("Result: Hello.").assertIsDisplayed()

        compose.onNodeWithText("Translate").performClick()
        compose.onNodeWithText("French").performClick()

        compose.onNodeWithContentDescription("Result: Bonjour").assertIsDisplayed()
        assertEquals(WritingAction.Translate(Language.FR) to "hello", app.engine.runs.last())
        compose.onNodeWithText("Replace").performClick()
        assertEquals("Bonjour", scenario.result.resultData.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT))
    }

    @Test
    fun `a missing key points to settings instead of offering a retry`() {
        app.engine.onRun = { _, _ -> throw AiException("Add your Claude API key in Settings.", AiException.Reason.MISSING_KEY) }

        ActivityScenario.launchActivityForResult<QuickActivity>(processText("text")).use {
            compose.onNodeWithText("Add your Claude API key in Settings.").assertIsDisplayed()
            compose.onNodeWithText("Open settings").assertIsDisplayed()
            compose.onNodeWithText("Try again").assertDoesNotExist()
        }
    }

    @Test
    fun `a failed request can be retried`() {
        var calls = 0
        app.engine.onRun = { _, _ -> if (++calls == 1) throw AiException("No internet connection.") else "Fixed." }

        ActivityScenario.launchActivityForResult<QuickActivity>(processText("text")).use {
            compose.onNodeWithText("No internet connection.").assertIsDisplayed()
            compose.onNodeWithText("Try again").performClick()
            compose.onNodeWithContentDescription("Result: Fixed.").assertIsDisplayed()
        }
    }

    @Test
    fun `shared text is handled read-only`() {
        app.engine.onRun = { _, _ -> "Shared and fixed." }
        val intent = Intent(app, QuickActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "shared and fixd")

        ActivityScenario.launchActivityForResult<QuickActivity>(intent).use {
            compose.onNodeWithContentDescription("Result: Shared and fixed.").assertIsDisplayed()
            compose.onNodeWithText("Replace").assertDoesNotExist()
        }
    }

    @Test
    fun `an intent without text closes immediately`() {
        val scenario = ActivityScenario.launchActivityForResult<QuickActivity>(
            Intent(app, QuickActivity::class.java).setAction(Intent.ACTION_PROCESS_TEXT)
        )

        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        assertTrue(app.engine.runs.isEmpty())
    }
}
