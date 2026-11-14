package com.aitherapist.dls.catalog

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Catalogue content models (AD-4). Field names and types follow the design's Check types table
 * and tools/catalog-builder/schema/check.schema.json; an unknown `type` fails decoding.
 */
object CatalogJson {
    val json: Json =
        Json {
            classDiscriminator = "type"
            ignoreUnknownKeys = false
        }

    fun decodeStep(text: String): Step = json.decodeFromString(Step.serializer(), text)

    fun decodeCheck(text: String): Check = json.decodeFromString(Check.serializer(), text)

    fun decodeCriterion(text: String): Criterion = json.decodeFromString(Criterion.serializer(), text)
}

@Serializable
enum class Side {
    @SerialName("left")
    LEFT,

    @SerialName("right")
    RIGHT,

    @SerialName("either")
    EITHER
}

/** A landmark reference: `{"lm": "LEFT_WRIST"}` or `{"mid": ["LEFT_HIP", "RIGHT_HIP"]}`. */
@Serializable(with = LandmarkRefSerializer::class)
sealed interface LandmarkRef {
    @Serializable
    data class Lm(val lm: String) : LandmarkRef

    @Serializable
    data class Mid(val mid: List<String>) : LandmarkRef {
        init {
            require(mid.size == 2) { "mid needs exactly two landmark names" }
        }
    }
}

internal object LandmarkRefSerializer : JsonContentPolymorphicSerializer<LandmarkRef>(LandmarkRef::class) {
    override fun selectDeserializer(element: JsonElement): KSerializer<out LandmarkRef> = when (element.jsonObject.keys) {
        setOf("lm") -> LandmarkRef.Lm.serializer()
        setOf("mid") -> LandmarkRef.Mid.serializer()
        else -> throw IllegalArgumentException("landmark ref must have exactly one of lm, mid")
    }
}

/** Closed set of check types (AD-4). `holdSec` must hold continuously within the attempt window. */
@Serializable
sealed class Check {
    @Serializable
    @SerialName("angleAbove")
    data class AngleAbove(
        val a: LandmarkRef,
        val vertex: LandmarkRef,
        val b: LandmarkRef,
        val deg: Double,
        val holdSec: Double,
        val side: Side? = null
    ) : Check()

    @Serializable
    @SerialName("angleBelow")
    data class AngleBelow(
        val a: LandmarkRef,
        val vertex: LandmarkRef,
        val b: LandmarkRef,
        val deg: Double,
        val holdSec: Double,
        val side: Side? = null
    ) : Check()

    @Serializable
    @SerialName("pointAbove")
    data class PointAbove(val point: LandmarkRef, val ref: LandmarkRef, val margin: Double, val holdSec: Double, val side: Side? = null) :
        Check()

    @Serializable
    @SerialName("distanceBelow")
    data class DistanceBelow(val a: LandmarkRef, val b: LandmarkRef, val ratio: Double, val holdSec: Double, val side: Side? = null) :
        Check()

    @Serializable
    @SerialName("distanceAbove")
    data class DistanceAbove(val a: LandmarkRef, val b: LandmarkRef, val ratio: Double, val holdSec: Double, val side: Side? = null) :
        Check()

    @Serializable
    @SerialName("inZone")
    data class InZone(val landmark: LandmarkRef, val zone: String, val holdSec: Double, val side: Side? = null) : Check()

    @Serializable
    @SerialName("handsRubbing")
    data class HandsRubbing(val maxWristRatio: Double, val minMotionRatio: Double, val holdSec: Double) : Check()

    @Serializable
    @SerialName("question")
    data class Question(val text: String) : Check()
}

/** Training criterion keyed by `kind` (AD-4); `low`/`high` are counting hysteresis thresholds. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("kind")
sealed class Criterion {
    abstract val check: Check
    abstract val perSide: Boolean
    abstract val low: Double?
    abstract val high: Double?

    @Serializable
    @SerialName("reps")
    data class Reps(
        override val check: Check,
        val target: Int,
        override val perSide: Boolean = false,
        override val low: Double? = null,
        override val high: Double? = null
    ) : Criterion()

    /** [target] is in seconds. */
    @Serializable
    @SerialName("hold")
    data class Hold(
        override val check: Check,
        val target: Double,
        override val perSide: Boolean = false,
        override val low: Double? = null,
        override val high: Double? = null
    ) : Criterion()
}

@Serializable
enum class Method {
    @SerialName("pose")
    POSE,

    @SerialName("hand")
    HAND,

    @SerialName("cloud")
    CLOUD,

    @SerialName("none")
    NONE
}

@Serializable
enum class Landmarker {
    @SerialName("pose")
    POSE,

    @SerialName("hand")
    HAND
}

/** Which landmarkers a step needs (AD-3). */
@Serializable
data class Detector(val landmarkers: List<Landmarker>)

@Serializable
data class Step(
    val id: String,
    val method: Method,
    val check: Check? = null,
    val detector: Detector? = null,
    /** Story 6.2: seconds the attempt window stays open (the time ring); null in catalogues built before it. */
    val timeSec: Int? = null
)

/* ---- Generated catalogue shape (Story 2.7 builder output, Story 2.8 loader) ---- */

/** `assessment.json`: `{"areas": [...], "tasks": [...], "steps": [...]}` in catalogue order. */
@Serializable
data class AssessmentFile(val areas: List<AreaEntry>, val tasks: List<TaskEntry>, val steps: List<CatalogStep>)

/** One area with its Arabic name; [nameAr] is null only in a dev catalogue missing it. */
@Serializable
data class AreaEntry(val id: String, val nameAr: String?)

/** One task header from the builder; [nameAr] is null only in a dev catalogue missing the name (Story 2.16). */
@Serializable
data class TaskEntry(val id: String, val area: String, val nameAr: String?, val source: String, val posture: String? = null)

/** `activities.json`: `{"activities": [...]}`. */
@Serializable
data class ActivitiesFile(val activities: List<Activity>)

/** `version.json`; [complete] is false for a dev catalogue with missing media or audio. */
@Serializable
data class CatalogVersion(val catalogVersion: String, val complete: Boolean, val steps: Int, val activities: Int)

/** A step's detector setting as written by the builder from detectors.json. */
@Serializable
data class StepDetector(val method: Method, val framing: String? = null, val check: Check? = null) {
    /** Landmarkers to run (AD-3): pose/hand from the method, both for cloud, none for `none`. */
    val landmarkers: List<Landmarker>
        get() = when (method) {
            Method.NONE -> emptyList()
            Method.CLOUD -> listOf(Landmarker.POSE, Landmarker.HAND)
            Method.POSE -> if (check is Check.HandsRubbing) listOf(Landmarker.POSE, Landmarker.HAND) else listOf(Landmarker.POSE)
            // Hand checks scale by pose shoulder width, so pose always runs too (coordinator decision, Story 3.3).
            Method.HAND -> listOf(Landmarker.POSE, Landmarker.HAND)
        }
}

/** Normal and slow audio paths relative to the assets root. */
@Serializable
data class StepAudio(val normal: String, val slow: String)

/** Per-variant still frame and optional demonstration clip, relative to the assets root. */
@Serializable
data class StepMedia(val clip: String? = null, val still: String)

@Serializable
data class CatalogStep(
    val id: String,
    val area: String,
    val task: String,
    val number: Int,
    val textAr: String,
    val textEn: String,
    val subSkills: String,
    val tags: List<String>,
    val source: String,
    val detector: StepDetector,
    val audio: StepAudio? = null,
    /** Keyed by character variant id; a null value means that variant's media is missing (dev catalogue). */
    val media: Map<String, StepMedia?>,
    /** Story 6.2: attempt time in seconds from step_timing.json; null in catalogues built before it. */
    val timeSec: Int? = null
) {
    fun media(variant: String): StepMedia? = media[variant]

    /** The runtime step the runner and verifiers consume. */
    fun toStep(): Step = Step(
        id = id,
        method = detector.method,
        check = detector.check,
        detector = Detector(detector.landmarkers),
        timeSec = timeSec
    )
}

@Serializable
data class Activity(
    val id: String,
    val plan: String,
    val domain: String,
    val number: Int,
    val title: String,
    val textAr: String,
    val generalGoal: String,
    val goal: String,
    val howToPerform: String,
    val tools: String,
    val criterion: String,
    val source: String,
    val audio: StepAudio? = null,
    val counter: Criterion? = null
)

/**
 * A task: its id, Arabic name (the id in a dev catalogue missing it) and its steps in catalogue order.
 * [hasName] is false when the catalogue had no name (the child then sees no name text).
 */
data class CatalogTask(
    val id: String,
    val area: String,
    val steps: List<CatalogStep>,
    val nameAr: String = id,
    val hasName: Boolean = true,
    /** How the child is positioned (staging only, never scoring); [Posture.UNKNOWN] for older catalogues. */
    val posture: Posture = Posture.UNKNOWN
)

/** Task posture from the catalogue's `posture` field (owner staging source, Story 6.1). */
enum class Posture {
    SEATED,
    STANDING,
    STANDING_OR_SEATED,
    UNKNOWN;

    companion object {
        fun parse(value: String?): Posture = when (value) {
            "seated" -> SEATED
            "standing" -> STANDING
            "standing_or_seated" -> STANDING_OR_SEATED
            else -> UNKNOWN
        }
    }
}

/** The whole loaded catalogue. */
data class Catalog(
    val version: CatalogVersion,
    val tasks: List<CatalogTask>,
    val activities: List<Activity>,
    /** Area id -> Arabic area name (the id in a dev catalogue missing it). */
    val areaNames: Map<String, String> = emptyMap()
) {
    val steps: List<CatalogStep> get() = tasks.flatMap { it.steps }
    val catalogVersion: String get() = version.catalogVersion
    val complete: Boolean get() = version.complete
}

class CatalogLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Decodes the three generated files; failures name the file. Pure (no Android types). */
object CatalogDecoder {
    const val ASSESSMENT = "catalog/assessment.json"
    const val ACTIVITIES = "catalog/activities.json"
    const val VERSION = "catalog/version.json"

    fun decode(read: (String) -> String): Catalog {
        val assessment = parse(ASSESSMENT, read) { CatalogJson.json.decodeFromString(AssessmentFile.serializer(), it) }
        val activities = parse(ACTIVITIES, read) { CatalogJson.json.decodeFromString(ActivitiesFile.serializer(), it) }
        val version = parse(VERSION, read) { CatalogJson.json.decodeFromString(CatalogVersion.serializer(), it) }
        val steps = assessment.steps
        check(steps.isNotEmpty(), ASSESSMENT) { "no steps" }
        check(activities.activities.isNotEmpty(), ACTIVITIES) { "no activities" }
        steps.map { it.id }.duplicates().let { check(it.isEmpty(), ASSESSMENT) { "duplicate step ids $it" } }
        activities.activities.map { it.id }.duplicates().let { check(it.isEmpty(), ACTIVITIES) { "duplicate activity ids $it" } }
        check(version.steps == steps.size, VERSION) { "steps ${version.steps} but assessment has ${steps.size}" }
        check(version.activities == activities.activities.size, VERSION) {
            "activities ${version.activities} but activities file has ${activities.activities.size}"
        }
        if (version.complete) {
            val missing = steps.filter { s -> s.audio == null || s.media.isEmpty() || s.media.values.any { it == null } }
            check(missing.isEmpty(), ASSESSMENT) { "complete=true but audio/media null for ${missing.map { it.id }}" }
        }
        val tasks = mutableListOf<CatalogTask>()
        for (step in steps) {
            val last = tasks.lastOrNull()
            if (last != null && last.id == step.task) {
                check(last.area == step.area, ASSESSMENT) { "task ${step.task} mixes areas ${last.area} and ${step.area}" }
                tasks[tasks.lastIndex] = last.copy(steps = last.steps + step)
            } else {
                check(tasks.none { it.id == step.task }, ASSESSMENT) { "task ${step.task} steps are not contiguous" }
                tasks += CatalogTask(step.task, step.area, listOf(step))
            }
        }
        check(assessment.tasks.map { it.id } == tasks.map { it.id }, ASSESSMENT) {
            "tasks ${assessment.tasks.map { it.id }} do not match the step tasks ${tasks.map { it.id }}"
        }
        val named =
            tasks.zip(assessment.tasks) { task, entry ->
                check(entry.area == task.area, ASSESSMENT) { "task ${task.id} area ${entry.area} but steps say ${task.area}" }
                if (version.complete) check(!entry.nameAr.isNullOrBlank(), ASSESSMENT) { "complete=true but task ${task.id} has no name" }
                val name = entry.nameAr?.takeIf { it.isNotBlank() }
                task.copy(nameAr = name ?: task.id, hasName = name != null, posture = Posture.parse(entry.posture))
            }
        check(assessment.areas.map { it.id } == named.map { it.area }.distinct(), ASSESSMENT) {
            "areas ${assessment.areas.map { it.id }} do not match the task areas"
        }
        if (version.complete) {
            check(assessment.areas.all { !it.nameAr.isNullOrBlank() }, ASSESSMENT) { "complete=true but an area has no name" }
        }
        val areaNames = assessment.areas.associate { it.id to (it.nameAr?.takeIf { n -> n.isNotBlank() } ?: it.id) }
        return Catalog(version, named, activities.activities, areaNames)
    }

    private fun List<String>.duplicates(): List<String> = groupingBy { it }.eachCount().filterValues { it > 1 }.keys.toList()

    private inline fun check(ok: Boolean, file: String, message: () -> String) {
        if (!ok) throw CatalogLoadException("$file: ${message()}")
    }

    private fun <T> parse(file: String, read: (String) -> String, block: (String) -> T): T {
        val text = try {
            read(file)
        } catch (e: Exception) {
            throw CatalogLoadException("$file: cannot read (${e.message})", e)
        }
        return try {
            block(text)
        } catch (e: Exception) {
            throw CatalogLoadException("$file: ${e.message}", e)
        }
    }
}
