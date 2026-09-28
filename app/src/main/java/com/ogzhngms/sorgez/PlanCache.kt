package com.ogzhngms.sorgez

import java.io.File
import java.security.MessageDigest

// Debug builds keep every plan they get, keyed by the exact prompt, so testing the same trip again
// does not spend the small free Gemini quota. Only plans that parse are kept. Release builds never use it.
class PlanCache(private val folder: File) {
    fun get(prompt: String): String? = file(prompt).takeIf { it.exists() }?.readText()

    fun put(prompt: String, plan: String) {
        runCatching { parseItinerary(plan) }.onSuccess {
            folder.mkdirs()
            file(prompt).writeText(plan)
        }
    }

    private fun file(prompt: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(prompt.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(folder, "$hash.json")
    }
}
