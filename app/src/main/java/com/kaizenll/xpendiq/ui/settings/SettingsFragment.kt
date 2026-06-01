package com.kaizenll.xpendiq.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.work.WorkInfo
import com.kaizenll.xpendiq.R
import com.kaizenll.xpendiq.util.NotificationAccess
import com.kaizenll.xpendiq.util.SmsPermissions
import com.kaizenll.xpendiq.work.BackfillWorker
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val viewModel: SettingsViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = SmsPermissions.smsGranted(requireContext())
        refreshPermissionStatus()
        if (!granted && results.isNotEmpty() &&
            !SmsPermissions.shouldShowRationale(requireActivity())
        ) {
            // User selected "Don't ask again" — direct them to system settings.
            openAppSettings()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialButton>(R.id.grant_btn).setOnClickListener {
            permissionLauncher.launch(SmsPermissions.required)
        }

        view.findViewById<MaterialButton>(R.id.backfill_btn).setOnClickListener {
            if (!SmsPermissions.smsGranted(requireContext())) {
                Snackbar.make(view, R.string.settings_need_permission_first, Snackbar.LENGTH_SHORT).show()
                permissionLauncher.launch(SmsPermissions.required)
                return@setOnClickListener
            }
            confirmAndStart()
        }

        view.findViewById<MaterialButton>(R.id.manage_categories_btn).setOnClickListener {
            findNavController().navigate(R.id.categoriesFragment)
        }

        view.findViewById<MaterialButton>(R.id.notif_grant_btn).setOnClickListener {
            NotificationAccess.openSettings(requireContext())
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.backfillStatus.collect { infos ->
                    renderBackfillStatus(view, infos)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
        refreshNotificationAccessStatus()
    }

    private fun refreshPermissionStatus() {
        val view = view ?: return
        val granted = SmsPermissions.smsGranted(requireContext())
        view.findViewById<TextView>(R.id.permission_status).setText(
            if (granted) R.string.settings_permission_granted
            else R.string.settings_permission_missing
        )
        view.findViewById<MaterialButton>(R.id.grant_btn).visibility =
            if (granted) View.GONE else View.VISIBLE
        view.findViewById<MaterialButton>(R.id.backfill_btn).isEnabled = granted
    }

    private fun refreshNotificationAccessStatus() {
        val view = view ?: return
        val granted = NotificationAccess.isGranted(requireContext())
        view.findViewById<TextView>(R.id.notif_status).setText(
            if (granted) R.string.settings_notif_granted
            else R.string.settings_notif_missing
        )
        view.findViewById<MaterialButton>(R.id.notif_grant_btn).visibility =
            if (granted) View.GONE else View.VISIBLE
    }

    private fun confirmAndStart() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_backfill_confirm_title)
            .setMessage(R.string.settings_backfill_confirm_msg)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.settings_run_backfill) { _, _ -> viewModel.startBackfill() }
            .show()
    }

    private fun renderBackfillStatus(view: View, infos: List<WorkInfo>) {
        val statusView = view.findViewById<TextView>(R.id.backfill_status)
        val btn = view.findViewById<MaterialButton>(R.id.backfill_btn)
        val info = infos.lastOrNull()
        if (info == null) {
            statusView.visibility = View.GONE
            btn.isEnabled = SmsPermissions.smsGranted(requireContext())
            return
        }

        val processed = info.progress.getInt(BackfillWorker.PROGRESS_PROCESSED, 0)
            .takeIf { it > 0 }
            ?: info.outputData.getInt(BackfillWorker.RESULT_PROCESSED, 0)
        val saved = info.progress.getInt(BackfillWorker.PROGRESS_SAVED, 0)
            .takeIf { it > 0 }
            ?: info.outputData.getInt(BackfillWorker.RESULT_SAVED, 0)

        statusView.visibility = View.VISIBLE
        when (info.state) {
            WorkInfo.State.ENQUEUED -> {
                statusView.setText(R.string.settings_backfill_enqueued)
                btn.isEnabled = false
            }
            WorkInfo.State.RUNNING -> {
                statusView.text = getString(R.string.settings_backfill_running, processed, saved)
                btn.isEnabled = false
            }
            WorkInfo.State.SUCCEEDED -> {
                statusView.text = getString(R.string.settings_backfill_done, processed, saved)
                btn.isEnabled = SmsPermissions.smsGranted(requireContext())
            }
            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                statusView.setText(R.string.settings_backfill_failed)
                btn.isEnabled = SmsPermissions.smsGranted(requireContext())
            }
            WorkInfo.State.BLOCKED -> {
                statusView.setText(R.string.settings_backfill_enqueued)
                btn.isEnabled = false
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", requireContext().packageName, null)
        }
        startActivity(intent)
    }
}
