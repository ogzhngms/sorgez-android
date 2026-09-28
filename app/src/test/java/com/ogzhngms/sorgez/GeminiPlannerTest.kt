package com.ogzhngms.sorgez

import com.google.firebase.ai.type.Candidate
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FinishReason
import com.google.firebase.ai.type.GenerateContentResponse
import com.google.firebase.ai.type.InvalidAPIKeyException
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.UnknownException
import java.net.UnknownHostException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

// Runs the model fallback against a fake of Firebase AI Logic that answers or fails per model.
class GeminiPlannerTest {
    private val asked = mutableListOf<String>()

    // Each model name maps to what the fake does: return text or throw.
    private fun planner(vararg outcomes: Pair<String, Any>) = GeminiPlanner { model, prompt ->
        asked += model
        assertEquals("Destination: Rome", prompt)
        when (val outcome = outcomes.toMap()[model] ?: "plan from $model") {
            is Throwable -> throw outcome
            else -> outcome as String
        }
    }

    private fun failure(planner: GeminiPlanner): Int = try {
        runBlocking { planner.plan("Destination: Rome") }
        fail("plan() should have thrown")
        0
    } catch (e: Exception) {
        errorMessage(e)
    }

    @Test
    fun firstModelAnswers() {
        assertEquals("plan from gemini-3.8-flash", runBlocking { planner().plan("Destination: Rome") })
        assertEquals(listOf("gemini-3.8-flash"), asked)
    }

    // Overloaded, out of free quota, retired and too slow models are skipped without the user noticing.
    @Test
    fun modelsThatCannotServeAreSkipped() {
        val planner = planner(
            "gemini-3.8-flash" to make<ServerException>("Unexpected Response: 503", null),
            "gemini-3.7-flash" to make<QuotaExceededException>("quota", null),
            "gemini-3.6-flash" to make<ServerException>("URL not found", null),
            "gemini-3.5-flash" to make<RequestTimeoutException>("timed out", null, emptyList<Any>()),
        )
        assertEquals("plan from gemini-3.5-flash-lite", runBlocking { planner.plan("Destination: Rome") })
        assertEquals(GEMINI_MODELS.take(5), asked)
    }

    // A model that hangs does not hold the plan up: after the hedge delay the next one starts and the first answer wins.
    @Test
    fun aSlowModelGetsCompanyAndTheFirstAnswerWins() = runTest {
        val planner = GeminiPlanner(hedgeAfterMillis = 40_000) { model, _ ->
            asked += model
            if (model == GEMINI_MODELS[0]) awaitCancellation()
            delay(5_000)
            "plan from $model"
        }
        assertEquals("plan from ${GEMINI_MODELS[1]}", planner.plan("Destination: Rome"))
        assertEquals(GEMINI_MODELS.take(2), asked)
        assertEquals(45_000L, currentTime)
    }

    @Test
    fun noNetworkStopsAtOnce() {
        val offline = make<UnknownException>("Something unexpected happened.", UnknownHostException("firebasevertexai.googleapis.com"))
        assertEquals(R.string.error_network, failure(planner("gemini-3.8-flash" to offline)))
        assertEquals(1, asked.size)
    }

    @Test
    fun everyModelOverloadedSaysTryLater() {
        val busy = make<ServerException>("Unexpected Response: 503", null)
        assertEquals(R.string.error_busy, failure(planner(*GEMINI_MODELS.map { it to busy }.toTypedArray())))
        assertEquals(GEMINI_MODELS, asked)
    }

    // An unregistered debug build or a modified copy: no other model would accept it, so stop and say so.
    @Test
    fun rejectedAppCheckStopsAtOnce() {
        val rejected = make<ServerException>("Firebase App Check token is invalid.", null)
        assertEquals(R.string.error_api_key, failure(planner("gemini-3.8-flash" to rejected)))
        assertEquals(1, asked.size)
    }

    @Test
    fun everyModelOutOfQuotaSaysWait() {
        val quota = make<QuotaExceededException>("quota", null)
        assertEquals(R.string.error_rate_limit, failure(planner(*GEMINI_MODELS.map { it to quota }.toTypedArray())))
    }

    @Test
    fun blockedPromptIsNotRetried() {
        assertEquals(R.string.error_refusal, failure(planner("gemini-3.8-flash" to make<PromptBlockedException>("blocked", null))))
        assertEquals(1, asked.size)
    }

    @Test
    fun truncatedPlanIsNotParsed() {
        val cut = make<GenerateContentResponse>(
            listOf(make<Candidate>(make<Content>(emptyList<Any>()), emptyList<Any>(), null, FinishReason.MAX_TOKENS, null, null, null)),
            null,
            null,
        )
        assertEquals(R.string.error_too_long, failure(planner("gemini-3.8-flash" to make<ResponseStoppedException>(cut, null))))
    }

    // A setup problem fails the same way on every model, so there is no point asking the next one.
    @Test
    fun setupProblemIsNotRetried() {
        assertEquals(R.string.error_api_key, failure(planner("gemini-3.8-flash" to make<InvalidAPIKeyException>("API key not valid", null))))
        assertEquals(1, asked.size)
    }

    @Test
    fun itinerarySchemaConvertsForTheSdk() {
        jsonSchema(ITINERARY_SCHEMA)
    }

    // The SDK's exception and response constructors are internal to Kotlin, so tests build them reflectively.
    private inline fun <reified T> make(vararg args: Any?): T = T::class.java.constructors.first { constructor ->
        constructor.parameterCount == args.size &&
            constructor.parameterTypes.zip(args).all { (type, arg) -> arg == null || type.isInstance(arg) }
    }.newInstance(*args) as T
}
