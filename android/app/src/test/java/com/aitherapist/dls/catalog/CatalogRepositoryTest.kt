package com.aitherapist.dls.catalog

import java.io.File
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Story 2.8: the committed generated catalogue decodes strictly, in order, with nullable media/audio. */
class CatalogRepositoryTest {
    private val assets = File("src/main/assets")
    private val realRead: (String) -> String = { File(assets, it).readText() }

    private fun real(): Catalog = CatalogDecoder.decode(realRead)

    @Test
    fun `real assets give 20 tasks 178 steps 148 activities and the version`() {
        val c = real()
        assertEquals(20, c.tasks.size)
        assertEquals(178, c.steps.size)
        assertEquals(148, c.activities.size)
        assertTrue(Regex("[0-9a-f]{12}").matches(c.catalogVersion))
        assertEquals(c.version.steps, c.steps.size)
        assertEquals(c.version.activities, c.activities.size)
    }

    @Test
    fun `every real task has a posture and each garment is taken off before it is put on`() {
        val c = real()
        assertTrue(c.tasks.none { it.posture == Posture.UNKNOWN })
        val ids = c.tasks.map { it.id }
        ids.filter { it.endsWith(".off") }.forEach { off ->
            val on = off.removeSuffix(".off") + ".on"
            assertEquals("$off right before $on", ids.indexOf(off) + 1, ids.indexOf(on))
        }
        assertEquals(Posture.UNKNOWN, Posture.parse(null))
        assertEquals(Posture.STANDING_OR_SEATED, Posture.parse("standing_or_seated"))
    }

    @Test
    fun `real tasks carry their scene objects`() {
        val c = real()
        assertTrue(c.tasks.any { it.sceneObjects.isNotEmpty() })
        assertTrue(c.tasks.all { t -> t.sceneObjects.all { Regex("^[a-z][a-z0-9_]*$").matches(it) } })
        assertEquals(TaskEntry("x", "a", null, "s").sceneObjects, null)
    }

    @Test
    fun `every real step carries a step time that reaches the runtime step`() {
        val c = real()
        c.steps.forEach { s ->
            val sec = requireNotNull(s.timeSec) { s.id }
            assertTrue(s.id, sec in 5..60)
            assertEquals(sec, s.toStep().timeSec)
        }
    }

    @Test
    fun `tasks and steps keep file order`() {
        val text = File(assets, CatalogDecoder.ASSESSMENT).readText()
        val root = kotlinx.serialization.json.Json.parseToJsonElement(text).jsonObject
        val fileOrder = root.getValue("steps").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }
        val c = real()
        assertEquals(fileOrder, c.steps.map { it.id })
        assertEquals(fileOrder.map { it.substringBeforeLast('.') }.distinct(), c.tasks.map { it.id })
        c.tasks.forEach { t -> assertEquals(t.steps.map { it.number }.sorted(), t.steps.map { it.number }) }
    }

    @Test
    fun `dev catalogue nulls are exposed as null`() {
        val c = real()
        if (!c.complete) {
            assertTrue(c.steps.any { it.audio == null } || c.steps.any { s -> s.media.values.any { it == null } })
        }
        c.steps.forEach { assertEquals(it.detector.method == Method.NONE, it.detector.landmarkers.isEmpty()) }
        assertEquals(null, c.steps.first().media("no-such-variant"))
    }

    private fun versionJson(steps: Int = 1, activities: Int = 1, complete: Boolean = false) =
        """{"activities":$activities,"catalogVersion":"abcdefabcdef","complete":$complete,"steps":$steps}"""

    private fun stepObj(
        id: String = "x.y.01",
        task: String = "x.y",
        area: String = "a",
        detector: String = """{"method":"none","framing":null,"check":null}""",
        extra: String = ""
    ) = """{"area":"$area","audio":null,"detector":$detector,"id":"$id","media":{"v":null},"number":1,""" +
        """"source":"s","subSkills":"k","tags":[],"task":"$task","textAr":"ع","textEn":"e"$extra}"""

    private fun stepJson(detector: String = """{"method":"none","framing":null,"check":null}""", extra: String = "") =
        """{"areas":[{"id":"a","nameAr":"\u0645"}],"tasks":[${taskObj()}],"steps":[${stepObj(detector = detector, extra = extra)}]}"""

    private fun taskObj(id: String = "x.y", area: String = "a", name: String? = "ع") =
        """{"id":"$id","area":"$area","nameAr":${name?.let { "\"$it\"" } ?: "null"},"source":"s"}"""

    private fun activityObj(id: String = "p.d.01") =
        """{"id":"$id","plan":"p","domain":"d","number":1,"title":"t","textAr":"ع","generalGoal":"g","goal":"g",""" +
            """"howToPerform":"h","tools":"t","criterion":"c","source":"s"}"""

    private val oneActivity = """{"activities":[${activityObj()}]}"""

    private fun fixture(assessment: String, activities: String = oneActivity, version: String = versionJson()): (String) -> String =
        { path ->
            when (path) {
                CatalogDecoder.ASSESSMENT -> assessment
                CatalogDecoder.ACTIVITIES -> activities
                else -> version
            }
        }

    private fun failure(read: (String) -> String): CatalogLoadException {
        val e = runCatching { CatalogDecoder.decode(read) }.exceptionOrNull()
        assertTrue("expected CatalogLoadException, got $e", e is CatalogLoadException)
        return e as CatalogLoadException
    }

    private fun assertFails(file: String, read: (String) -> String) {
        val e = failure(read)
        assertTrue(e.message!!, e.message!!.startsWith(file))
    }

    @Test
    fun `integrity failures name the file`() {
        val a = CatalogDecoder.ASSESSMENT
        val two = { s1: String, s2: String -> """{"areas":[{"id":"a","nameAr":"\u0645"}],"tasks":[${taskObj()}],"steps":[$s1,$s2]}""" }
        // non-contiguous task
        assertFails(
            a,
            fixture(
                """{"areas":[{"id":"a","nameAr":"\u0645"}],"tasks":[${taskObj()},${taskObj(
                    "x.z"
                )}],"steps":[${stepObj("x.y.01")},${stepObj("x.z.01", "x.z")},${stepObj("x.y.02")}]}""",
                version = versionJson(steps = 3)
            )
        )
        assertFails(a, fixture(two(stepObj("x.y.01"), stepObj("x.y.01")), version = versionJson(steps = 2)))
        assertFails(a, fixture(two(stepObj("x.y.01"), stepObj("x.y.02", area = "b")), version = versionJson(steps = 2)))
        assertFails(a, fixture("""{"areas":[{"id":"a","nameAr":"\u0645"}],"tasks":[],"steps":[]}""", version = versionJson(steps = 0)))
        // task headers must match the step tasks, in order and area
        assertFails(a, fixture("""{"areas":[{"id":"a","nameAr":"\u0645"}],"tasks":[],"steps":[${stepObj()}]}"""))
        assertFails(a, fixture("""{"areas":[{"id":"a","nameAr":"\u0645"}],"tasks":[${taskObj(area = "b")}],"steps":[${stepObj()}]}"""))
        assertFails(
            a,
            fixture("""{"areas":[{"id":"a","nameAr":"\u0645"}],"tasks":[${taskObj()},${taskObj("x.z")}],"steps":[${stepObj()}]}""")
        )
        assertFails(a, fixture("""{"steps":[${stepObj()}]}"""))
        // area headers must match the task areas
        assertFails(a, fixture("""{"areas":[],"tasks":[${taskObj()}],"steps":[${stepObj()}]}"""))
        assertFails(a, fixture(stepJson(), version = versionJson(complete = true)))
        assertFails(CatalogDecoder.ACTIVITIES, fixture(stepJson(), """{"activities":[]}""", versionJson(activities = 0)))
        assertFails(
            CatalogDecoder.ACTIVITIES,
            fixture(stepJson(), """{"activities":[${activityObj()},${activityObj()}]}""", versionJson(activities = 2))
        )
        assertFails(CatalogDecoder.VERSION, fixture(stepJson(), version = versionJson(steps = 2)))
        assertFails(CatalogDecoder.VERSION, fixture(stepJson(), version = versionJson(activities = 2)))
    }

    @Test
    fun `bad or missing activities and version files fail naming them`() {
        for (file in listOf(CatalogDecoder.ACTIVITIES, CatalogDecoder.VERSION)) {
            val ok = fixture(stepJson())
            assertFails(file) { if (it == file) "{\"bogus\":1}" else ok(it) }
            assertFails(file) { if (it == file) throw java.io.FileNotFoundException(it) else ok(it) }
        }
    }

    @Test
    fun `failures are not cached`() = runTest {
        var fail = true
        val ok = fixture(stepJson())
        val repo = CachedCatalogRepository({
            if (fail) throw java.io.IOException("boom")
            ok(it)
        }, StandardTestDispatcher(testScheduler))
        assertTrue(repo.load().exceptionOrNull() is CatalogLoadException)
        fail = false
        assertTrue(repo.load().isSuccess)
    }

    @Test
    fun `task names come from the task headers with the id as dev fallback`() {
        assertEquals("ع", CatalogDecoder.decode(fixture(stepJson())).tasks.single().nameAr)
        val noName = fixture("""{"areas":[{"id":"a","nameAr":"\u0645"}],"tasks":[${taskObj(name = null)}],"steps":[${stepObj()}]}""")
        assertEquals("x.y", CatalogDecoder.decode(noName).tasks.single().nameAr)
        assertFalse(CatalogDecoder.decode(noName).tasks.single().hasName)
    }

    @Test
    fun `area names come from the catalogue`() {
        assertEquals(mapOf("a" to "\u0645"), CatalogDecoder.decode(fixture(stepJson())).areaNames)
        assertEquals(real().tasks.map { it.area }.distinct().toSet(), real().areaNames.keys)
        real().areaNames.forEach { (id, name) -> assertTrue(id, name.any { it in '\u0600'..'\u06FF' }) }
    }

    @Test
    fun `real assets name every task in Arabic`() {
        real().tasks.forEach { t -> assertTrue(t.id, t.nameAr != t.id && t.nameAr.any { it in '؀'..'ۿ' }) }
    }

    @Test
    fun `fixture with null media and audio loads`() {
        val c = CatalogDecoder.decode(fixture(stepJson()))
        assertEquals(null, c.steps.single().audio)
        assertEquals(null, c.steps.single().media("v"))
        assertEquals(Step("x.y.01", Method.NONE, null, Detector(emptyList())), c.steps.single().toStep())
    }

    @Test
    fun `unknown check type fails naming file and type`() {
        val e = runCatching { CatalogDecoder.decode(fixture(stepJson("""{"method":"pose","framing":null,"check":{"type":"foo"}}"""))) }
            .exceptionOrNull()
        assertNotNull(e)
        assertTrue(e!!.message!!.contains(CatalogDecoder.ASSESSMENT))
        assertTrue(e.message!!.contains("foo"))
    }

    @Test
    fun `unknown field fails`() {
        val e = runCatching { CatalogDecoder.decode(fixture(stepJson(extra = ""","bogus":1"""))) }.exceptionOrNull()
        assertTrue(e is CatalogLoadException && e.message!!.contains(CatalogDecoder.ASSESSMENT))
    }

    @Test
    fun `missing file fails naming it`() {
        val e = runCatching { CatalogDecoder.decode { throw java.io.FileNotFoundException(it) } }.exceptionOrNull()
        assertTrue(e!!.message!!.startsWith(CatalogDecoder.ASSESSMENT))
    }

    @Test
    fun `repository caches success and returns failure as result`() = runTest {
        var reads = 0
        val repo = CachedCatalogRepository({
            reads++
            realRead(it)
        }, StandardTestDispatcher(testScheduler))
        val first = repo.load().getOrThrow()
        assertSame(first, repo.load().getOrThrow())
        assertEquals(3, reads)
        val bad = CachedCatalogRepository(fixture(stepJson(extra = ""","bogus":1""")), StandardTestDispatcher(testScheduler)).load()
        assertTrue(bad.exceptionOrNull() is CatalogLoadException)
    }

    @Test
    fun `landmarkers derive from method and check`() {
        assertEquals(listOf(Landmarker.POSE, Landmarker.HAND), StepDetector(Method.CLOUD).landmarkers)
        assertEquals(listOf(Landmarker.POSE, Landmarker.HAND), StepDetector(Method.HAND).landmarkers)
        assertEquals(listOf(Landmarker.POSE), StepDetector(Method.POSE).landmarkers)
        assertEquals(
            listOf(Landmarker.POSE, Landmarker.HAND),
            StepDetector(Method.POSE, check = Check.HandsRubbing(1.0, 1.0, 1.0)).landmarkers
        )
    }
}
