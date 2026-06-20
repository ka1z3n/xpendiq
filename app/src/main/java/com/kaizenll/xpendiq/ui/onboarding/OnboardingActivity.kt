package com.kaizenll.xpendiq.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kaizenll.xpendiq.MainActivity
import com.kaizenll.xpendiq.R
import com.kaizenll.xpendiq.util.NotificationAccess
import com.kaizenll.xpendiq.util.Preferences
import com.kaizenll.xpendiq.util.SmsPermissions
import com.kaizenll.xpendiq.work.BackfillWorker
import com.google.android.material.button.MaterialButton
import java.time.LocalDate
import java.time.ZoneId

class OnboardingActivity : AppCompatActivity() {

    // Set when we send the user to the notification-access settings screen, so the next onResume
    // knows to carry on with the backfill prompt rather than waiting for a result that never comes
    // (notification access has no runtime-permission callback).
    private var pendingNotifContinue = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        if (SmsPermissions.smsGranted(this)) {
            promptNotificationAccessThenBackfill()
        }
        // If denied, user can retry via the button, or use Skip.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_onboarding)

        val pager = findViewById<ViewPager2>(R.id.pager)
        val dot1 = findViewById<View>(R.id.dot1)
        val dot2 = findViewById<View>(R.id.dot2)
        val primary = findViewById<MaterialButton>(R.id.btn_primary)
        val skip = findViewById<MaterialButton>(R.id.btn_skip)

        pager.adapter = OnboardingPagerAdapter(this)
        dot1.isSelected = true

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                dot1.isSelected = position == 0
                dot2.isSelected = position == 1
                primary.setText(if (position == 0) R.string.onb_continue else R.string.onb_allow)
                skip.visibility = if (position == 1) View.VISIBLE else View.INVISIBLE
            }
        })

        primary.setOnClickListener {
            if (pager.currentItem == 0) {
                pager.currentItem = 1
            } else {
                if (SmsPermissions.smsGranted(this)) {
                    promptNotificationAccessThenBackfill()
                } else {
                    permissionLauncher.launch(SmsPermissions.required)
                }
            }
        }

        skip.setOnClickListener { finishOnboarding(runBackfill = false) }
    }

    override fun onResume() {
        super.onResume()
        if (pendingNotifContinue) {
            pendingNotifContinue = false
            promptBackfillThenFinish()
        }
    }

    /**
     * Notification access powers RCS-only bank alerts. It has no runtime dialog, so we explain it
     * and bounce the user to the system listener-settings screen; [onResume] resumes the flow when
     * they come back. Already-granted or "Not now" goes straight to the backfill prompt.
     */
    private fun promptNotificationAccessThenBackfill() {
        if (NotificationAccess.isGranted(this)) {
            promptBackfillThenFinish()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.onb_notif_title)
            .setMessage(R.string.onb_notif_msg)
            .setNegativeButton(R.string.onb_notif_skip) { _, _ -> promptBackfillThenFinish() }
            .setPositiveButton(R.string.onb_notif_enable) { _, _ ->
                pendingNotifContinue = true
                NotificationAccess.openSettings(this)
            }
            .setCancelable(false)
            .show()
    }

    private fun promptBackfillThenFinish() {
        AlertDialog.Builder(this)
            .setTitle(R.string.onb_backfill_title)
            .setMessage(R.string.onb_backfill_msg)
            .setNegativeButton(R.string.onb_backfill_skip) { _, _ ->
                finishOnboarding(runBackfill = false)
            }
            .setPositiveButton(R.string.onb_backfill_run) { _, _ ->
                finishOnboarding(runBackfill = true)
            }
            .setCancelable(false)
            .show()
    }

    private fun finishOnboarding(runBackfill: Boolean) {
        if (runBackfill && SmsPermissions.smsGranted(this)) {
            // First run imports the current calendar month only (from the 1st). Older history is
            // unverified and would inflate the uncategorized pile, so it stays an explicit opt-in
            // via the 90-day Settings backfill.
            val monthStart = LocalDate.now()
                .withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            WorkManager.getInstance(this).enqueueUniqueWork(
                BackfillWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<BackfillWorker>()
                    .addTag(BackfillWorker.TAG)
                    .setInputData(workDataOf(BackfillWorker.KEY_CUTOFF_MILLIS to monthStart))
                    .build(),
            )
        }
        Preferences.setOnboardingComplete(this)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
