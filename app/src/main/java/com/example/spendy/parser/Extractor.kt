package com.example.spendy.parser

interface Extractor {
    val name: String
    fun match(sender: String, body: String, receivedAtMillis: Long): ParsedTransaction?
}

object ParseUtil {

    private val AMOUNT = Regex("(?:rs\\.?|inr|₹)\\s?([\\d,]+\\.?\\d*)", RegexOption.IGNORE_CASE)

    private val AMOUNT_WITH_CCY = Regex(
        "(rs\\.?|inr|₹|usd|\\\$)\\s?([\\d,]+\\.?\\d*)",
        RegexOption.IGNORE_CASE,
    )

    fun parseAmountPaise(raw: String): Long? {
        val cleaned = raw.replace(",", "").trim()
        val asDouble = cleaned.toDoubleOrNull() ?: return null
        return Math.round(asDouble * 100.0)
    }

    fun findAmountPaise(body: String): Long? {
        val m = AMOUNT.find(body) ?: return null
        return parseAmountPaise(m.groupValues[1])
    }

    /** Returns (amountInMinorUnits, ISO-currency-code) or null when no money phrase is present. */
    fun findAmountWithCurrency(body: String): Pair<Long, String>? {
        val m = AMOUNT_WITH_CCY.find(body) ?: return null
        val amount = parseAmountPaise(m.groupValues[2]) ?: return null
        val token = m.groupValues[1].lowercase()
        val currency = when (token) {
            "usd", "$" -> "USD"
            else -> "INR"
        }
        return amount to currency
    }

    fun normalizeMerchant(raw: String?): String? =
        raw?.uppercase()?.replace(Regex("\\s+"), "")?.takeIf { it.isNotEmpty() }

    /**
     * Folds Unicode "styled" letters (mathematical bold/italic/sans-serif, fullwidth, etc.) into
     * plain ASCII so that simple regexes like `\bspent\b` match. Some senders — notably SBI Card's
     * RCS template — write the verbs in Mathematical Alphanumeric Symbols (U+1D400 block) to
     * make the message look bolder; without normalization the filter and extractors miss them.
     */
    fun normalizeBody(body: String): String =
        java.text.Normalizer.normalize(body, java.text.Normalizer.Form.NFKC)

    /** Parse dd/mm/yy or dd-mm-yy or dd-MMM-yy. Falls back to receivedAt if it can't parse. */
    fun parseOccurredAt(dateStr: String?, receivedAtMillis: Long): Long {
        if (dateStr.isNullOrBlank()) return receivedAtMillis
        val candidates = listOf("dd/MM/yy", "dd/MM/yyyy", "dd-MM-yy", "dd-MM-yyyy", "dd-MMM-yy", "dd-MMM-yyyy")
        for (pat in candidates) {
            try {
                val fmt = java.text.SimpleDateFormat(pat, java.util.Locale.ENGLISH)
                fmt.isLenient = false
                val d = fmt.parse(dateStr.trim()) ?: continue
                return d.time
            } catch (_: Exception) {
                // try next
            }
        }
        return receivedAtMillis
    }
}
