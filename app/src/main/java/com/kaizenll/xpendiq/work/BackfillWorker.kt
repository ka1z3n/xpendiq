package com.kaizenll.xpendiq.work

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kaizenll.xpendiq.XpendiqApplication
import java.util.concurrent.TimeUnit

class BackfillWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? XpendiqApplication ?: return Result.failure()
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(BACKFILL_DAYS)

        val cursor = applicationContext.contentResolver.query(
            Uri.parse("content://sms/inbox"),
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
            ),
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(cutoff.toString()),
            "${Telephony.Sms.DATE} ASC",
        ) ?: return Result.failure()

        var processed = 0
        var saved = 0
        cursor.use {
            val idIdx = it.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addrIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (it.moveToNext()) {
                val smsId = it.getLong(idIdx)
                val sender = it.getString(addrIdx) ?: continue
                val body = it.getString(bodyIdx) ?: continue
                val date = it.getLong(dateIdx)
                val result = app.repository.ingestSms(sender, body, date, smsId)
                processed++
                if (result == com.kaizenll.xpendiq.data.repo.TransactionRepository.IngestResult.SAVED) saved++
                if (processed % PROGRESS_EVERY == 0) {
                    setProgress(workDataOf(PROGRESS_PROCESSED to processed, PROGRESS_SAVED to saved))
                }
            }
        }
        setProgress(workDataOf(PROGRESS_PROCESSED to processed, PROGRESS_SAVED to saved))
        return Result.success(workDataOf(RESULT_PROCESSED to processed, RESULT_SAVED to saved))
    }

    companion object {
        const val BACKFILL_DAYS = 90L
        const val TAG = "xpendiq_backfill"
        const val UNIQUE_WORK_NAME = "xpendiq_backfill_once"
        const val PROGRESS_PROCESSED = "processed"
        const val PROGRESS_SAVED = "saved"
        const val RESULT_PROCESSED = "processed"
        const val RESULT_SAVED = "saved"
        private const val PROGRESS_EVERY = 50
    }
}
