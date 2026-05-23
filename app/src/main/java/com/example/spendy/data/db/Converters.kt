package com.example.spendy.data.db

import androidx.room.TypeConverter
import com.example.spendy.data.entity.MerchantRuleSource
import com.example.spendy.data.entity.PaymentMode
import com.example.spendy.data.entity.TransactionType

class Converters {
    @TypeConverter fun txnTypeToString(v: TransactionType): String = v.name
    @TypeConverter fun stringToTxnType(s: String): TransactionType = TransactionType.valueOf(s)

    @TypeConverter fun modeToString(v: PaymentMode): String = v.name
    @TypeConverter fun stringToMode(s: String): PaymentMode = PaymentMode.valueOf(s)

    @TypeConverter fun ruleSourceToString(v: MerchantRuleSource): String = v.name
    @TypeConverter fun stringToRuleSource(s: String): MerchantRuleSource = MerchantRuleSource.valueOf(s)
}
