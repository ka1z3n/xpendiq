package com.kaizenll.xpendiq.entitlement

import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.kaizenll.xpendiq.R
import com.kaizenll.xpendiq.XpendiqApplication

/** The app-wide entitlement manager, from any attached fragment. */
val Fragment.entitlement: EntitlementManager
    get() = (requireActivity().application as XpendiqApplication).entitlement

/**
 * Gate for any edit/add/delete action. Returns true when the user may proceed; otherwise shows the
 * "subscribe to edit" prompt on [anchor] and returns false. (Step D will route this to the paywall
 * screen instead of a snackbar.)
 */
fun Fragment.requireEntitledToEdit(anchor: View): Boolean {
    if (entitlement.isEntitled()) return true
    Snackbar.make(anchor, R.string.paywall_edit_blocked, Snackbar.LENGTH_LONG)
        .setAction(R.string.paywall_subscribe_action) {
            runCatching { findNavController().navigate(R.id.paywallFragment) }
        }
        .show()
    return false
}
