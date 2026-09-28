package com.ogzhngms.sorgez

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
// it is Firebase AI Logic in the app and a fake in tests.
class GeminiPlanner(private val ask: suspend (model: String, prompt: String) -> String) {
    suspend fun plan(prompt: String): String {
        var busy: Throwable? = null
        for (model in GEMINI_MODELS) {
            try {
                return ask(model, prompt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (!cannotServe(e)) throw e
                busy = e
            }
        }
        throw busy!!
    }
}

// Retired (404) or overloaded (5xx) models come back as ServerException; another model may still answer.
private fun cannotServe(e: Throwable) = e is ServerException || e is QuotaExceededException || e is RequestTimeoutException

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
        generative.generateContent(prompt).text ?: throw JSONException("The reply had no text")
    }
}

// ITINERARY_SCHEMA in the SDK's own schema type; every object property stays required.
internal fun jsonSchema(schema: Map<*, *>): JsonSchema<*> = when (schema["type"]) {
    "object" -> JsonSchema.obj((schema["properties"] as Map<*, *>).entries.associate { (name, value) -> name as String to jsonSchema(value as Map<*, *>) })
    "array" -> JsonSchema.array(jsonSchema(schema["items"] as Map<*, *>))
    "integer" -> JsonSchema.integer()
    else -> JsonSchema.string()
}

@StringRes
fun errorMessage(error: Throwable): Int = when (error) {
    is QuotaExceededException -> R.string.error_rate_limit
    is ServerException, is RequestTimeoutException -> R.string.error_busy
    is InvalidAPIKeyException, is ServiceDisabledException, is APINotConfiguredException, is PermissionMissingException -> R.string.error_api_key
    is PromptBlockedException -> R.string.error_refusal
    is ResponseStoppedException ->
        if (error.response.candidates.firstOrNull()?.finishReason == FinishReason.MAX_TOKENS) R.string.error_too_long else R.string.error_refusal
    is SerializationException, is JSONException -> R.string.error_parse
    // With no network the SDK wraps the socket error in an UnknownException.
    is IOException -> R.string.error_network
    else -> if (error.cause is IOException) R.string.error_network else R.string.error_generic
}

// The service's own words, shown under the friendly text so a setup or quota problem is easy to diagnose.
fun errorDetail(error: Throwable): String? = (error as? FirebaseAIException)?.message
