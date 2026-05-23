package com.example.spendy.data.db

import android.util.Log
import com.example.spendy.data.entity.TransactionType

/**
 * Brings seeded categories' colours on existing installs in line with [CategoryPalette]. Only
 * touches rows whose `colorHex` still matches one of the old generic defaults — so user choices
 * made via Manage Categories are preserved.
 *
 * Idempotent.
 */
object ColorPaletteMigration {

    private const val TAG = "ColorPaletteMig"

    /** Colours used by earlier versions of the seeder. A row at any of these is fair game. */
    private val OLD_DEFAULTS = setOf(
        "#607D8B",  // old DEBIT default (blue grey, all spends)
        "#4CAF50",  // old CREDIT default (green, all credits)
    )

    suspend fun run(db: SpendyDatabase) {
        val dao = db.categoryDao()
        var updated = 0
        for (type in TransactionType.values()) {
            val palette = when (type) {
                TransactionType.DEBIT -> CategoryPalette.DEBIT
                TransactionType.CREDIT -> CategoryPalette.CREDIT
                TransactionType.INVESTMENT -> CategoryPalette.INVESTMENT
            }
            for ((name, newColor) in palette) {
                val cat = dao.findByNameAndType(name, type) ?: continue
                if (cat.colorHex == newColor) continue           // already at target
                if (cat.colorHex !in OLD_DEFAULTS) continue       // user-customised, leave alone
                dao.update(cat.copy(colorHex = newColor))
                updated++
            }
        }
        if (updated > 0) Log.i(TAG, "Refreshed colors on $updated categories")
    }
}
