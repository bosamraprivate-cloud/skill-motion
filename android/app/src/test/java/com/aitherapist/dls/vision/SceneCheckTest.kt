package com.aitherapist.dls.vision

import com.aitherapist.dls.R
import com.aitherapist.dls.catalog.Check
import com.aitherapist.dls.catalog.LandmarkRef
import com.aitherapist.dls.catalog.Method
import com.aitherapist.dls.catalog.Side
import com.aitherapist.dls.catalog.StepDetector
import com.aitherapist.dls.ui.child.sceneIssueRes
import com.aitherapist.dls.ui.child.sceneKindRes
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Story 6.4: required kinds, /scene parsing, box to zone, acceptance, face blur and issue messages. */
class SceneCheckTest {
    private fun inZone(zone: String) = StepDetector(Method.POSE, "table", Check.InZone(LandmarkRef.Lm("WRIST"), zone, 0.3, Side.EITHER))

    @Test
    fun `required kinds come from the catalogue zone checks plus person`() {
        val detectors = listOf(inZone("mat"), StepDetector(Method.CLOUD, "table", Check.Question("q")), inZone("mat"), inZone("sink"))
        assertEquals(listOf("mat", "sink"), ScenePlan.zoneKinds(detectors))
        assertEquals(listOf("mat", "sink", "person"), ScenePlan.requestKinds(ScenePlan.zoneKinds(detectors)))
        assertEquals(
            emptyList<String>(),
            ScenePlan.requestKinds(ScenePlan.zoneKinds(listOf(StepDetector(Method.CLOUD, "table", Check.Question("q")))))
        )
    }

    private val good =
        """{"ok":true,"objects":[{"kind":"mat","box":{"x":0.25,"y":0.5,"w":0.5,"h":0.25},"confidence":0.9}],
           "issues":[{"code":"partly_visible","kind":"mat"},{"code":"bogus"}],"modelId":"deepseek-flash","promptVersion":"scene-v2"}"""

    @Test
    fun `parses a valid answer and drops unknown issue codes`() {
        val r = SceneChecker.parse(good) as SceneResult.Checked
        assertTrue(r.ok)
        assertEquals(SceneObject("mat", SceneBox(0.25f, 0.5f, 0.5f, 0.25f), 0.9f), r.objects.single())
        assertEquals(listOf(SceneIssue(SceneIssueCode.PARTLY_VISIBLE, "mat")), r.issues)
        assertEquals("scene-v2", r.promptVersion)
    }

    @Test
    fun `malformed answers are unavailable`() {
        listOf(
            "not json",
            """{"objects":[],"issues":[],"modelId":"m","promptVersion":"p"}""",
            """{"ok":true,"objects":[],"issues":[],"promptVersion":"p"}""",
            """{"ok":true,"objects":[{"kind":"mat","box":{"x":0.1,"y":0.5,"w":2,"h":0.4}}],"modelId":"m","promptVersion":"p"}""",
            """{"ok":true,"objects":[{"kind":"mat","box":{"x":0.1,"y":0.5}}],"modelId":"m","promptVersion":"p"}"""
        ).forEach { assertEquals(it, SceneResult.Unavailable(VerdictReason.BAD_ANSWER), SceneChecker.parse(it)) }
    }

    @Test
    fun `client sends the scene contract and maps failures to unavailable`() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse.Builder().code(200).body(good).build())
            server.enqueue(MockResponse.Builder().code(401).build())
            val checker = SceneChecker(server.url("/").toString(), "s3cret", newRequestId = { "id-1" })
            assertTrue(checker.check(byteArrayOf(1), listOf("mat", "person")) is SceneResult.Checked)
            val req = server.takeRequest()
            assertEquals("/scene", req.url.encodedPath)
            assertEquals("Bearer s3cret", req.headers["Authorization"])
            val body = requireNotNull(req.body).utf8()
            assertTrue(body.contains("name=\"kinds\"") && body.contains("mat,person"))
            assertTrue(body.contains("name=\"locale\"") && body.contains("ar-EG"))
            assertTrue(body.contains("name=\"image\""))
            assertEquals(SceneResult.Unavailable(VerdictReason.REFUSED), checker.check(byteArrayOf(1), listOf("mat")))
        } finally {
            server.close()
        }
        assertEquals(
            SceneResult.Unavailable(VerdictReason.NETWORK),
            SceneChecker("https://x.dev", "", newRequestId = {
                "i"
            }).check(byteArrayOf(1), listOf("mat"))
        )
    }

    @Test
    fun `boxes map to clamped zones`() {
        assertEquals(Zone(0.25f, 0.5f, 0.75f, 0.75f), SceneBox(0.25f, 0.5f, 0.5f, 0.25f).toZone())
        assertEquals(Zone(0.8f, 0.8f, 1f, 1f), SceneBox(0.8f, 0.8f, 0.5f, 0.5f).toZone())
        assertNull(SceneBox(1f, 0.5f, 0f, 0.1f).toZone())
        val r = SceneResult.Checked(true, listOf(SceneObject("mat", SceneBox(0.25f, 0.5f, 0.5f, 0.25f), 0.9f)), emptyList(), "m", "p")
        assertEquals(mapOf("mat" to Zone(0.25f, 0.5f, 0.75f, 0.75f)), ScenePlan.zonesOf(r, listOf("mat")))
        val withScene =
            r.copy(
                objects = r.objects + SceneObject("cup", SceneBox(0.1f, 0.1f, 0.1f, 0.1f), 0.9f) +
                    SceneObject("person", SceneBox(0f, 0f, 0.5f, 1f), 0.9f)
            )
        assertEquals("scene objects are not saved for scoring", setOf("mat", "person"), ScenePlan.zonesOf(withScene, listOf("mat")).keys)
    }

    @Test
    fun `required kinds join zones and scene objects, deduplicated, person appended for the request`() {
        assertEquals(listOf("mat", "table", "chair", "cup"), ScenePlan.requiredKinds(listOf("mat"), listOf("table", "chair", "mat", "cup")))
        assertEquals(listOf("table", "chair", "person"), ScenePlan.requestKinds(emptyList(), listOf("table", "chair", "person")))
        assertEquals(listOf("mat", "person"), ScenePlan.requestKinds(listOf("mat"), emptyList()))
        assertEquals(emptyList<String>(), ScenePlan.requestKinds(emptyList(), listOf("person")))
        assertEquals(emptyList<String>(), ScenePlan.requiredKinds(emptyList(), emptyList()))
    }

    @Test
    fun `acceptance needs every scene object box but never the person`() {
        val box = SceneBox(0.1f, 0.1f, 0.2f, 0.2f)
        val required = ScenePlan.requiredKinds(emptyList(), listOf("table", "cup"))
        val both = listOf(SceneObject("table", box, 0.9f), SceneObject("cup", box, 0.9f))
        assertTrue(ScenePlan.accepted(SceneResult.Checked(true, both, emptyList(), "m", "p"), required + "person"))
        assertFalse(ScenePlan.accepted(SceneResult.Checked(true, both.take(1), emptyList(), "m", "p"), required))
    }

    @Test
    fun `acceptance ignores person issues but needs every zone box`() {
        val mat = SceneObject("mat", SceneBox(0.25f, 0.5f, 0.5f, 0.25f), 0.9f)
        val personIssues = listOf(SceneIssue(SceneIssueCode.MISSING_OBJECT, "person"), SceneIssue(SceneIssueCode.PERSON_IN_VIEW))
        assertTrue(ScenePlan.accepted(SceneResult.Checked(false, listOf(mat), personIssues, "m", "p"), listOf("mat")))
        assertFalse(ScenePlan.accepted(SceneResult.Checked(true, emptyList(), emptyList(), "m", "p"), listOf("mat")))
        assertFalse(
            ScenePlan.accepted(
                SceneResult.Checked(false, listOf(mat), listOf(SceneIssue(SceneIssueCode.TOO_DARK)), "m", "p"),
                listOf("mat")
            )
        )
    }

    @Test
    fun `face blur covers the eye band ear to ear but not the mouth, or the top of the person box`() {
        val pose = List(33) { LandmarkPoint(0.5f, 0.9f, 0f) }.toMutableList()
        pose[0] = LandmarkPoint(0.5f, 0.25f, 0.9f) // nose
        for (i in 1..3) pose[i] = LandmarkPoint(0.47f, 0.2f, 0.9f) // left eye
        for (i in 4..6) pose[i] = LandmarkPoint(0.53f, 0.2f, 0.9f) // right eye
        pose[7] = LandmarkPoint(0.4f, 0.21f, 0.9f) // ears
        pose[8] = LandmarkPoint(0.6f, 0.21f, 0.9f)
        pose[9] = LandmarkPoint(0.48f, 0.3f, 0.9f) // mouth
        pose[10] = LandmarkPoint(0.52f, 0.3f, 0.9f)
        val face = requireNotNull(FaceBlur.faceBox(pose, null))
        // width 0.2 padded 0.04 per side; unit = eye-to-mouth 0.1: top 0.2 - 0.11, bottom 0.2 + 0.045
        assertEquals(0.36f, face.left, 1e-4f)
        assertEquals(0.64f, face.right, 1e-4f)
        assertEquals(0.09f, face.top, 1e-4f)
        assertEquals(0.245f, face.bottom, 1e-4f)
        assertTrue("mouth stays visible", face.bottom < 0.3f)
        assertTrue("forehead covered", face.top < 0.2f - 0.05f)

        val noMouth = pose.toMutableList().also {
            it[9] = LandmarkPoint(0f, 0f, 0f)
            it[10] = LandmarkPoint(0f, 0f, 0f)
        }
        val viaNose = requireNotNull(FaceBlur.faceBox(noMouth, null))
        assertEquals(0.245f, viaNose.bottom, 1e-4f) // unit = 2 * eye-to-nose = 0.1

        val tiny = List(33) { LandmarkPoint(0f, 0f, 0f) }.toMutableList().also { it[2] = LandmarkPoint(0.5f, 0.5f, 0.9f) }
        val minimum = requireNotNull(FaceBlur.faceBox(tiny, null))
        assertTrue("at least the minimum side", minimum.right - minimum.left >= FaceBlur.MIN_SIDE - 1e-6f)

        val person = Zone(0.25f, 0f, 0.75f, 1f)
        assertEquals(Zone(0.25f, 0f, 0.75f, 0.15f), FaceBlur.faceBox(List(33) { LandmarkPoint(0f, 0f, 0f) }, person))
        assertNull(FaceBlur.faceBox(null, null))
    }

    @Test
    fun `blur box is mapped into the crop`() {
        val crop = CropBox(100, 100, 300, 300)
        assertEquals(CropBox(0, 0, 100, 50), FaceBlur.inCrop(Zone(0.0f, 0.0f, 0.5f, 0.375f), 400, 400, crop))
        assertNull(FaceBlur.inCrop(Zone(0.8f, 0.8f, 0.9f, 0.9f), 400, 400, crop))
    }

    @Test
    fun `every issue code and kind has a message`() {
        assertEquals(SceneIssueCode.entries.size, SceneIssueCode.entries.map { sceneIssueRes(it) }.toSet().size)
        assertEquals(R.string.scene_issue_missing_object, sceneIssueRes(SceneIssueCode.MISSING_OBJECT))
        SceneIssueCode.entries.forEach { assertEquals(it, SceneIssueCode.of(it.wire)) }
        assertEquals(R.string.scene_kind_mat, sceneKindRes("mat"))
        assertEquals(R.string.scene_kind_sink, sceneKindRes("sink"))
        assertEquals(R.string.scene_kind_person, sceneKindRes("person"))
        assertEquals(R.string.scene_kind_table, sceneKindRes("table"))
        assertEquals(R.string.scene_kind_toothpaste, sceneKindRes("toothpaste"))
        val sceneKinds = listOf(
            "table", "chair", "cup", "plate", "bowl", "spoon", "fork", "sandwich", "bottle", "straw", "sink", "soap", "towel",
            "toothbrush", "toothpaste", "jacket", "tshirt", "shirt", "trousers", "socks", "shoes"
        )
        assertEquals("every scene object kind has its own label", sceneKinds.size, sceneKinds.map { sceneKindRes(it) }.toSet().size)
        assertFalse(sceneKinds.any { sceneKindRes(it) == R.string.scene_kind_unknown })
        assertEquals(R.string.scene_kind_unknown, sceneKindRes("rocket"))
        assertEquals(R.string.scene_kind_unknown, sceneKindRes(null))
    }
}
