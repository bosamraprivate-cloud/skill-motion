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
        assertEquals(mapOf("mat" to Zone(0.25f, 0.5f, 0.75f, 0.75f)), ScenePlan.zonesOf(r))
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
    fun `face blur box pads the face points or falls back to the head of the person box`() {
        val pose = List(33) { LandmarkPoint(0.5f, 0.9f, 0f) }.toMutableList()
        pose[0] = LandmarkPoint(0.5f, 0.2f, 0.9f)
        pose[7] = LandmarkPoint(0.45f, 0.2f, 0.9f)
        pose[8] = LandmarkPoint(0.55f, 0.2f, 0.9f)
        val face = requireNotNull(FaceBlur.faceBox(pose, null))
        assertTrue(face.left < 0.45f && face.right > 0.55f && face.top < 0.2f && face.bottom > 0.2f)
        assertTrue("at least the minimum side", face.right - face.left >= FaceBlur.MIN_SIDE)
        val person = Zone(0.25f, 0f, 0.75f, 1f)
        assertEquals(Zone(0.25f, 0f, 0.75f, 0.25f), FaceBlur.faceBox(List(33) { LandmarkPoint(0f, 0f, 0f) }, person))
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
        assertEquals(R.string.scene_kind_unknown, sceneKindRes("rocket"))
        assertEquals(R.string.scene_kind_unknown, sceneKindRes(null))
    }
}
