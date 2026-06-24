package com.kaizenll.xpendiq.util

/**
 * Foreign-currency → INR conversion. The app stays offline: the rate is a manual value the user
 * sets in Settings, not a live feed. Only USD is recognized by the parser today, but the helper is
 * currency-agnostic — callers pass the rate that applies to [currency].
 */
object Fx {

    /** ISO-4217 code of the home currency; rows in this currency need no conversion. */
    const val HOME_CURRENCY = "INR"

    /**
     * The INR-equivalent (in paise) to freeze on a row, or null when no conversion applies:
     * the row is already INR, or no rate is set. A null result means "leave it out of totals".
     */
    fun toInrPaise(amountMinor: Long, currency: String, rate: Double?): Long? {
        if (currency.equals(HOME_CURRENCY, ignoreCase = true)) return null
        if (rate == null || rate <= 0.0) return null
        return Math.round(amountMinor * rate)
    }

    /**
     * A row's value in INR paise for totalling: the raw amount for INR rows, the frozen
     * [amountInrPaise] for converted foreign rows, and 0 for foreign rows not yet converted.
     * Mirrors the CASE expression the DAO uses so on-device day totals match Insights/Home.
     */
    fun inrEquivalentPaise(amountMinor: Long, currency: String, amountInrPaise: Long?): Long =
        if (currency.equals(HOME_CURRENCY, ignoreCase = true)) amountMinor else (amountInrPaise ?: 0L)
}
