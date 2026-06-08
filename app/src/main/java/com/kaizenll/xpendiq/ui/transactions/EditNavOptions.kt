package com.kaizenll.xpendiq.ui.transactions

import androidx.navigation.navOptions
import com.kaizenll.xpendiq.R

/**
 * Slide transition for opening the Add/Edit transaction screen, so it animates in like the rest
 * of the app instead of snapping into place. Shared by the Transactions FAB and the detail sheet.
 */
internal val EDIT_NAV_OPTIONS = navOptions {
    anim {
        enter = R.anim.nav_enter
        exit = R.anim.nav_exit
        popEnter = R.anim.nav_pop_enter
        popExit = R.anim.nav_pop_exit
    }
}
