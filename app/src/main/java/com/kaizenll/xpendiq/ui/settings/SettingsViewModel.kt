package com.kaizenll.xpendiq.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.kaizenll.xpendiq.work.BackfillWorker
import kotlinx.coroutines.flow.Flow

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val workManager: WorkManager = WorkManager.getInstance(app)

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
