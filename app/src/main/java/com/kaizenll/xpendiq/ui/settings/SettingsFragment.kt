package com.kaizenll.xpendiq.ui.settings

import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import java.util.Locale
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.work.WorkInfo
import com.kaizenll.xpendiq.BuildConfig
import com.kaizenll.xpendiq.R
import com.kaizenll.xpendiq.XpendiqApplication
import com.kaizenll.xpendiq.entitlement.EntitlementState
import com.kaizenll.xpendiq.util.AppLock
import com.kaizenll.xpendiq.util.NotificationAccess
import com.kaizenll.xpendiq.util.Preferences
import com.kaizenll.xpendiq.util.SmsPermissions
import com.kaizenll.xpendiq.work.BackfillWorker
import com.google.android.material.color.MaterialColors
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import java.time.LocalDate
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val viewModel: SettingsViewModel by viewModels()

    // SAF: pick where to write the CSV / which CSV to read back.
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> if (uri != null) runExport(uri) }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) confirmImport(uri) }

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

        if (!BuildConfig.SMS_ENABLED) {
            // Notification-only flavor: no SMS access row, no SMS backfill. Notification access
            // (the capture path here) and Manage categories remain in their cards.
            view.findViewById<View>(R.id.sms_row).visibility = View.GONE
            view.findViewById<View>(R.id.permissions_divider).visibility = View.GONE
            view.findViewById<View>(R.id.backfill_row).visibility = View.GONE
            view.findViewById<View>(R.id.data_divider).visibility = View.GONE
        }

        view.findViewById<View>(R.id.sms_row).setOnClickListener {
            if (SmsPermissions.smsGranted(requireContext())) openAppSettings()
            else permissionLauncher.launch(SmsPermissions.required)
        }

        view.findViewById<View>(R.id.notif_row).setOnClickListener {
            NotificationAccess.openSettings(requireContext())
        }

        val runBackfill = View.OnClickListener {
            if (!SmsPermissions.smsGranted(requireContext())) {
                Snackbar.make(view, R.string.settings_need_permission_first, Snackbar.LENGTH_SHORT).show()
                permissionLauncher.launch(SmsPermissions.required)
                return@OnClickListener
            }
            confirmAndStart()
        }
        view.findViewById<View>(R.id.backfill_btn).setOnClickListener(runBackfill)
        view.findViewById<View>(R.id.backfill_row).setOnClickListener(runBackfill)

        view.findViewById<View>(R.id.manage_categories_row).setOnClickListener {
            findNavController().navigate(R.id.categoriesFragment)
        }

        view.findViewById<View>(R.id.fx_row).setOnClickListener { showFxRateDialog() }

        view.findViewById<View>(R.id.report_row).setOnClickListener {
            findNavController().navigate(R.id.reportIssueFragment)
        }

        view.findViewById<View>(R.id.source_row).setOnClickListener {
            openUrl(getString(R.string.url_source))
        }

        view.findViewById<View>(R.id.privacy_row).setOnClickListener {
            openUrl(getString(R.string.url_privacy))
        }

        setupDebugEntitlement(view)

        view.findViewById<View>(R.id.export_row).setOnClickListener {
            exportLauncher.launch("xpendiq-backup-${LocalDate.now()}.csv")
        }

        view.findViewById<View>(R.id.import_row).setOnClickListener {
            // "*/*" so a .csv is never greyed out by an odd MIME mapping on the device.
            importLauncher.launch(arrayOf("*/*"))
        }

        setupAppLock(view)

        val versionName = runCatching {
            requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0).versionName
        }.getOrNull() ?: "—"
        view.findViewById<TextView>(R.id.version_text).text =
            getString(R.string.settings_version, versionName)

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
        refreshCategoryCount()
        refreshFxRate()
    }

    private fun refreshFxRate() {
        val subtitle = view?.findViewById<TextView>(R.id.fx_subtitle) ?: return
        val rate = viewModel.usdInrRate()
        if (rate == null) {
            subtitle.text = getString(R.string.settings_fx_unset)
            return
        }
        val base = getString(R.string.settings_fx_value, formatRate(rate))
        subtitle.text = base // immediate; the age suffix (if any) lands once the count loads
        val setAt = viewModel.usdInrRateSetAt()
        if (setAt <= 0L) return
        val days = ((System.currentTimeMillis() - setAt) / DAY_MILLIS).toInt().coerceAtLeast(0)
        viewLifecycleOwner.lifecycleScope.launch {
            // Only nag users who actually have foreign spends — otherwise the rate's age is noise.
            if (viewModel.foreignTxnCount() == 0) return@launch
            val tv = view?.findViewById<TextView>(R.id.fx_subtitle) ?: return@launch
            val suffix = if (days >= FX_STALE_DAYS) {
                resources.getQuantityString(R.plurals.settings_fx_stale, days, days)
            } else {
                resources.getQuantityString(R.plurals.settings_fx_age, days, days)
            }
            tv.text = "$base · $suffix"
        }
    }

    /** Trim a trailing ".00" so 83.0 reads "83" but 83.50 stays "83.50". */
    private fun formatRate(rate: Double): String {
        val s = String.format(Locale.US, "%.2f", rate)
        return s.removeSuffix(".00")
    }

    private fun showFxRateDialog() {
        val current = viewModel.usdInrRate()
        val input = EditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(R.string.settings_fx_dialog_hint)
            if (current != null) setText(formatRate(current))
        }
        val container = FrameLayout(requireContext()).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, 0, pad, 0)
            addView(input)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_fx_dialog_title)
            .setMessage(R.string.settings_fx_dialog_msg)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val rate = input.text.toString().trim().toDoubleOrNull()
                if (rate == null || rate <= 0.0) {
                    Snackbar.make(requireView(), R.string.settings_fx_dialog_hint, Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                onFxRateEntered(rate, previous = current)
            }
            .show()
    }

    private fun onFxRateEntered(rate: Double, previous: Double?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val foreignCount = viewModel.foreignTxnCount()
            // Changing an existing rate with past foreign rows: ask whether to restate history
            // (a rate fix) or leave it frozen (the rate simply moved). First-ever rate has nothing
            // frozen yet, and no-change / no-foreign cases have nothing to restate — just save.
            if (previous != null && rate != previous && foreignCount > 0) {
                showApplyToPastDialog(rate, foreignCount)
            } else {
                saveFxRate(rate, applyToPast = previous == null)
            }
        }
    }

    private fun showApplyToPastDialog(rate: Double, foreignCount: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_fx_apply_title)
            .setMessage(getString(R.string.settings_fx_apply_msg, foreignCount, formatRate(rate)))
            // "Keep existing" is the safe default (positive), so a rate that simply moved doesn't
            // silently rewrite history; "Update all" is the correction path.
            .setPositiveButton(R.string.settings_fx_apply_keep) { _, _ -> saveFxRate(rate, applyToPast = false) }
            .setNegativeButton(R.string.settings_fx_apply_all) { _, _ -> saveFxRate(rate, applyToPast = true) }
            .show()
    }

    private fun saveFxRate(rate: Double, applyToPast: Boolean) {
        viewLifecycleOwner.lifecycleScope.launch {
            val count = viewModel.setUsdInrRate(rate, applyToPast)
            refreshFxRate()
            view?.let {
                val msg = if (count > 0) getString(R.string.settings_fx_saved, count)
                else getString(R.string.settings_fx_saved_future)
                Snackbar.make(it, msg, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshPermissionStatus() {
        val view = view ?: return
        val granted = SmsPermissions.smsGranted(requireContext())
        bindStatus(view.findViewById(R.id.permission_status), granted)
        setRunEnabled(view.findViewById(R.id.backfill_btn), granted)
    }

    private fun refreshNotificationAccessStatus() {
        val view = view ?: return
        bindStatus(view.findViewById(R.id.notif_status), NotificationAccess.isGranted(requireContext()))
    }

    private fun refreshCategoryCount() {
        viewLifecycleOwner.lifecycleScope.launch {
            val count = viewModel.categoryCount()
            val subtitle = view?.findViewById<TextView>(R.id.categories_subtitle) ?: return@launch
            subtitle.text = resources.getQuantityString(R.plurals.settings_categories_count, count, count)
        }
    }

    /** Inline status: a coloured dot + "Granted" (green) / "Not granted" (error). */
    private fun bindStatus(status: TextView, granted: Boolean) {
        val color = if (granted) {
            ContextCompat.getColor(status.context, R.color.money_credit)
        } else {
            MaterialColors.getColor(status, com.google.android.material.R.attr.colorError)
        }
        status.setText(if (granted) R.string.settings_status_granted else R.string.settings_status_missing)
        status.setTextColor(color)
        status.compoundDrawableTintList = ColorStateList.valueOf(color)
    }

    private fun setRunEnabled(runBtn: TextView, enabled: Boolean) {
        runBtn.isEnabled = enabled
        runBtn.alpha = if (enabled) 1f else 0.4f
    }

    private fun confirmAndStart() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_backfill_confirm_title)
            .setMessage(R.string.settings_backfill_confirm_msg)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.settings_run_backfill) { _, _ -> viewModel.startBackfill() }
            .show()
    }

    private fun setupAppLock(view: View) {
        val switch = view.findViewById<MaterialSwitch>(R.id.app_lock_switch)
        switch.isChecked = Preferences.isAppLockEnabled(requireContext())
        switch.setOnCheckedChangeListener { _, checked ->
            if (checked && !canAuthenticate()) {
                // No biometric/screen lock on the device — can't enable. Revert and explain.
                switch.isChecked = false
                Snackbar.make(view, R.string.settings_app_lock_no_credential, Snackbar.LENGTH_LONG).show()
                return@setOnCheckedChangeListener
            }
            Preferences.setAppLockEnabled(requireContext(), checked)
            // The current session is clearly the owner; don't prompt until the app is next reopened.
            AppLock.isUnlocked = true
            applySecureFlag(checked)
        }
        // The whole row toggles the switch.
        view.findViewById<View>(R.id.app_lock_row).setOnClickListener { switch.toggle() }
    }

    private fun canAuthenticate(): Boolean =
        BiometricManager.from(requireContext())
            .canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS

    private fun applySecureFlag(secure: Boolean) {
        val window = activity?.window ?: return
        if (secure) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun runExport(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val count = runCatching {
                requireContext().contentResolver.openOutputStream(uri)?.let { viewModel.exportCsv(it) }
            }.getOrNull()
            val v = view ?: return@launch
            if (count != null) {
                val msg = getString(R.string.settings_export_done, count)
                showBackupStatus(msg)
                Snackbar.make(v, msg, Snackbar.LENGTH_LONG).show()
            } else {
                Snackbar.make(v, R.string.settings_export_failed, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun confirmImport(uri: Uri) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_import_confirm_title)
            .setMessage(R.string.settings_import_confirm_msg)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.settings_import) { _, _ -> runImport(uri) }
            .show()
    }

    private fun runImport(uri: Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                requireContext().contentResolver.openInputStream(uri)?.use { viewModel.importCsv(it) }
            }.getOrNull()
            val v = view ?: return@launch
            if (result == null) {
                Snackbar.make(v, R.string.settings_import_failed, Snackbar.LENGTH_LONG).show()
                return@launch
            }
            val msg = if (result.failed > 0) {
                getString(R.string.settings_import_done_errors, result.imported, result.skipped, result.failed)
            } else {
                getString(R.string.settings_import_done, result.imported, result.skipped)
            }
            val statusMsg = if (result.categories > 0 || result.rules > 0) {
                msg + "\n" + getString(R.string.settings_import_also, result.categories, result.rules)
            } else {
                msg
            }
            showBackupStatus(statusMsg)
            refreshCategoryCount()
            Snackbar.make(v, msg, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun showBackupStatus(text: String) {
        val statusView = view?.findViewById<TextView>(R.id.backup_status) ?: return
        statusView.text = text
        statusView.visibility = View.VISIBLE
    }

    private fun renderBackfillStatus(view: View, infos: List<WorkInfo>) {
        val subtitle = view.findViewById<TextView>(R.id.backfill_subtitle)
        val runBtn = view.findViewById<TextView>(R.id.backfill_btn)
        val granted = SmsPermissions.smsGranted(requireContext())
        val info = infos.lastOrNull()

        if (info == null) {
            subtitle.text = getString(R.string.settings_backfill_subtitle, 0)
            setRunEnabled(runBtn, granted)
            return
        }

        val processed = info.progress.getInt(BackfillWorker.PROGRESS_PROCESSED, 0)
            .takeIf { it > 0 }
            ?: info.outputData.getInt(BackfillWorker.RESULT_PROCESSED, 0)
        val saved = info.progress.getInt(BackfillWorker.PROGRESS_SAVED, 0)
            .takeIf { it > 0 }
            ?: info.outputData.getInt(BackfillWorker.RESULT_SAVED, 0)

        when (info.state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> {
                subtitle.setText(R.string.settings_backfill_enqueued)
                setRunEnabled(runBtn, false)
            }
            WorkInfo.State.RUNNING -> {
                subtitle.text = getString(R.string.settings_backfill_running, processed, saved)
                setRunEnabled(runBtn, false)
            }
            WorkInfo.State.SUCCEEDED -> {
                subtitle.text = getString(R.string.settings_backfill_subtitle, saved)
                setRunEnabled(runBtn, granted)
            }
            WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                subtitle.setText(R.string.settings_backfill_failed)
                setRunEnabled(runBtn, granted)
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", requireContext().packageName, null)
        }
        startActivity(intent)
    }

    /** Open an external link (source repo / privacy policy); no-op if no browser can handle it. */
    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    // --- Debug-only entitlement simulator (until Play Billing is wired) ---

    private fun entitlementApp() = requireContext().applicationContext as XpendiqApplication

    private fun setupDebugEntitlement(view: View) {
        val header = view.findViewById<View>(R.id.debug_section_header)
        val card = view.findViewById<View>(R.id.debug_section_card)
        val show = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
        header.visibility = show
        card.visibility = show
        if (!BuildConfig.DEBUG) return
        view.findViewById<View>(R.id.debug_entitlement_row).setOnClickListener {
            showDebugEntitlementDialog()
        }
        refreshDebugEntitlement()
    }

    private fun refreshDebugEntitlement() {
        val subtitle = view?.findViewById<TextView>(R.id.debug_entitlement_subtitle) ?: return
        subtitle.text = when (val s = entitlementApp().entitlement.state.value) {
            is EntitlementState.InTrial -> "In trial · ${s.daysLeft} days left"
            EntitlementState.Expired -> "Trial expired · read-only + locking"
            EntitlementState.Subscribed -> "Subscribed · full access"
        }
    }

    private fun showDebugEntitlementDialog() {
        val ctx = requireContext()
        val now = System.currentTimeMillis()
        val day = 24L * 60 * 60 * 1000
        val options = arrayOf(
            "Trial — 30 days left",
            "Trial — 5 days left (day 25)",
            "Trial — 1 day left",
            "Expired",
            "Subscribed",
            "Reset (fresh trial)",
            getString(R.string.settings_debug_fire_reminder),
        )
        AlertDialog.Builder(ctx)
            .setTitle(R.string.settings_debug_entitlement)
            .setItems(options) { _, which ->
                if (which <= 5) {
                    when (which) {
                        0 -> { Preferences.setSubscribed(ctx, false); Preferences.setTrialStart(ctx, now) }
                        1 -> { Preferences.setSubscribed(ctx, false); Preferences.setTrialStart(ctx, now - 25 * day) }
                        2 -> { Preferences.setSubscribed(ctx, false); Preferences.setTrialStart(ctx, now - 29 * day) }
                        3 -> { Preferences.setSubscribed(ctx, false); Preferences.setTrialStart(ctx, now - 31 * day) }
                        4 -> Preferences.setSubscribed(ctx, true)
                        5 -> { Preferences.setSubscribed(ctx, false); Preferences.setTrialStart(ctx, now) }
                    }
                    entitlementApp().entitlement.refresh()
                    refreshDebugEntitlement()
                    view?.let { Snackbar.make(it, "Entitlement → ${options[which]}", Snackbar.LENGTH_SHORT).show() }
                } else {
                    com.kaizenll.xpendiq.work.TrialReminder.fireNow(ctx)
                    view?.let { Snackbar.make(it, "Trial reminder fired", Snackbar.LENGTH_SHORT).show() }
                }
            }
            .show()
    }

    companion object {
        private const val DAY_MILLIS = 24L * 60 * 60 * 1000
        // Rates older than this get a "tap to review" nudge (USD/INR drifts a few % over ~2 months).
        private const val FX_STALE_DAYS = 60
    }
}
