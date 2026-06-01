package com.kaizenll.xpendiq.data.db

import androidx.room.TypeConverter
import com.kaizenll.xpendiq.data.entity.MerchantRuleSource
import com.kaizenll.xpendiq.data.entity.PaymentMode
import com.kaizenll.xpendiq.data.entity.TransactionType

class Converters {
    @TypeConverter fun txnTypeToString(v: TransactionType): String = v.name
    @TypeConverter fun stringToTxnType(s: String): TransactionType = TransactionType.valueOf(s)

    @TypeConverter fun modeToString(v: PaymentMode): String = v.name
    @TypeConverter fun stringToMode(s: String): PaymentMode = PaymentMode.valueOf(s)

    @TypeConverter fun ruleSourceToString(v: MerchantRuleSource): String = v.name
    @TypeConverter fun stringToRuleSource(s: String): MerchantRuleSource = MerchantRuleSource.valueOf(s)
}
