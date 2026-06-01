package com.kaizenll.xpendiq.parser

/**
 * Pre-parse gate. Reject anything that is not a transactional SMS from a bank-like sender.
 * Filters run in order; first match wins.
 */
object SmsFilter {

    private val SENDER_BANK = Regex("^[A-Z]{2}-[A-Z0-9]{4,8}(-[A-Z])?$")

    private val OTP = Regex(
        "\\b(otp|one[- ]time password|verification code|do not share|secure code)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val SCHEDULED = Regex(
        "(scheduled on|will be debited|to be debited from|autopay for[^\\n]*scheduled|standing instructions[^.]*is due|has requested money|emi will|will get debited|upcoming|due on|due by\\s+\\d)",
        RegexOption.IGNORE_CASE,
    )
    private val BALANCE_REPORT = Regex(
        "(reported your (fund|securities) bal|at eod .* reported your|current balance is|closing bal|avl(?:bl)?\\s*limit\\b(?!\\s*:?\\s*\\w*\\s*spent)|available\\s*(?:credit\\s*)?limit\\s*(?:is|:)|available\\s*balance\\s*(?:is|:))",
        RegexOption.IGNORE_CASE,
    )
    private val PROMO = Regex(
        "(t&c|apply now|claim your|voucher|sale is live|pre[- ]approved|cashback eligible|coupon)",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Statement / due-payment reminders. These mention amounts but represent something the user
     * needs to pay, not a transaction that has happened. Drop them outright.
     */
    private val STATEMENT_REMINDER = Regex(
        "(" +
            "total\\s+(amount\\s+)?due\\s+(of\\s+)?rs" +              // "Total Amount Due of Rs ..."
            "|min(?:imum)?\\s+(amount\\s+)?due\\s+(of\\s+)?rs" +       // "Minimum Due Rs ..." / "Min Amt Due"
            "|total\\s+of\\s+rs\\s*[\\d,]+\\s+or\\s+min(?:imum)?" +    // "Total of Rs X or minimum of Rs Y"
            "|min(?:imum)?\\s+of\\s+rs\\s*[\\d,]+\\s+is\\s+due" +
            "|statement\\s+is\\s+sent" +
            "|pay\\s+total\\s+due" +
            "|pay\\s+(your\\s+)?minimum\\s+due" +
            "|payment\\s+due\\s+by" +
            "|is\\s+due\\s+(by|on)\\s+\\d" +                          // "is due by 30-Aug-25"
            "|by\\s+\\d{2}-[a-z]{3}-\\d{2,4}.*credit\\s+card" +        // "by 30-Aug-25 for ... Credit Card"
            "|bill\\s+for\\s+[a-z]{3,}-?\\d+\\s+on\\s+a/?c" +          // "bill for MAR-26 on A/c"
            "|reported\\s+to\\s+credit\\s+bureaus" +                  // "Delayed/No payments reported to Credit Bureaus"
            ")",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Shipping / order-status updates from non-bank service senders that happen to use a
     * transactional verb ("already paid", "purchase confirmed"). Drop them before the parser
     * sees the verb.
     */
    private val ORDER_STATUS = Regex(
        "(out for delivery|tracking number|is confirmed and should be delivered|undelivered|has been shipped|order has been|will be delivered|ticket\\s+\\d+\\s+has been (updated|resolved)|service request|share your feedback|refund will be processed)",
        RegexOption.IGNORE_CASE,
    )

    private val TRANSACTION_VERBS = Regex(
        "\\b(debited|credited|spent|received on|withdrawn|sent rs|reversal of)\\b",
        RegexOption.IGNORE_CASE,
    )

    enum class Decision {
        PARSE,
        DROP_NON_BANK,
        DROP_OTP,
        DROP_SCHEDULED,
        DROP_STATEMENT_REMINDER,
        DROP_ORDER_STATUS,
        DROP_BALANCE_REPORT,
        DROP_PROMO,
    }

    fun classify(sender: String, body: String, requireBankSender: Boolean = true): Decision {
        if (requireBankSender && !SENDER_BANK.matches(sender)) return Decision.DROP_NON_BANK
        if (OTP.containsMatchIn(body)) return Decision.DROP_OTP
        if (SCHEDULED.containsMatchIn(body)) return Decision.DROP_SCHEDULED
        if (STATEMENT_REMINDER.containsMatchIn(body)) return Decision.DROP_STATEMENT_REMINDER
        if (ORDER_STATUS.containsMatchIn(body)) return Decision.DROP_ORDER_STATUS
        // Promo-with-link guard: only drop if it doesn't *also* contain a (strong) transactional verb.
        if (PROMO.containsMatchIn(body) && !TRANSACTION_VERBS.containsMatchIn(body)) return Decision.DROP_PROMO
        // Standalone "Avl Limit / Available Limit" notifications.
        if (BALANCE_REPORT.containsMatchIn(body) && !TRANSACTION_VERBS.containsMatchIn(body)) return Decision.DROP_BALANCE_REPORT
        return Decision.PARSE
    }
}
