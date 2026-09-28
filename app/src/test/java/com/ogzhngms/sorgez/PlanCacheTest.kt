package com.ogzhngms.sorgez

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanCacheTest {
    private val sample = File("src/main/res/raw/sample_itinerary.json").readText()
    private val cache = PlanCache(Files.createTempDirectory("plans").toFile())

    @Test
    fun theSamePromptGetsTheSavedPlanBack() {
        cache.put("Destination: Rome", sample)
        assertEquals(sample, cache.get("Destination: Rome"))
        assertNull(cache.get("Destination: Ankara"))
    }

    @Test
    fun theSameChoicesShareAKeyHoweverThePlaceIsTyped() {
        val rome = TripAnswers(destination = "Roma", interests = setOf(Interest.FOOD, Interest.CULTURE))
        val key = planKey(rome, "Turkish", Currency.TRY)
        assertEquals(key, planKey(rome.copy(destination = " roma "), "Turkish", Currency.TRY))
        assertEquals(key, planKey(rome.copy(interests = setOf(Interest.CULTURE, Interest.FOOD)), "Turkish", Currency.TRY))
        assertNotEquals(key, planKey(rome.copy(days = 4), "Turkish", Currency.TRY))
        assertNotEquals(key, planKey(rome, "English", Currency.TRY))
        assertNotEquals(key, planKey(rome, "Turkish", Currency.EUR))
    }

    @Test
    fun aPlanThatDoesNotParseIsNotKept() {
        cache.put("Destination: Rome", "{ half a plan")
        assertNull(cache.get("Destination: Rome"))
    }
}
