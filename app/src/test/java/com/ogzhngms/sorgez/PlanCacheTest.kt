package com.ogzhngms.sorgez

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
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
    fun aPlanThatDoesNotParseIsNotKept() {
        cache.put("Destination: Rome", "{ half a plan")
        assertNull(cache.get("Destination: Rome"))
    }
}
