package com.kaizenll.xpendiq.util

import java.text.NumberFormat
import java.util.Locale

object CurrencyFormat {

    private val inrFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"))
    private val usFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.US)

    /** Backwards-compatible: assumes INR. */
    fun paiseToInr(amountPaise: Long): String = format(amountPaise, "INR")

    /** Format any supported currency, falling back to the raw 3-letter code prefix. */
    fun format(amountMinor: Long, currency: String): String {
        val (whole, minor, formatter, symbol) = when (currency.uppercase()) {
            "INR" -> Quad(amountMinor / 100, amountMinor % 100, inrFormat, "₹")
            "USD" -> Quad(amountMinor / 100, amountMinor % 100, usFormat, "$")
            else -> Quad(amountMinor / 100, amountMinor % 100, inrFormat, "${currency.uppercase()} ")
        }
        return if (minor == 0L) {
            "$symbol${formatter.format(whole)}"
        } else {
            "$symbol${formatter.format(whole)}.${minor.toString().padStart(2, '0')}"
        }
    }

    private data class Quad(
        val whole: Long,
        val minor: Long,
        val formatter: NumberFormat,
        val symbol: String,
    )
}
