package com.example.spendy

import android.app.Application
import com.example.spendy.categorizer.Categorizer
import com.example.spendy.data.db.ColorPaletteMigration
import com.example.spendy.data.db.HashRehashMigration
import com.example.spendy.data.db.SeedMigration
import com.example.spendy.data.db.SpendyDatabase
import com.example.spendy.data.db.StaleTransactionCleanup
import com.example.spendy.data.repo.CategoryRepository
import com.example.spendy.data.repo.TransactionRepository
import com.example.spendy.parser.SmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SpendyApplication : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: SpendyDatabase by lazy { SpendyDatabase.get(this, appScope) }

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
        appScope.launch(Dispatchers.IO) {
            HashRehashMigration.run(database)
            SeedMigration.run(database)
            ColorPaletteMigration.run(database)
            StaleTransactionCleanup.run(database, parser)
        }
    }
}
