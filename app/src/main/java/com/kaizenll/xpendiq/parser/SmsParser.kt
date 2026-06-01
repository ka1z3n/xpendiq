package com.kaizenll.xpendiq.parser

import com.kaizenll.xpendiq.parser.extractor.FastagTollExtractor
import com.kaizenll.xpendiq.parser.extractor.GenericFallbackExtractor
import com.kaizenll.xpendiq.parser.extractor.HdfcCreditAlertExtractor
import com.kaizenll.xpendiq.parser.extractor.HdfcUpiDebitExtractor
import com.kaizenll.xpendiq.parser.extractor.IciciCardSpendExtractor
import com.kaizenll.xpendiq.parser.extractor.IciciCcPaymentExtractor
import com.kaizenll.xpendiq.parser.extractor.IciciCcReversalExtractor
import com.kaizenll.xpendiq.parser.extractor.MutualFundSipExtractor
import com.kaizenll.xpendiq.parser.extractor.PayUExtractor
import com.kaizenll.xpendiq.parser.extractor.SbiCardSpendExtractor
import com.kaizenll.xpendiq.parser.extractor.SbiNachExtractor
import com.kaizenll.xpendiq.parser.extractor.SbiNeftCreditExtractor
import com.kaizenll.xpendiq.parser.extractor.SbiUpiDebitExtractor
import com.kaizenll.xpendiq.parser.extractor.WalletDebitExtractor

class SmsParser(
    private val extractors: List<Extractor> = defaultChain(),
) {

    fun parse(
        sender: String,
        body: String,
        receivedAtMillis: Long,
        requireBankSender: Boolean = true,
    ): ParsedTransaction? {
        val normalized = ParseUtil.normalizeBody(body)
        if (SmsFilter.classify(sender, normalized, requireBankSender) != SmsFilter.Decision.PARSE) return null
        for (extractor in extractors) {
            val result = extractor.match(sender, normalized, receivedAtMillis) ?: continue
            return result
        }
        return null
    }

    companion object {
        fun defaultChain(): List<Extractor> = listOf(
            HdfcUpiDebitExtractor,
            HdfcCreditAlertExtractor,
            SbiCardSpendExtractor,
            IciciCardSpendExtractor,
            IciciCcReversalExtractor,
            IciciCcPaymentExtractor,
            SbiUpiDebitExtractor,
            SbiNachExtractor,
            SbiNeftCreditExtractor,
            MutualFundSipExtractor,
            WalletDebitExtractor,
            PayUExtractor,
            FastagTollExtractor,
            GenericFallbackExtractor,
        )
    }
}
