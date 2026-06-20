package com.kaizenll.xpendiq.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kaizenll.xpendiq.BuildConfig
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
            promptNotificationAccessThenContinue()
        }
        // If denied, user can retry via the button, or use Skip.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_onboarding)

        val pager = findViewById<ViewPager2>(R.id.pager)
        val dots = findViewById<LinearLayout>(R.id.dots)
        val primary = findViewById<MaterialButton>(R.id.btn_primary)
        val skip = findViewById<MaterialButton>(R.id.btn_skip)

        pager.adapter = OnboardingPagerAdapter(this)
        val pageCount = pager.adapter!!.itemCount
        buildDots(dots, pageCount)
        updateChrome(0, pageCount, dots, primary, skip)

        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateChrome(position, pageCount, dots, primary, skip)
            }
        })

        primary.setOnClickListener {
            val lastPage = pageCount - 1
            if (pager.currentItem < lastPage) {
                pager.currentItem += 1
            } else if (!BuildConfig.SMS_ENABLED) {
                // Notification-only flavor: there's no SMS to request — go straight to the
                // notification-access ask, which is how this build captures transactions.
                promptNotificationAccessThenContinue()
            } else if (SmsPermissions.smsGranted(this)) {
                promptNotificationAccessThenContinue()
            } else {
                permissionLauncher.launch(SmsPermissions.required)
            }
        }

        skip.setOnClickListener { finishOnboarding(runBackfill = false) }
    }

    /** One tappable dot per page; selection highlights the current one. */
    private fun buildDots(container: LinearLayout, count: Int) {
        val size = dp(10)
        val margin = dp(4)
        repeat(count) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    setMargins(margin, margin, margin, margin)
                }
                setBackgroundResource(R.drawable.dot_indicator)
            }
            container.addView(dot)
        }
    }

    /** The Skip link and Allow button only appear on the final (permission) page. */
    private fun updateChrome(
        position: Int,
        count: Int,
        dots: LinearLayout,
        primary: MaterialButton,
        skip: MaterialButton,
    ) {
        for (i in 0 until dots.childCount) dots.getChildAt(i).isSelected = (i == position)
        val onLastPage = position == count - 1
        primary.setText(if (onLastPage) R.string.onb_allow else R.string.onb_continue)
        skip.visibility = if (onLastPage) View.VISIBLE else View.INVISIBLE
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics,
        ).toInt()

    override fun onResume() {
        super.onResume()
        if (pendingNotifContinue) {
            pendingNotifContinue = false
            continueAfterNotificationAccess()
        }
    }

    /**
     * Notification access powers RCS-only bank alerts (and is the sole capture path on the Play
     * flavor). It has no runtime dialog, so we explain it and bounce the user to the system
     * listener-settings screen; [onResume] resumes the flow when they come back. Already-granted or
     * "Not now" continues immediately.
     */
    private fun promptNotificationAccessThenContinue() {
        if (NotificationAccess.isGranted(this)) {
            continueAfterNotificationAccess()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.onb_notif_title)
            .setMessage(R.string.onb_notif_msg)
            .setNegativeButton(R.string.onb_notif_skip) { _, _ -> continueAfterNotificationAccess() }
            .setPositiveButton(R.string.onb_notif_enable) { _, _ ->
                pendingNotifContinue = true
                NotificationAccess.openSettings(this)
            }
            .setCancelable(false)
            .show()
    }

    /** Backfill is SMS-only, so the notification-only flavor finishes straight after notif access. */
    private fun continueAfterNotificationAccess() {
        if (BuildConfig.SMS_ENABLED) promptBackfillThenFinish() else finishOnboarding(runBackfill = false)
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
