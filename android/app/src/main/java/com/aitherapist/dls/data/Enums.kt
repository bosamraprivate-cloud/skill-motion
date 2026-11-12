package com.aitherapist.dls.data

/** Child effect level (per child profile), default CALM. Stored lower_snake; a legacy stored `off` reads as CALM. */
enum class EffectLevel { FULL, CALM }

/** Character chosen on the child profile (AD-11). Stored lower_snake (`boy`, `girl`). */
enum class ChildCharacter { BOY, GIRL }

enum class SessionKind { ASSESSMENT, REPLAY }

enum class SessionState { RUNNING, PAUSED, DONE, ABANDONED }

/** Attempt verdict. Doubt is never failure: uncertainty is [CANNOT_TELL], never [NOT_DONE]. */
enum class AttemptVerdict { DONE, NOT_DONE, CANNOT_TELL }

enum class VerifierKind { POSE, HAND, CLOUD, NONE }

/** lower_snake storage name of an enum constant (`NOT_DONE` -> `not_done`). */
internal fun Enum<*>.storageName(): String = name.lowercase()

internal inline fun <reified E : Enum<E>> enumFromStorage(value: String): E = enumValues<E>().firstOrNull { it.storageName() == value }
    ?: throw IllegalArgumentException("Unknown ${E::class.simpleName} value '$value'")
