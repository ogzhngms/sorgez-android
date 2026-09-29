package com.ogzhngms.sorgez

import java.io.File
import java.security.MessageDigest

// Debug builds keep every plan they get, so testing the same trip again does not spend the small free
// Gemini quota. Only plans that parse are kept. Release builds never use it.
class PlanCache(private val folder: File) {
    fun get(key: String): String? = file(key).takeIf { it.exists() }?.readText()

    fun put(key: String, plan: String) {
        runCatching { parseItinerary(plan) }.onSuccess {
            folder.mkdirs()
            file(key).writeText(plan)
        }
    }

    private fun file(key: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(folder, "$hash.json")
    }
}

// The answers are fixed choices apart from the destination and notes, which are compared the way place names
// are, so "Roma", "roma " and "ROMA" share one plan. The language and currency change the plan's text.
fun planKey(answers: TripAnswers, language: String, currency: Currency): String = listOf(
    normalize(answers.destination),
    answers.days,
    answers.month,
    answers.companions,
    answers.budget,
    answers.interests.sorted().joinToString(","),
    normalize(answers.notes),
    language,
    currency,
).joinToString("|")
