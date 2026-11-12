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
data class Step(val id: String, val method: Method, val check: Check? = null, val detector: Detector? = null)
