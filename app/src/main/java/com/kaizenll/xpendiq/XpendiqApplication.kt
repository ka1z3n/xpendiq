package com.kaizenll.xpendiq

import android.app.Application
import com.kaizenll.xpendiq.categorizer.Categorizer
import com.kaizenll.xpendiq.data.db.ColorPaletteMigration
import com.kaizenll.xpendiq.data.db.HashRehashMigration
import com.kaizenll.xpendiq.data.db.SeedMigration
import com.kaizenll.xpendiq.data.db.XpendiqDatabase
import com.kaizenll.xpendiq.data.db.StaleTransactionCleanup
import com.kaizenll.xpendiq.data.repo.CategoryRepository
import com.kaizenll.xpendiq.data.repo.TransactionRepository
import com.kaizenll.xpendiq.parser.SmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class XpendiqApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: XpendiqDatabase by lazy { XpendiqDatabase.get(this, appScope) }

    val parser: SmsParser by lazy { SmsParser() }

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
    }
}
