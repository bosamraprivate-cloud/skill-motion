package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.data.ChildCharacter
import com.aitherapist.dls.data.EffectLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChildValidationTest {
    private val valid = ChildForm(name = "Mariam", code = "A1", age = "6", character = ChildCharacter.GIRL)

    @Test
    fun `valid form has no errors and default effect is calm`() {
        assertTrue(ChildValidation.validate(valid).isEmpty)
        assertEquals(EffectLevel.CALM, ChildForm().effectLevel)
    }

    @Test
    fun `missing fields are required`() {
        val errors = ChildValidation.validate(ChildForm(name = "  ", code = " "))
        assertEquals(ChildFieldError.REQUIRED, errors.name)
        assertEquals(ChildFieldError.REQUIRED, errors.code)
        assertEquals(ChildFieldError.REQUIRED, errors.age)
        assertEquals(ChildFieldError.REQUIRED, errors.character)
        assertFalse(errors.isEmpty)
    }

    @Test
    fun `age must be a whole number from 1 to 18`() {
        listOf("0", "19", "x", "6.5", "-3").forEach {
            assertEquals(it, ChildFieldError.AGE_RANGE, ChildValidation.validate(valid.copy(age = it)).age)
        }
        listOf("1", "18", " 7 ").forEach { assertNull(it, ChildValidation.validate(valid.copy(age = it)).age) }
        assertEquals(7, ChildValidation.parseAge(" 7 "))
    }

    @Test
    fun `code in use is refused`() {
        assertEquals(ChildFieldError.CODE_IN_USE, ChildValidation.validate(valid, codeInUse = true).code)
    }

    @Test
    fun `form toString hides name and code`() {
        assertFalse(valid.toString().contains("Mariam"))
        assertFalse(valid.toString().contains("A1"))
    }

    @Test
    fun `code allows only ascii letters digits and hyphen`() {
        assertNull(ChildValidation.validate(valid.copy(code = " ab-12 ")).code)
        listOf("A 1", "A_1", "اب", "é1").forEach {
            assertEquals(it, ChildFieldError.INVALID, ChildValidation.validate(valid.copy(code = it)).code)
        }
        assertEquals("AB-12", ChildValidation.normalizeCode(" ab-12 "))
    }

    @Test
    fun `name and code have length limits`() {
        assertNull(ChildValidation.validate(valid.copy(name = "n".repeat(40), code = "C".repeat(16))).name)
        assertEquals(ChildFieldError.TOO_LONG, ChildValidation.validate(valid.copy(name = "n".repeat(41))).name)
        assertEquals(ChildFieldError.TOO_LONG, ChildValidation.validate(valid.copy(code = "C".repeat(17))).code)
    }
}
