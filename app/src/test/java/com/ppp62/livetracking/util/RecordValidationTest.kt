package com.ppp62.livetracking.util

import org.junit.Assert.*
import org.junit.Test

class RecordValidationTest {
    @Test fun rejectsNonFiniteValues() {
        listOf("NaN","Infinity","-Infinity","1e999").forEach {assertFalse(RecordValidation.number(it,true))}
    }
    @Test fun preservesMissingOptionalValues() {
        assertTrue(RecordValidation.number("",false))
        assertFalse(RecordValidation.number("",true))
        assertFalse(RecordValidation.number("fish",false))
    }
    @Test fun validatesWeightWithoutRejectingValidTemperature() {
        assertFalse(RecordValidation.number("-2",true,true))
        assertTrue(RecordValidation.number("0",true,true))
        assertTrue(RecordValidation.number("-2",true))
    }
}
