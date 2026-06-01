package com.kaizenll.xpendiq.data.db

import android.util.Log
import com.kaizenll.xpendiq.util.Hashing

/**
 * One-shot, idempotent migration that fixes a class of duplicate rows produced when the
 * parser was upgraded to understand a new format (e.g. USD support). The old hash formula
 * incorporated the parser-derived `amountPaise + occurredAt`, so the same SMS parsed by two
 * different parser versions produced two rows with different hashes.
 *
 * Logic:
 *  - Group existing transactions by (sender, smsBody.trim()).
 *  - Within each group, keep the row with the highest id (latest insertion = latest parser).
 *  - Delete the others.
 *  - Re-hash the survivor with [Hashing.transactionHash] so subsequent ingestions dedupe.
 *
 * Safe to run on every app start.
 */
object HashRehashMigration {

    private const val TAG = "HashRehash"

    suspend fun run(db: XpendiqDatabase) {
        val txnDao = db.transactionDao()
        val all = txnDao.findAll()
        val groups = all
            .filter { !it.sender.isNullOrBlank() && !it.smsBody.isNullOrBlank() }
            .groupBy { (it.sender ?: "") to (it.smsBody ?: "").trim() }

        var deleted = 0
        var rehashed = 0
        for ((key, rows) in groups) {
            val (sender, body) = key
            val newHash = Hashing.transactionHash(sender, body)
            val sorted = rows.sortedByDescending { it.id }
            val keep = sorted.first()
            for (drop in sorted.drop(1)) {
                txnDao.deleteById(drop.id)
                deleted++
            }
            if (keep.smsBodyHash != newHash) {
                txnDao.updateHash(keep.id, newHash)
                rehashed++
            }
        }
        if (deleted > 0 || rehashed > 0) {
            Log.i(TAG, "Removed $deleted duplicate rows, re-hashed $rehashed survivors")
        }
    }
}
