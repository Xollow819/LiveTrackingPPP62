package com.ppp62.livetracking.util

object RecordValidation {
    fun number(text:String,required:Boolean,nonNegative:Boolean=false):Boolean {
        if(text.isBlank()) return !required
        val value=text.toDoubleOrNull() ?: return false
        return value.isFinite() && (!nonNegative || value>=0)
    }
    fun wholeNumber(text: String, required: Boolean = false, minimum: Int = 0): Boolean {
        if (text.isBlank()) return !required
        return text.toIntOrNull()?.let { it >= minimum } == true
    }
    fun range(text: String, range: ClosedFloatingPointRange<Double>, required: Boolean = false): Boolean {
        if (text.isBlank()) return !required
        return text.toDoubleOrNull()?.let { it.isFinite() && it in range } == true
    }
}
