package com.example.spendy.data.db

import android.util.Log
import com.example.spendy.data.entity.DeletedSmsHash
import com.example.spendy.parser.SmsParser

/**
 * Runs today's parser pipeline over every existing transaction. Anything the current parser
 * (with its latest filters) would reject gets deleted and its SMS hash is recorded in
 * [DeletedSmsHash] so a future backfill scan won't recreate it.
 *
 * Respects user edits: rows with `isUserEdited = true` are never touched, since the user has
 * explicitly confirmed/corrected them.
 *
 * Idempotent — after the first sweep, future runs find nothing to delete.
 */
object StaleTransactionCleanup {

    private const val TAG = "StaleTxnCleanup"

    suspend fun run(db: SpendyDatabase, parser: SmsParser) {
        val txnDao = db.transactionDao()
        val deletedDao = db.deletedSmsHashDao()
        val now = System.currentTimeMillis()
        var deletedCount = 0

        for (txn in txnDao.findAll()) {
            if (txn.isUserEdited) continue
            val body = txn.smsBody ?: continue
            val sender = txn.sender ?: continue
            val reparsed = parser.parse(sender, body, txn.occurredAt)
            if (reparsed == null) {
                txnDao.deleteById(txn.id)
                deletedDao.insert(DeletedSmsHash(txn.smsBodyHash, now))
                deletedCount++
            }
        }
        if (deletedCount > 0) Log.i(TAG, "Removed $deletedCount stale transactions")
    }
}
