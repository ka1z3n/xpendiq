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
import com.kaizenll.xpendiq.MainActivity
import com.kaizenll.xpendiq.R
import com.kaizenll.xpendiq.util.Preferences
import com.kaizenll.xpendiq.util.SmsPermissions
import com.kaizenll.xpendiq.work.BackfillWorker
import com.google.android.material.button.MaterialButton

class OnboardingActivity : AppCompatActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        if (SmsPermissions.smsGranted(this)) {
            promptBackfillThenFinish()
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
                    promptBackfillThenFinish()
                } else {
                    permissionLauncher.launch(SmsPermissions.required)
                }
            }
        }

        skip.setOnClickListener { finishOnboarding(runBackfill = false) }
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
            WorkManager.getInstance(this).enqueueUniqueWork(
                BackfillWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<BackfillWorker>()
                    .addTag(BackfillWorker.TAG)
                    .build(),
            )
        }
        Preferences.setOnboardingComplete(this)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
