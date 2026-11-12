package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.data.ChildCharacter
import com.aitherapist.dls.data.EffectLevel
import com.aitherapist.dls.data.normalizeChildCode

/** Why a child form field is refused. */
enum class ChildFieldError { REQUIRED, CODE_IN_USE, AGE_RANGE, INVALID, TOO_LONG }

/** Raw add/edit form input. [age] stays text so "x" is refused instead of silently dropped. */
data class ChildForm(
    val name: String = "",
    val code: String = "",
    val age: String = "",
    val character: ChildCharacter? = null,
    val effectLevel: EffectLevel = EffectLevel.CALM
) {
    // Name and code identify a child; never print them.
    override fun toString(): String = "ChildForm(character=$character, effectLevel=$effectLevel)"
}

data class ChildFormErrors(
    val name: ChildFieldError? = null,
    val code: ChildFieldError? = null,
    val age: ChildFieldError? = null,
    val character: ChildFieldError? = null
) {
    val isEmpty: Boolean get() = name == null && code == null && age == null && character == null
}

/** Pure child-profile validation (Story 2.11). Code uniqueness needs the database, so the caller passes [codeInUse]. */
object ChildValidation {
    const val MIN_AGE = 1
    const val MAX_AGE = 18
    const val MAX_NAME = 40
    const val MAX_CODE = 16
    private val CODE_CHARS = Regex("[A-Z0-9-]+")

    /** Stored form of a code: trimmed, upper case (ASCII only is allowed, so SQLite and Kotlin agree). */
    fun normalizeCode(code: String): String = normalizeChildCode(code)

    /** Whole years in [MIN_AGE]..[MAX_AGE], or null. */
    fun parseAge(text: String): Int? = text.trim().toIntOrNull()?.takeIf { it in MIN_AGE..MAX_AGE }

    fun validate(form: ChildForm, codeInUse: Boolean = false): ChildFormErrors = ChildFormErrors(
        name = when {
            form.name.isBlank() -> ChildFieldError.REQUIRED
            form.name.trim().length > MAX_NAME -> ChildFieldError.TOO_LONG
            else -> null
        },
        code = when {
            form.code.isBlank() -> ChildFieldError.REQUIRED
            normalizeCode(form.code).length > MAX_CODE -> ChildFieldError.TOO_LONG
            !CODE_CHARS.matches(normalizeCode(form.code)) -> ChildFieldError.INVALID
            codeInUse -> ChildFieldError.CODE_IN_USE
            else -> null
        },
        age = when {
            form.age.isBlank() -> ChildFieldError.REQUIRED
            parseAge(form.age) == null -> ChildFieldError.AGE_RANGE
            else -> null
        },
        character = if (form.character == null) ChildFieldError.REQUIRED else null
    )
}
