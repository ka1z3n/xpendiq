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

    /**
     * Persist a new USD→INR rate and re-freeze the INR-equivalent on every existing USD row, so
     * past foreign transactions immediately fold into totals at the new rate.
     */
    suspend fun setUsdInrRate(rate: Double): Int {
        Preferences.setUsdInrRate(getApplication(), rate)
        return database.transactionDao()
            .recomputeInrForCurrency("USD", rate, System.currentTimeMillis())
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
