package com.example.spendy.parser

import com.example.spendy.data.entity.PaymentMode
import com.example.spendy.data.entity.TransactionType
import com.example.spendy.parser.extractor.FastagTollExtractor
import com.example.spendy.parser.extractor.IciciCcPaymentExtractor
import com.example.spendy.parser.extractor.SbiNeftCreditExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {

    private val parser = SmsParser()
    private val now = 1_700_000_000_000L

    @Test fun `HDFC UPI debit is parsed with merchant`() {
        val body = """
            Sent Rs.60.00
            From HDFC Bank A/C *1234
            To Ramesh Kumar
            On 11/01/26
            Ref 200000000000
            Not You?
            Call 18002586161/SMS BLOCK UPI to 7308080808
        """.trimIndent()
        val p = parser.parse("VM-HDFCBK-T", body, now)
        assertNotNull(p)
        assertEquals(6000L, p!!.amountPaise)
        assertEquals(TransactionType.DEBIT, p.type)
        assertEquals(PaymentMode.UPI, p.paymentMode)
        assertEquals("Ramesh Kumar", p.merchantRaw)
        assertEquals("RAMESHKUMAR", p.merchantNormalized)
        assertEquals("1234", p.accountTail)
    }

    @Test fun `HDFC credit alert is parsed as CREDIT with VPA`() {
        val body = "Credit Alert!\nRs.100.00 credited to HDFC Bank A/c XX1234 on 19-11-25 from VPA john.doe@oksbi (UPI 100000000000)"
        val p = parser.parse("VM-HDFCBK-S", body, now)
        assertNotNull(p)
        assertEquals(10000L, p!!.amountPaise)
        assertEquals(TransactionType.CREDIT, p.type)
        assertEquals("john.doe@oksbi", p.merchantRaw)
    }

    @Test fun `SBI Credit Card spend parses merchant and tail`() {
        val body = "Rs.1,268.50 spent on your SBI Credit Card ending 9999 at ATRIACONVERGENCETECH on 05/12/25. Trxn. not done by you? Report at https://sbicard.com/Dispute"
        val p = parser.parse("VM-SBICRD-S", body, now)
        assertNotNull(p)
        assertEquals(126850L, p!!.amountPaise)
        assertEquals(TransactionType.DEBIT, p.type)
        assertEquals(PaymentMode.CARD_CREDIT, p.paymentMode)
        assertEquals("ATRIACONVERGENCETECH", p.merchantRaw)
        assertEquals("ATRIACONVERGENCETECH", p.merchantNormalized)
        assertEquals("9999", p.accountTail)
    }

    @Test fun `ICICI CC payment received is CREDIT with CC bill marker`() {
        val body = "Payment of Rs 16,002.17 has been received on your ICICI Bank Credit Card XX5151 through Bharat Bill Payment System on 23-MAY-25."
        val p = parser.parse("JD-ICICIT-S", body, now)
        assertNotNull(p)
        assertEquals(TransactionType.CREDIT, p!!.type)
        assertEquals(IciciCcPaymentExtractor.MERCHANT_MARKER, p.merchantNormalized)
    }

    @Test fun `SIP purchase is INVESTMENT`() {
        val body = "Your SIP Purchase in Folio 12345678/90 under HDFC BSE Sensex Index Fund-DP Growth for Rs. 19,999.00 has been processed at the NAV of 774.657 for 25.817 units and 23-Jun-2025."
        val p = parser.parse("AD-HDFCMF-S", body, now)
        assertNotNull(p)
        assertEquals(TransactionType.INVESTMENT, p!!.type)
        assertEquals(1_999_900L, p.amountPaise)
        assertTrue(p.merchantRaw!!.contains("HDFC BSE Sensex"))
    }

    @Test fun `SBI MF purchase is INVESTMENT`() {
        val body = "Dear Investor, Purchase transaction in Folio No. 87654321 in Scheme : SBI Small Cap Fund Dir Growth for date 24-Apr-2026 for amount of INR 24,998.75 at NAV of 191.0425 is processed for number of units 130.854 - SBIMF"
        val p = parser.parse("AD-SBIMFD-S", body, now)
        assertNotNull(p)
        assertEquals(TransactionType.INVESTMENT, p!!.type)
        assertEquals(2_499_875L, p.amountPaise)
    }

    @Test fun `AutoPay scheduled is dropped`() {
        val body = "Dear UPI User, UPI AutoPay for smallcase Publisher debit of Rs.4720.00 is scheduled on .13/12/25, 299d5317d639451dbc8fe442e49cc375@okicici. Please ensure sufficient balance in your account. -SBI"
        assertNull(parser.parse("VA-SBIUPI-S", body, now))
    }

    @Test fun `Collect request is dropped`() {
        val body = "MakeMyTrip has requested money through Google-pay. On approval, Rs 3450.00 will be debited from your Bank Account-ICICI Bank."
        assertNull(parser.parse("AD-ICICIT-S", body, now))
    }

    @Test fun `Broker EOD balance report is dropped`() {
        val body = "GROWW INVEST at EOD 27/02/2026 reported your Fund bal Rs 378.900 & Securities bal 0. This excludes your Bank, DP and PMS bal with the broker-BSE"
        assertNull(parser.parse("VA-BSELTD-S", body, now))
    }

    @Test fun `OTP is dropped`() {
        val body = "123456 is your OTP for your transaction at Amazon. Do not share with anyone."
        assertNull(parser.parse("AD-HDFCBK-S", body, now))
    }

    @Test fun `Non-bank sender is dropped`() {
        val body = "Rs 500 debited from your account"
        assertNull(parser.parse("9876543210", body, now))
    }

    @Test fun `ICICI card spend parses with merchant and tail`() {
        val body = "INR 508.00 spent using ICICI Bank Card XX5151 on 23-Jun-25 on INSTAMART. Avl Limit: INR 43,773.94. If not you, call 1800 2662/SMS BLOCK 5151 to 9215676766."
        val p = parser.parse("JD-ICICIT-S", body, now)
        assertNotNull(p)
        assertEquals(50800L, p!!.amountPaise)
        assertEquals(TransactionType.DEBIT, p.type)
        assertEquals(PaymentMode.CARD_CREDIT, p.paymentMode)
        assertEquals("INSTAMART", p.merchantRaw)
        assertEquals("5151", p.accountTail)
    }

    @Test fun `ICICI card spend multi-word merchant`() {
        val body = "INR 381.00 spent using ICICI Bank Card XX5151 on 24-Jun-25 on SWIGGY INSTAMAR. Avl Limit: INR 43,392.94. If not you, call 1800 2662."
        val p = parser.parse("JD-ICICIT-S", body, now)
        assertNotNull(p)
        assertEquals("SWIGGY INSTAMAR", p!!.merchantRaw)
        assertEquals("SWIGGYINSTAMAR", p.merchantNormalized)
    }

    @Test fun `SBI UPI debit parses with merchant and tail`() {
        val body = "Dear UPI user A/C X1010 debited by 35.0 on date 06Aug25 trf to Anil Verma Refno 100000000001. If not u? call 1800111109. -SBI"
        val p = parser.parse("VA-SBIUPI-S", body, now)
        assertNotNull(p)
        assertEquals(3500L, p!!.amountPaise)
        assertEquals(TransactionType.DEBIT, p.type)
        assertEquals(PaymentMode.UPI, p.paymentMode)
        assertEquals("Anil Verma", p.merchantRaw)
        assertEquals("1010", p.accountTail)
    }

    @Test fun `SBI UPI debit large amount`() {
        val body = "Dear UPI user A/C X1010 debited by 40000.00 on date 06Apr26 trf to RAJESH GUPTA Refno 100000000002 If not u? call-1800111109 for other services-18001234-SBI"
        val p = parser.parse("VA-SBIUPI-S", body, now)
        assertNotNull(p)
        assertEquals(40_00_000L, p!!.amountPaise)
        assertEquals("RAJESH GUPTA", p.merchantRaw)
    }

    @Test fun `PayU gateway transaction parses`() {
        val body = "Transaction No. 10000000003 for Rs. 89.00 done for FLASHPE FOODS PRIVATE LIMITED has succeeded Team PayU"
        val p = parser.parse("VM-PAYUIB-S", body, now)
        assertNotNull(p)
        assertEquals(8900L, p!!.amountPaise)
        assertEquals("FLASHPE FOODS PRIVATE LIMITED", p.merchantRaw)
        assertEquals(PaymentMode.UPI, p.paymentMode)
    }

    @Test fun `ICICI FASTag toll parses`() {
        val body = "Rs.20 paid at TSPA for TS07AB1234 on 28-11-2025 20:11:34 with Amazon Pay ICICI Bank FASTag"
        val p = parser.parse("JD-ICICIT-S", body, now)
        assertNotNull(p)
        assertEquals(2000L, p!!.amountPaise)
        assertEquals(TransactionType.DEBIT, p.type)
        assertEquals(FastagTollExtractor.MERCHANT_MARKER, p.merchantNormalized)
        assertTrue(p.merchantRaw!!.contains("TSPA"))
    }

    @Test fun `IndusInd FASTag toll parses`() {
        val body = "Rs.50.00 toll paid at Shamshabad on 20-Dec-2025 08:56:08 PM for MP51XY5678 via FASTag. Bal Rs.260.00. Call 18602108887 to report issue. - IndusInd Bank"
        val p = parser.parse("AX-INDUSB-S", body, now)
        assertNotNull(p)
        assertEquals(5000L, p!!.amountPaise)
        assertEquals(FastagTollExtractor.MERCHANT_MARKER, p!!.merchantNormalized)
    }

    @Test fun `SBI NEFT credit with salary hint flags Income`() {
        val body = "Dear Customer, INR 1,23,456.00 credited to your A/c No XX1010 on 29/09/2025 through NEFT with UTR HDFCH00000000001 by ACME TECH PRIVATE LIMITED, INFO: BATCHID:0004 0001 SALARY-SBI"
        val p = parser.parse("JK-SBIPSG-S", body, now)
        assertNotNull(p)
        assertEquals(TransactionType.CREDIT, p!!.type)
        assertEquals(12_34_56_00L, p.amountPaise)
        assertEquals(SbiNeftCreditExtractor.INCOME_MARKER, p.merchantNormalized)
        assertEquals("ACME TECH PRIVATE LIMITED", p.merchantRaw)
    }

    @Test fun `SBI NEFT credit without salary keeps payer as merchant`() {
        val body = "Dear Customer, INR 45,000.00 credited to your A/c No XX1010 on 20/05/2025 through NEFT with UTR HDFCH00000000002 by GLOBEX AI PVT LTD, INFO: BATCHID:0039 0001 DOMNEFT01 - C00000000000000000 -   -SBI"
        val p = parser.parse("JK-SBIPSG-S", body, now)
        assertNotNull(p)
        assertEquals(TransactionType.CREDIT, p!!.type)
        assertEquals("GLOBEX AI PVT LTD", p.merchantRaw)
        assertNotEquals(SbiNeftCreditExtractor.INCOME_MARKER, p.merchantNormalized)
    }

    @Test fun `ICICI Standing Instructions notice is dropped`() {
        val body = "Dear Customer, your payment of INR 79.00 for Google Play to be debited from your ICICI Bank Credit Card 5151, as per Standing Instructions WZbdkwSksm, is due by 06/01/2026. To cancel this debit..."
        assertNull(parser.parse("JD-ICICIT-S", body, now))
    }

    @Test fun `ICICI credit card statement total due reminder is dropped`() {
        val body = "Pay Total Due of Rs 13,194.75 or Minimum Due Rs 2,210.00 by 30-Aug-25 for ICICI Bank Credit Card XX5151. Delayed/No payments are reported to Credit Bureaus."
        assertNull(parser.parse("JD-ICICIT-S", body, now))
    }

    @Test fun `Credit card statement-sent notice is dropped`() {
        val body = "ICICI Bank Credit Card XX5151 Statement is sent to ab************yz@gmail.com. Total of Rs 13,194.75 or minimum of Rs 2,210.00 is due by 30-AUG-25."
        assertNull(parser.parse("JD-ICICIT-S", body, now))
    }

    @Test fun `Standalone Avl Limit notice is dropped`() {
        val body = "Dear Customer, Your Avl Limit on ICICI Bank Credit Card XX5151 is INR 43,773.94. -ICICI Bank"
        assertNull(parser.parse("JD-ICICIT-S", body, now))
    }

    @Test fun `Wakefit shipping update with paid keyword is dropped`() {
        val body = "Wakefit Duo Plus Rebonded Mattress undelivered. In case you've already paid, the refund will be processed in the next 3-5 working days. Visit us at https://gs.im/WKEFTT/e/tWqIil1BA07"
        assertNull(parser.parse("VM-WKEFTT-S", body, now))
    }

    @Test fun `USD amount in card spend is captured`() {
        val body = "USD 9.99 debited on ICICI Bank Card XX5151 at NETFLIX on 23-Mar-26. Avl Limit: INR 45,000.00."
        val p = parser.parse("JD-ICICIT-S", body, now)
        assertNotNull(p)
        assertEquals("USD", p!!.currency)
        assertEquals(999L, p.amountPaise)
    }

    @Test fun `SBI Card RCS with Unicode bold verbs is normalized and parsed`() {
        // SBI Card's RCS template wraps "spent on your SBI Credit Card ending" / "INFO" in
        // Mathematical Alphanumeric Symbols (U+1D400 block). Without NFKC normalization the
        // filter and the SbiCardSpendExtractor regex don't see the verbs.
        val body = "Rs.485.00 𝐬𝐩𝐞𝐧𝐭 " +
            "𝐨𝐧 𝐲𝐨𝐮𝐫 " +
            "𝐒𝐁𝐈 " +
            "𝐂𝐫𝐞𝐝𝐢𝐭 " +
            "𝐂𝐚𝐫𝐝 " +
            "𝐞𝐧𝐝𝐢𝐧𝐠 " +
            "9999 at SwiggyLimited on 01/06/26. " +
            "𝐈𝐍𝐅𝐎"
        val p = parser.parse(sender = "SBI CARDS AND PAYMENT SERVICES", body = body, receivedAtMillis = now, requireBankSender = false)
        assertNotNull(p)
        assertEquals(48500L, p!!.amountPaise)
        assertEquals(TransactionType.DEBIT, p.type)
        assertEquals(PaymentMode.CARD_CREDIT, p.paymentMode)
        assertEquals("SwiggyLimited", p.merchantRaw)
        assertEquals("9999", p.accountTail)
    }
}
