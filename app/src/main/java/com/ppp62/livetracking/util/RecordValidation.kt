package com.ppp62.livetracking.util

object RecordValidation {
    fun number(text:String,required:Boolean,nonNegative:Boolean=false):Boolean {
        if(text.isBlank()) return !required
        val value=text.toDoubleOrNull() ?: return false
        return value.isFinite() && (!nonNegative || value>=0)
    }
}
