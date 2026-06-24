package com.kaizenll.xpendiq.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kaizenll.xpendiq.data.dao.CategoryDao
import com.kaizenll.xpendiq.data.dao.DeletedSmsHashDao
import com.kaizenll.xpendiq.data.dao.IgnoredSenderDao
import com.kaizenll.xpendiq.data.dao.MerchantRuleDao
import com.kaizenll.xpendiq.data.dao.TransactionDao
import com.kaizenll.xpendiq.data.entity.Category
import com.kaizenll.xpendiq.data.entity.DeletedSmsHash
import com.kaizenll.xpendiq.data.entity.IgnoredSender
import com.kaizenll.xpendiq.data.entity.MerchantRule
import com.kaizenll.xpendiq.data.entity.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        TransactionEntity::class,
        Category::class,
        MerchantRule::class,
        DeletedSmsHash::class,
        IgnoredSender::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class XpendiqDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun deletedSmsHashDao(): DeletedSmsHashDao
    abstract fun ignoredSenderDao(): IgnoredSenderDao

    companion object {
        @Volatile private var INSTANCE: XpendiqDatabase? = null

        fun get(context: Context, scope: CoroutineScope): XpendiqDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context, scope).also { INSTANCE = it }
            }

        private fun build(context: Context, scope: CoroutineScope): XpendiqDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                XpendiqDatabase::class.java,
                "xpendiq.db",
            )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        scope.launch(Dispatchers.IO) {
                            INSTANCE?.let { DatabaseSeeder.seed(it) }
                        }
                    }
                })
                .build()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN currency TEXT NOT NULL DEFAULT 'INR'")
            }
        }

        // Adds the "excluded from totals" flag and back-fills it for the existing CC-bill-payment
        // bucket (an internal transfer). New "Self-transfer" categories are added by SeedMigration.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN excludedFromTotals INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE categories SET excludedFromTotals = 1 WHERE name = 'Transfers (CC payment)'")
            }
        }

        // Frozen INR-equivalent for foreign-currency rows (nullable). Populated at capture from
        // the user's manual FX rate; existing foreign rows are filled when a rate is first set.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN amountInrPaise INTEGER")
            }
        }
    }
}
