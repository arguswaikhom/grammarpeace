package com.grammarpeace.app.core

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class HttpWritingEngineTest {

    private val server = MockWebServer()
    private var settings = AiSettings(apiKeys = mapOf(Provider.CLAUDE to "sk-ant-test"))

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    private fun engine() = HttpWritingEngine(
        settings = { settings },
        http = OkHttpClient(),
        claudeBaseUrl = server.url("/v1").toString(),
        openRouterBaseUrl = server.url("/or/v1").toString(),
        retryDelayMillis = 0,
    )

    private fun enqueue(body: String, code: Int = 200) =
        server.enqueue(MockResponse.Builder().code(code).body(body).build())

    private fun RecordedRequest.json(): JsonObject = Json.parseToJsonElement(body!!.utf8()).jsonObject

    private suspend fun failure(block: suspend () -> Unit): AiException {
        try {
            block()
        } catch (e: AiException) {
            return e
        }
        fail("Expected an AiException")
        throw IllegalStateException()
    }

    // region Claude

    @Test
    fun `claude request has the right headers and body`() = runTest {
        enqueue("""{"stop_reason":"end_turn","content":[{"type":"text","text":" I have an apple. "}]}""")

        val result = engine().run(WritingAction.Fix, "i has a apple")

        assertEquals("I have an apple.", result)
        val request = server.takeRequest()
        assertEquals("/v1/messages", request.url.encodedPath)
        assertEquals("sk-ant-test", request.headers["x-api-key"])
        assertEquals("2023-06-01", request.headers["anthropic-version"])
        assertTrue(request.headers["Content-Type"]!!.startsWith("application/json"))
        val body = request.json()
        assertEquals("claude-haiku-5-5", body["model"]!!.jsonPrimitive.content)
        assertEquals("low", body["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertEquals(HttpWritingEngine.outputBudget("i has a apple"), body["max_tokens"]!!.jsonPrimitive.int)
        val message = body["messages"]!!.jsonArray.single().jsonObject
        assertEquals("user", message["role"]!!.jsonPrimitive.content)
        assertEquals(
            Prompts.grammar("i has a apple", Language.AUTO, emptyList()),
            message["content"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun `effort is not sent for a custom claude model`() = runTest {
        settings = settings.copy(claudeModel = "claude-haiku-4-5")
        enqueue("""{"content":[{"type":"text","text":"ok"}]}""")

        engine().run(WritingAction.Fix, "text")

        assertNull(server.takeRequest().json()["output_config"])
    }

    @Test
    fun `thinking blocks are skipped and text blocks joined`() = runTest {
        enqueue(
            """{"stop_reason":"end_turn","content":[
                {"type":"thinking","thinking":"","signature":"abc"},
                {"type":"text","text":"Hello, "},
                {"type":"text","text":"world."}]}"""
        )

        assertEquals("Hello, world.", engine().run(WritingAction.Fix, "helo wrld"))
    }

    @Test
    fun `a cut-off answer is an error, never a result`() = runTest {
        enqueue("""{"stop_reason":"max_tokens","content":[{"type":"text","text":"Half an ans"}]}""")

        val error = failure { engine().run(WritingAction.Fix, "text") }

        assertEquals(AiException.Reason.TOO_LONG, error.reason)
    }

    @Test
    fun `a refusal is reported`() = runTest {
        enqueue("""{"stop_reason":"refusal","content":[]}""")

        assertEquals(AiException.Reason.REFUSED, failure { engine().run(WritingAction.Fix, "text") }.reason)
    }

    @Test
    fun `the provider's own error message is shown`() = runTest {
        enqueue("""{"type":"error","error":{"type":"invalid_request_error","message":"model: not found"}}""", 400)

        val error = failure { engine().run(WritingAction.Fix, "text") }

        assertEquals("model: not found", error.message)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a rejected key is flagged so the UI can point to settings`() = runTest {
        enqueue("""{"error":{"message":"invalid x-api-key"}}""", 401)

        assertEquals(AiException.Reason.BAD_KEY, failure { engine().run(WritingAction.Fix, "text") }.reason)
    }

    @Test
    fun `a missing key fails before any request is made`() = runTest {
        settings = AiSettings()

        val error = failure { engine().run(WritingAction.Fix, "text") }

        assertEquals(AiException.Reason.MISSING_KEY, error.reason)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an overloaded response is retried once`() = runTest {
        enqueue("""{"error":{"message":"Overloaded"}}""", 529)
        enqueue("""{"content":[{"type":"text","text":"Fixed."}]}""")

        assertEquals("Fixed.", engine().run(WritingAction.Fix, "text"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a second overloaded response is reported`() = runTest {
        enqueue("""{"error":{"message":"Overloaded"}}""", 529)
        enqueue("""{"error":{"message":"Overloaded"}}""", 529)

        assertEquals("Overloaded", failure { engine().run(WritingAction.Fix, "text") }.message)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a dropped connection is retried, then reported as a network problem`() = runTest {
        repeat(2) {
            server.enqueue(MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build())
        }

        val error = failure { engine().run(WritingAction.Fix, "text") }

        assertEquals(AiException.Reason.NETWORK, error.reason)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a non-JSON reply is an error`() = runTest {
        enqueue("<html>gateway</html>")

        assertEquals("Invalid response from Claude.", failure { engine().run(WritingAction.Fix, "text") }.message)
    }

    @Test
    fun `the output budget grows with the input and is capped`() {
        assertEquals(HttpWritingEngine.MIN_OUTPUT_TOKENS, HttpWritingEngine.outputBudget(""))
        assertEquals(HttpWritingEngine.MIN_OUTPUT_TOKENS + 5_000, HttpWritingEngine.outputBudget("a".repeat(10_000)))
        assertEquals(HttpWritingEngine.MAX_OUTPUT_TOKENS, HttpWritingEngine.outputBudget("a".repeat(1_000_000)))
    }

    // endregion

    // region OpenAI-compatible

    @Test
    fun `a custom openai endpoint gets bearer auth and max_tokens`() = runTest {
        settings = AiSettings(
            provider = Provider.OPENAI,
            apiKeys = mapOf(Provider.OPENAI to "sk-test"),
            openAiBaseUrl = server.url("/custom/v1/").toString(),
            openAiModel = "my-model",
        )
        enqueue("""{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"Bonjour"}}]}""")

        assertEquals("Bonjour", engine().run(WritingAction.Translate(Language.FR), "Hello"))

        val request = server.takeRequest()
        assertEquals("/custom/v1/chat/completions", request.url.encodedPath)
        assertEquals("Bearer sk-test", request.headers["Authorization"])
        val body = request.json()
        assertEquals("my-model", body["model"]!!.jsonPrimitive.content)
        assertTrue("max_tokens" in body)
        assertFalse("max_completion_tokens" in body)
    }

    @Test
    fun `openrouter uses its own endpoint and free model`() = runTest {
        settings = AiSettings(provider = Provider.OPENROUTER, apiKeys = mapOf(Provider.OPENROUTER to "sk-or-test"))
        enqueue("""{"choices":[{"message":{"content":"Better text"}}]}""")

        assertEquals("Better text", engine().run(WritingAction.Rewrite(Tone.CLEAR), "text"))

        val request = server.takeRequest()
        assertEquals("/or/v1/chat/completions", request.url.encodedPath)
        assertEquals("openrouter/free", request.json()["model"]!!.jsonPrimitive.content)
    }

    @Test
    fun `chat completion problems are reported`() = runTest {
        settings = AiSettings(provider = Provider.OPENROUTER, apiKeys = mapOf(Provider.OPENROUTER to "k"))

        enqueue("""{"choices":[{"finish_reason":"length","message":{"content":"Half"}}]}""")
        assertEquals(AiException.Reason.TOO_LONG, failure { engine().run(WritingAction.Fix, "t") }.reason)

        enqueue("""{"choices":[{"message":{"content":null,"refusal":"No."}}]}""")
        assertEquals(AiException.Reason.REFUSED, failure { engine().run(WritingAction.Fix, "t") }.reason)

        enqueue("""{"choices":[{"message":{"content":"  "}}]}""")
        assertTrue(failure { engine().run(WritingAction.Fix, "t") }.message!!.contains("empty response"))

        enqueue("""{"choices":[]}""")
        assertEquals("Invalid response from OpenRouter.", failure { engine().run(WritingAction.Fix, "t") }.message)
    }

    @Test
    fun `an unusable base url is explained`() = runTest {
        settings = AiSettings(
            provider = Provider.OPENAI,
            apiKeys = mapOf(Provider.OPENAI to "k"),
            openAiBaseUrl = "not a url",
        )

        assertTrue(failure { engine().run(WritingAction.Fix, "t") }.message!!.contains("base URL"))
    }

    // endregion

    @Test
    fun `alternatives and explanation use their prompts`() = runTest {
        enqueue("""{"content":[{"type":"text","text":"1. Option one\n2. Option two"}]}""")
        enqueue("""{"content":[{"type":"text","text":"- has -> have"}]}""")
        val engine = engine()

        assertEquals(listOf("Option one", "Option two"), engine.alternatives("i has", "I have"))
        assertEquals("- has -> have", engine.explain("i has", "I have"))

        val first = server.takeRequest().json()["messages"]!!.jsonArray.single().jsonObject["content"]!!.jsonPrimitive.content
        val second = server.takeRequest().json()["messages"]!!.jsonArray.single().jsonObject["content"]!!.jsonPrimitive.content
        assertEquals(Prompts.alternatives("i has", "I have", Language.AUTO), first)
        assertEquals(Prompts.explain("i has", "I have", Language.AUTO), second)
    }
}
