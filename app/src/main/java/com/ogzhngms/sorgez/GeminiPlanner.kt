package com.ogzhngms.sorgez

import android.util.Log
import androidx.annotation.StringRes
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.APINotConfiguredException
import com.google.firebase.ai.type.FinishReason
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InvalidAPIKeyException
import com.google.firebase.ai.type.JsonSchema
import com.google.firebase.ai.type.PermissionMissingException
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.SerializationException
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.ServiceDisabledException
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONException

// Free-tier models that answer with schema JSON, best first. Each has its own capacity and quota,
// so when one cannot serve (retired, rate-limited, overloaded or too slow) the request quietly moves down this list.
internal val GEMINI_MODELS = listOf(
    "gemini-3.8-flash",
    "gemini-3.7-flash",
    "gemini-3.6-flash",
    "gemini-3.5-flash",
    "gemini-3.5-flash-lite",
    "gemini-3.1-flash-lite",
)

// Walks GEMINI_MODELS until one answers. `ask` sends the prompt to one model and returns its text;
// it is Firebase AI Logic in the app and a fake in tests. A model that cannot serve hands over to the next
// at once, and one that is merely slow gets company: after hedgeAfterMillis the next model starts alongside it,
// the first answer wins and the others are cancelled, so one stuck model never holds the plan up.
class GeminiPlanner(
    private val hedgeAfterMillis: Long = 40_000,
    private val ask: suspend (model: String, prompt: String) -> String,
) {
    suspend fun plan(prompt: String): String = coroutineScope {
        val outcomes = Channel<Result<String>>(Channel.UNLIMITED)
        val calls = mutableListOf<Job>()
        var started = 0
        var running = 0
        fun startNext() {
            val model = GEMINI_MODELS[started++]
            running++
            calls += launch {
                val outcome = try {
                    Result.success(ask(model, prompt))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Result.failure(e)
                }
                outcomes.send(outcome)
            }
        }
        startNext()
        while (true) {
            val outcome = if (started < GEMINI_MODELS.size) withTimeoutOrNull(hedgeAfterMillis) { outcomes.receive() } else outcomes.receive()
            if (outcome == null) {
                startNext()
                continue
            }
            running--
            val error = outcome.exceptionOrNull()
            if (error == null || !cannotServe(error)) {
                calls.forEach { it.cancel() }
                return@coroutineScope outcome.getOrThrow()
            }
            if (started < GEMINI_MODELS.size) startNext() else if (running == 0) throw error
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }
}

// Retired (404) or overloaded (5xx) models come back as ServerException; another model may still answer.
// A rejected App Check token is also a ServerException, but no other model would accept it either.
private fun cannotServe(e: Throwable) =
    !appCheckRejected(e) && (e is ServerException || e is QuotaExceededException || e is RequestTimeoutException)

// The service turned the app away because it could not prove it is the real app: a debug build whose token
// is not registered yet, or a modified copy.
private fun appCheckRejected(e: Throwable) = e is FirebaseAIException && e.message.orEmpty().contains("App Check", ignoreCase = true)

// Gemini through Firebase AI Logic: the Gemini key stays in the Firebase project and App Check vouches for the app,
// so the APK carries no key at all.
fun firebaseGemini(): suspend (model: String, prompt: String) -> String {
    val ai = Firebase.ai(backend = GenerativeBackend.googleAI())
    val config = generationConfig {
        responseMimeType = "application/json"
        responseJsonSchema = jsonSchema(ITINERARY_SCHEMA)
    }
    val models = mutableMapOf<String, GenerativeModel>()
    return { model, prompt ->
        val generative = models.getOrPut(model) {
            ai.generativeModel(
                modelName = model,
                generationConfig = config,
                systemInstruction = content { text(SYSTEM_PROMPT) },
                requestOptions = RequestOptions(timeoutInMillis = 60_000),
            )
        }
        val started = System.currentTimeMillis()
        try {
            val text = generative.generateContent(prompt).text ?: throw JSONException("The reply had no text")
            Log.i(TAG, "$model answered in ${System.currentTimeMillis() - started} ms")
            text
        } catch (e: Throwable) {
            Log.w(TAG, "$model failed after ${System.currentTimeMillis() - started} ms: ${e.javaClass.simpleName} ${e.message}")
            throw e
        }
    }
}

private const val TAG = "GeminiPlanner"

// ITINERARY_SCHEMA in the SDK's own schema type; every object property stays required.
internal fun jsonSchema(schema: Map<*, *>): JsonSchema<*> = when (schema["type"]) {
    "object" -> JsonSchema.obj((schema["properties"] as Map<*, *>).entries.associate { (name, value) -> name as String to jsonSchema(value as Map<*, *>) })
    "array" -> JsonSchema.array(jsonSchema(schema["items"] as Map<*, *>))
    "integer" -> JsonSchema.integer()
    else -> JsonSchema.string()
}

@StringRes
fun errorMessage(error: Throwable): Int = when {
    appCheckRejected(error) -> R.string.error_api_key
    else -> errorMessageByType(error)
}

@StringRes
private fun errorMessageByType(error: Throwable): Int = when (error) {
    is QuotaExceededException -> R.string.error_rate_limit
    is ServerException, is RequestTimeoutException -> R.string.error_busy
    is InvalidAPIKeyException, is ServiceDisabledException, is APINotConfiguredException, is PermissionMissingException -> R.string.error_api_key
    is PromptBlockedException -> R.string.error_refusal
    is ResponseStoppedException ->
        if (error.response.candidates.firstOrNull()?.finishReason == FinishReason.MAX_TOKENS) R.string.error_too_long else R.string.error_refusal
    is SerializationException, is JSONException -> R.string.error_parse
    // With no network the SDK wraps the socket error in an UnknownException.
    is IOException -> R.string.error_network
    // The socket error can sit a level or two down: coroutines may wrap a rethrown error in a copy of itself.
    else -> if (generateSequence(error.cause) { it.cause }.any { it is IOException }) R.string.error_network else R.string.error_generic
}

// The service's own words, shown under the friendly text so a setup or quota problem is easy to diagnose.
fun errorDetail(error: Throwable): String? = (error as? FirebaseAIException)?.message
