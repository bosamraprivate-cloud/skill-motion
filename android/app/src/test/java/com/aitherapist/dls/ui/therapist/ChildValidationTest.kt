package com.aitherapist.dls.ui.therapist

import com.aitherapist.dls.data.ChildCharacter
import com.aitherapist.dls.data.EffectLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChildValidationTest {
    private val valid = ChildForm(name = "Mariam", age = "6", character = ChildCharacter.GIRL)

    @Test
    fun `valid form has no errors and default effect is calm`() {
        assertTrue(ChildValidation.validate(valid).isEmpty)
        assertEquals(EffectLevel.CALM, ChildForm().effectLevel)
        assertTrue(ChildForm().showTimeRing)
    }

    @Test
    fun `missing fields are required`() {
        val errors = ChildValidation.validate(ChildForm(name = "  "))
        assertEquals(ChildFieldError.REQUIRED, errors.name)
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
    fun `form toString hides the name`() {
        assertFalse(valid.toString().contains("Mariam"))
    }

    @Test
    fun `name has a length limit`() {
        assertNull(ChildValidation.validate(valid.copy(name = "n".repeat(40))).name)
        assertEquals(ChildFieldError.TOO_LONG, ChildValidation.validate(valid.copy(name = "n".repeat(41))).name)
    }
}
