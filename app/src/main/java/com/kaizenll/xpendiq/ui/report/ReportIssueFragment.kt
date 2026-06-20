package com.kaizenll.xpendiq.ui.report

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.kaizenll.xpendiq.R
import com.kaizenll.xpendiq.util.DeviceInfo
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText

/**
 * Lets the user report a bug or a transaction Xpendiq missed. The report — their text, an optional
 * pasted SMS, and non-identifying device diagnostics — is handed to an email app via an intent, so
 * nothing leaves the device without the user explicitly sending it.
 */
class ReportIssueFragment : Fragment(R.layout.fragment_report_issue) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        val descInput = view.findViewById<TextInputEditText>(R.id.desc_input)
        val smsInput = view.findViewById<TextInputEditText>(R.id.sms_input)

        view.findViewById<MaterialButton>(R.id.send_btn).setOnClickListener {
            val description = descInput.text?.toString().orEmpty().trim()
            if (description.isEmpty()) {
                Snackbar.make(view, R.string.report_desc_required, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendReport(description, smsInput.text?.toString().orEmpty().trim())
        }
    }

    private fun sendReport(description: String, sms: String) {
        val body = buildString {
            append(description)
            append("\n\n")
            if (sms.isNotEmpty()) {
                append("---- Message that wasn't captured ----\n")
                append(sms)
                append("\n\n")
            }
            append("---- Diagnostics ----\n")
            append(DeviceInfo.diagnostics(requireContext()))
        }
        val subject = getString(R.string.report_email_subject)
        val uri = Uri.parse(
            "mailto:" + Uri.encode(SUPPORT_EMAIL) +
                "?subject=" + Uri.encode(subject) +
                "&body=" + Uri.encode(body),
        )
        try {
            startActivity(Intent(Intent.ACTION_SENDTO, uri))
        } catch (e: ActivityNotFoundException) {
            Snackbar.make(requireView(), R.string.report_no_email_app, Snackbar.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val SUPPORT_EMAIL = "shadowshredder77@gmail.com"
    }
}
