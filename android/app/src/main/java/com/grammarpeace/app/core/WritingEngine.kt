package com.grammarpeace.app.core

import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** A failure the user can act on. [message] is ready to show as is. */
class AiException(
    message: String,
    val reason: Reason = Reason.OTHER,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Reason { MISSING_KEY, BAD_KEY, NETWORK, TOO_LONG, REFUSED, OTHER }
}

/** The writing features of the app. An interface so the UI can be tested without a network. */
interface WritingEngine {
    /** Runs [action] on [text] and returns the rewritten text. */
    suspend fun run(action: WritingAction, text: String): String

    /** Two other ways to phrase [corrected]. */
    suspend fun alternatives(original: String, corrected: String): List<String>

    /** A short explanation of what changed between [original] and [corrected]. */
    suspend fun explain(original: String, corrected: String): String

    /** Makes one tiny request with [settings] to check that the key and model work. */
    suspend fun test(settings: AiSettings)
}

/** Calls Claude, OpenAI (or any OpenAI-compatible endpoint), or OpenRouter directly with the user's own key. */
class HttpWritingEngine(
    private val settings: suspend () -> AiSettings,
    private val http: OkHttpClient = defaultHttpClient(),
    private val claudeBaseUrl: String = "https://api.anthropic.com/v1",
    private val openRouterBaseUrl: String = "https://openrouter.ai/api/v1",
    private val retryDelayMillis: Long = 700,
) : WritingEngine {

    override suspend fun run(action: WritingAction, text: String): String {
        val current = settings()
        val prompt = when (action) {
            WritingAction.Fix -> Prompts.grammar(text, current.grammarLanguage, current.customDictionary)
            is WritingAction.Rewrite -> Prompts.tone(text, action.tone)
            is WritingAction.Translate -> Prompts.translate(text, action.target)
        }
        return complete(current, prompt, outputBudget(text))
    }

    override suspend fun alternatives(original: String, corrected: String): List<String> {
        val current = settings()
        val reply = complete(
            current,
            Prompts.alternatives(original, corrected, current.grammarLanguage),
            outputBudget(corrected, copies = 2),
        )
        return Prompts.parseAlternatives(reply)
    }

    override suspend fun explain(original: String, corrected: String): String {
        val current = settings()
        return complete(current, Prompts.explain(original, corrected, current.grammarLanguage), MIN_OUTPUT_TOKENS)
    }

    override suspend fun test(settings: AiSettings) {
        complete(settings, "Reply with the single word OK.", MIN_OUTPUT_TOKENS)
    }

    private suspend fun complete(settings: AiSettings, prompt: String, maxTokens: Int): String {
        val key = settings.apiKey.trim()
        if (key.isEmpty()) {
            throw AiException(
                "Add your ${settings.provider.displayName} API key in Settings.",
                AiException.Reason.MISSING_KEY,
            )
        }
        return when (settings.provider) {
            Provider.CLAUDE -> callClaude(key, settings.claudeModel, prompt, maxTokens)
            Provider.OPENAI -> {
                val baseUrl = settings.openAiBaseUrl.trim().trimEnd('/')
                callChatCompletions(
                    baseUrl = baseUrl,
                    key = key,
                    model = settings.openAiModel,
                    // OpenAI's own API renamed the limit; compatible servers still expect the old name.
                    tokenParam = if (baseUrl == AiSettings.DEFAULT_OPENAI_BASE_URL) "max_completion_tokens" else "max_tokens",
                    maxTokens = maxTokens,
                    prompt = prompt,
                    providerName = "OpenAI",
                )
            }
            Provider.OPENROUTER -> callChatCompletions(
                baseUrl = openRouterBaseUrl,
                key = key,
                model = "openrouter/free",
                tokenParam = "max_tokens",
                maxTokens = maxTokens,
                prompt = prompt,
                providerName = "OpenRouter",
            )
        }
    }

    // region Claude

    private suspend fun callClaude(key: String, model: String, prompt: String, maxTokens: Int): String {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                })
            }
            // Grammar fixes are simple, low effort keeps answers fast and cheap.
            // Only sent with the default model because older models reject the parameter.
            if (model == AiSettings.DEFAULT_CLAUDE_MODEL) {
                putJsonObject("output_config") { put("effort", "low") }
            }
        }
        val request = Request.Builder()
            .url("$claudeBaseUrl/messages")
            .header("x-api-key", key)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val json = send(request, "Claude")

        when (json.string("stop_reason")) {
            "refusal" -> throw AiException("Claude declined to rewrite this text.", AiException.Reason.REFUSED)
            "max_tokens" -> throw AiException(TOO_LONG, AiException.Reason.TOO_LONG)
        }

        // Content can include thinking blocks, so only join the text blocks.
        val text = (json["content"] as? JsonArray).orEmpty()
            .filterIsInstance<JsonObject>()
            .filter { it.string("type") == "text" }
            .joinToString("") { it.string("text").orEmpty() }
            .trim()
        if (text.isEmpty()) throw AiException("Claude returned an empty response. Please try again.")
        return text
    }

    // endregion

    // region OpenAI-compatible

    private suspend fun callChatCompletions(
        baseUrl: String,
        key: String,
        model: String,
        tokenParam: String,
        maxTokens: Int,
        prompt: String,
        providerName: String,
    ): String {
        val url = "$baseUrl/chat/completions".toHttpUrlOrNull()
            ?: throw AiException("The $providerName base URL in Settings is not a valid URL.")
        val body = buildJsonObject {
            put("model", model)
            put(tokenParam, maxTokens)
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                })
            }
        }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val json = send(request, providerName)

        val choice = (json["choices"] as? JsonArray)?.firstOrNull() as? JsonObject
        val message = choice?.get("message") as? JsonObject
            ?: throw AiException("Invalid response from $providerName.")
        if (choice.string("finish_reason") == "length") {
            throw AiException(TOO_LONG, AiException.Reason.TOO_LONG)
        }
        message.string("refusal")?.takeIf { it.isNotBlank() }?.let {
            throw AiException("$providerName refused to respond: $it", AiException.Reason.REFUSED)
        }
        val text = message.string("content").orEmpty().trim()
        if (text.isEmpty()) throw AiException("$providerName returned an empty response. Please try again.")
        return text
    }

    // endregion

    // region Networking

    /** Sends [request], retrying once when the provider is overloaded or the connection drops. */
    private suspend fun send(request: Request, providerName: String): JsonObject {
        var attempt = 0
        while (true) {
            attempt++
            val canRetry = attempt < MAX_ATTEMPTS
            val response = try {
                http.newCall(request).await()
            } catch (e: IOException) {
                if (canRetry && e !is InterruptedIOException) {
                    delay(retryDelayMillis)
                    continue
                }
                throw AiException(networkMessage(e), AiException.Reason.NETWORK, e)
            }

            response.use {
                val raw = try {
                    it.body.string()
                } catch (e: IOException) {
                    throw AiException(networkMessage(e), AiException.Reason.NETWORK, e)
                }
                val json = runCatching { LENIENT_JSON.parseToJsonElement(raw) as? JsonObject }.getOrNull()

                if (it.isSuccessful) {
                    return json ?: throw AiException("Invalid response from $providerName.")
                }
                if (canRetry && it.code in RETRYABLE_STATUS) {
                    delay(retryDelayMillis)
                    return@use
                }
                val apiMessage = (json?.get("error") as? JsonObject)?.string("message")
                throw when (it.code) {
                    401, 403 -> AiException(
                        apiMessage ?: "$providerName rejected the API key. Check it in Settings.",
                        AiException.Reason.BAD_KEY,
                    )
                    else -> AiException(apiMessage ?: "$providerName request failed (${it.code}).")
                }
            }
        }
    }

    private fun networkMessage(e: IOException): String = when (e) {
        is UnknownHostException -> "No internet connection."
        is InterruptedIOException -> "The request timed out. Please try again."
        else -> "Couldn't reach the AI provider. Check your connection and try again."
    }

    // endregion

    companion object {
        const val TOO_LONG =
            "The answer was cut off, so nothing was changed. Try a shorter piece of text."

        /** Leaves room for the model's own reasoning on top of a short answer. */
        const val MIN_OUTPUT_TOKENS = 4096
        const val MAX_OUTPUT_TOKENS = 16000

        private const val MAX_ATTEMPTS = 2
        private val RETRYABLE_STATUS = setOf(429, 500, 502, 503, 504, 529)
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val LENIENT_JSON = Json { ignoreUnknownKeys = true; isLenient = true }

        /**
         * A rewrite is about as long as its input, so the limit has to grow with it.
         * A cut-off answer must never be offered as a replacement for the user's text.
         */
        fun outputBudget(text: String, copies: Int = 1): Int {
            val estimate = text.toByteArray(Charsets.UTF_8).size / 2 * copies + MIN_OUTPUT_TOKENS
            return estimate.coerceIn(MIN_OUTPUT_TOKENS, MAX_OUTPUT_TOKENS)
        }

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(90, TimeUnit.SECONDS)
            .build()
    }
}

private fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()

/** Suspends until the call finishes, cancelling the HTTP request if the coroutine is cancelled. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, unclaimed, _ -> unclaimed.close() }
        }

        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }
    })
}
