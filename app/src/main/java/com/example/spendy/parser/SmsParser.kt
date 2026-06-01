package com.example.spendy.parser

import com.example.spendy.parser.extractor.FastagTollExtractor
import com.example.spendy.parser.extractor.GenericFallbackExtractor
import com.example.spendy.parser.extractor.HdfcCreditAlertExtractor
import com.example.spendy.parser.extractor.HdfcUpiDebitExtractor
import com.example.spendy.parser.extractor.IciciCardSpendExtractor
import com.example.spendy.parser.extractor.IciciCcPaymentExtractor
import com.example.spendy.parser.extractor.IciciCcReversalExtractor
import com.example.spendy.parser.extractor.MutualFundSipExtractor
import com.example.spendy.parser.extractor.PayUExtractor
import com.example.spendy.parser.extractor.SbiCardSpendExtractor
import com.example.spendy.parser.extractor.SbiNachExtractor
import com.example.spendy.parser.extractor.SbiNeftCreditExtractor
import com.example.spendy.parser.extractor.SbiUpiDebitExtractor
import com.example.spendy.parser.extractor.WalletDebitExtractor

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
