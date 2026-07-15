package com.kaizenll.xpendiq

import android.app.Application
import com.kaizenll.xpendiq.categorizer.Categorizer
import com.kaizenll.xpendiq.data.db.ColorPaletteMigration
import com.kaizenll.xpendiq.data.db.HashRehashMigration
import com.kaizenll.xpendiq.data.db.SeedMigration
import com.kaizenll.xpendiq.data.db.XpendiqDatabase
import com.kaizenll.xpendiq.data.db.StaleTransactionCleanup
import com.kaizenll.xpendiq.billing.BillingFactory
import com.kaizenll.xpendiq.billing.BillingGateway
import com.kaizenll.xpendiq.data.repo.CategoryRepository
import com.kaizenll.xpendiq.data.repo.TransactionRepository
import com.kaizenll.xpendiq.entitlement.EntitlementManager
import com.kaizenll.xpendiq.parser.SmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class XpendiqApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: XpendiqDatabase by lazy { XpendiqDatabase.get(this, appScope) }

    val parser: SmsParser by lazy { SmsParser() }

    /** Trial / subscription state. Drives read-only gating and the lock flag on new captures. */
    val entitlement: EntitlementManager by lazy { EntitlementManager(this) }

    /** Purchase surface. Real Play Billing on the `play` flavor; a no-op on the free `full` flavor. */
    val billing: BillingGateway by lazy { BillingFactory.create(this) }

    val categorizer: Categorizer by lazy {
        Categorizer(database.categoryDao(), database.merchantRuleDao())
    }

    val repository: TransactionRepository by lazy {
        TransactionRepository(
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            deletedSmsHashDao = database.deletedSmsHashDao(),
            ignoredSenderDao = database.ignoredSenderDao(),
            merchantRuleDao = database.merchantRuleDao(),
            parser = parser,
            categorizer = categorizer,
            fxRateProvider = { com.kaizenll.xpendiq.util.Preferences.getUsdInrRate(this) },
            entitledProvider = { entitlement.isEntitled() },
        )
    }

    val categoryRepository: CategoryRepository by lazy {
        CategoryRepository(
            categoryDao = database.categoryDao(),
            transactionDao = database.transactionDao(),
            merchantRuleDao = database.merchantRuleDao(),
        )
    }

    override fun onCreate() {
        super.onCreate()
        com.kaizenll.xpendiq.util.AppLock.registerBackgroundReset(this)
        appScope.launch(Dispatchers.IO) {
            HashRehashMigration.run(database)
            SeedMigration.run(database)
            ColorPaletteMigration.run(database)
            StaleTransactionCleanup.run(database, parser)
        }
        // Whenever the user is entitled (in trial or subscribed), make sure nothing stays hidden —
        // this reveals rows that accrued while expired, the moment a subscription kicks in.
        appScope.launch(Dispatchers.IO) {
            entitlement.state.collect { state ->
                if (state.isEntitled && repository.lockedCount() > 0) repository.unlockAll()
            }
        }
        // Nudge the user ~5 days before the trial ends (no-op once subscribed / already past day 25).
        com.kaizenll.xpendiq.work.TrialReminder.ensureChannel(this)
        com.kaizenll.xpendiq.work.TrialReminder.schedule(this)
        // Connect billing and reconcile any existing subscription into the entitlement flag (no-op
        // on the free flavor). This also restores a purchase after reinstall / on a new device.
        billing.start()
    }
}
