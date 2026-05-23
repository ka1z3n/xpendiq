package com.example.spendy.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.spendy.data.dao.CategoryDao
import com.example.spendy.data.dao.DeletedSmsHashDao
import com.example.spendy.data.dao.IgnoredSenderDao
import com.example.spendy.data.dao.MerchantRuleDao
import com.example.spendy.data.dao.TransactionDao
import com.example.spendy.data.entity.Category
import com.example.spendy.data.entity.DeletedSmsHash
import com.example.spendy.data.entity.IgnoredSender
import com.example.spendy.data.entity.MerchantRule
import com.example.spendy.data.entity.TransactionEntity
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
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class SpendyDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun deletedSmsHashDao(): DeletedSmsHashDao
    abstract fun ignoredSenderDao(): IgnoredSenderDao

    companion object {
        @Volatile private var INSTANCE: SpendyDatabase? = null

        fun get(context: Context, scope: CoroutineScope): SpendyDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: build(context, scope).also { INSTANCE = it }
            }

        private fun build(context: Context, scope: CoroutineScope): SpendyDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                SpendyDatabase::class.java,
                "spendy.db",
            )
                .addMigrations(MIGRATION_1_2)
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
    }
}
