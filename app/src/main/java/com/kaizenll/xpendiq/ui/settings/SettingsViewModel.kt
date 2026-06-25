package com.kaizenll.xpendiq.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.kaizenll.xpendiq.XpendiqApplication
import com.kaizenll.xpendiq.data.io.TransactionBackup
import com.kaizenll.xpendiq.util.Preferences
import com.kaizenll.xpendiq.work.BackfillWorker
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val workManager: WorkManager = WorkManager.getInstance(app)
    private val database = (app as XpendiqApplication).database

    /** Total category count, for the Manage categories row subtitle. */
    suspend fun categoryCount(): Int = database.categoryDao().count()

    /** Current manual USD→INR rate, or null when unset. */
    fun usdInrRate(): Double? = Preferences.getUsdInrRate(getApplication())

    /** Epoch millis the rate was last set (0 if never), for the staleness reminder. */
    fun usdInrRateSetAt(): Long = Preferences.getUsdInrRateSetAt(getApplication())

    /** How many foreign (USD) transactions exist — gates the "update past?" prompt and reminder. */
    suspend fun foreignTxnCount(): Int = database.transactionDao().countByCurrency("USD")

    /**
     * Persist a new USD→INR rate, stamping the time so the UI can flag a stale rate later. New
     * captures always freeze at the latest rate. When [applyToPast] is true, also re-freezes every
     * existing USD row at this rate (a correction) and returns how many were updated; otherwise
     * past rows keep their captured values — the rate simply moved — and 0 is returned.
     */
    suspend fun setUsdInrRate(rate: Double, applyToPast: Boolean): Int {
        val now = System.currentTimeMillis()
        Preferences.setUsdInrRate(getApplication(), rate, now)
        return if (applyToPast) {
            database.transactionDao().recomputeInrForCurrency("USD", rate, now)
        } else {
            0
        }
    }

    /** Write the full transaction history to [out] as CSV; returns the row count. */
    suspend fun exportCsv(out: OutputStream): Int = TransactionBackup.export(database, out)

    /** Merge a CSV backup from [input]; returns added / skipped / failed counts. */
    suspend fun importCsv(input: InputStream): TransactionBackup.ImportResult =
        TransactionBackup.import(database, input)

    val backfillStatus: Flow<List<WorkInfo>> =
        workManager.getWorkInfosForUniqueWorkFlow(BackfillWorker.UNIQUE_WORK_NAME)

    fun startBackfill() {
        val req = OneTimeWorkRequestBuilder<BackfillWorker>()
            .addTag(BackfillWorker.TAG)
            .build()
        workManager.enqueueUniqueWork(
            BackfillWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            req,
        )
    }
}
