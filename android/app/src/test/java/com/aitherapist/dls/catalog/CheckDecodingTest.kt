package com.aitherapist.dls.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckDecodingTest {
    @Test
    fun `distanceBelow decodes with resolved fields`() {
        val check =
            CatalogJson.decodeCheck(
                """{"type":"distanceBelow","a":{"lm":"WRIST"},"b":{"mid":["MOUTH_LEFT","MOUTH_RIGHT"]},"ratio":0.6,"holdSec":0.3,"side":"either"}"""
            )
        assertEquals(
            Check.DistanceBelow(
                a = LandmarkRef.Lm("WRIST"),
                b = LandmarkRef.Mid(listOf("MOUTH_LEFT", "MOUTH_RIGHT")),
                ratio = 0.6,
                holdSec = 0.3,
                side = Side.EITHER
            ),
            check
        )
    }

    @Test(expected = Exception::class)
    fun `unknown type fails`() {
        CatalogJson.decodeCheck("""{"type":"spin"}""")
    }

    @Test(expected = Exception::class)
    fun `unknown field fails`() {
        CatalogJson.decodeCheck("""{"type":"question","text":"q","extra":1}""")
    }

    @Test(expected = Exception::class)
    fun `landmark ref with both keys fails`() {
        CatalogJson.decodeCheck(
            """{"type":"inZone","landmark":{"lm":"NOSE","mid":["A","B"]},"zone":"mat","holdSec":0}"""
        )
    }

    @Test(expected = Exception::class)
    fun `mid with one name fails`() {
        CatalogJson.decodeCheck("""{"type":"inZone","landmark":{"mid":["A"]},"zone":"mat","holdSec":0}""")
    }

    @Test
    fun `every design type decodes to its subclass`() {
        val ref = """{"lm":"LEFT_ELBOW"}"""
        val cases =
            mapOf(
                """{"type":"angleAbove","a":$ref,"vertex":$ref,"b":$ref,"deg":90,"holdSec":1,"side":"left"}""" to
                    Check.AngleAbove::class,
                """{"type":"angleBelow","a":$ref,"vertex":$ref,"b":$ref,"deg":90,"holdSec":1}""" to Check.AngleBelow::class,
                """{"type":"pointAbove","point":$ref,"ref":$ref,"margin":0.1,"holdSec":1,"side":"right"}""" to
                    Check.PointAbove::class,
                """{"type":"distanceBelow","a":$ref,"b":$ref,"ratio":0.5,"holdSec":0.3}""" to Check.DistanceBelow::class,
                """{"type":"distanceAbove","a":$ref,"b":$ref,"ratio":0.5,"holdSec":0.3}""" to Check.DistanceAbove::class,
                """{"type":"inZone","landmark":$ref,"zone":"sink","holdSec":1}""" to Check.InZone::class,
                """{"type":"handsRubbing","maxWristRatio":0.5,"minMotionRatio":0.1,"holdSec":2}""" to Check.HandsRubbing::class,
                """{"type":"question","text":"Is the cup lifted?"}""" to Check.Question::class
            )
        cases.forEach { (json, type) -> assertTrue(json, type.isInstance(CatalogJson.decodeCheck(json))) }
    }

    @Test
    fun `step and criterion decode`() {
        val step =
            CatalogJson.decodeStep(
                """{"id":"x.y.01","method":"pose","check":{"type":"question","text":"q"},"detector":{"landmarkers":["pose","hand"]}}"""
            )
        assertEquals(Method.POSE, step.method)
        assertEquals(listOf(Landmarker.POSE, Landmarker.HAND), step.detector!!.landmarkers)

        val reps = CatalogJson.decodeCriterion("""{"kind":"reps","check":{"type":"question","text":"q"},"target":5,"low":30,"high":60}""")
        assertTrue(reps is Criterion.Reps && reps.target == 5 && reps.low == 30.0)
        val hold = CatalogJson.decodeCriterion("""{"kind":"hold","check":{"type":"question","text":"q"},"target":3,"perSide":true}""")
        assertTrue(hold is Criterion.Hold && hold.perSide)
    }
}
