package com.example.spendy.util

object ParseAmount {

    /** Parses "₹1,268.50" / "1268.5" / "1268" / "1,268" to paise. Returns null on parse error. */
    fun toPaise(input: String): Long? {
        val cleaned = input.trim()
            .removePrefix("₹")
            .removePrefix("Rs.")
            .removePrefix("Rs")
            .removePrefix("INR")
            .trim()
            .replace(",", "")
        val d = cleaned.toDoubleOrNull() ?: return null
        if (d < 0) return null
        return Math.round(d * 100.0)
    }

    /** Formats paise back to a string the user can edit. "368" / "368.50". */
    fun formatPaise(amountPaise: Long): String {
        val rupees = amountPaise / 100
        val paise = amountPaise % 100
        return if (paise == 0L) rupees.toString() else "$rupees.${paise.toString().padStart(2, '0')}"
    }
}
