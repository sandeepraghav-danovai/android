package com.sandeepraghav.passvault.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CategoryEntity::class, PasswordEntryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun passwordEntryDao(): PasswordEntryDao

    companion object {
        const val FILE_NAME = "passvault.db"

        @Volatile private var instance: VaultDatabase? = null

        fun getInstance(context: Context): VaultDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, VaultDatabase::class.java, FILE_NAME)
                    .build()
                    .also { instance = it }
            }

        fun dbFile(context: Context) = context.getDatabasePath(FILE_NAME)

        /**
         * Closes and forgets the open database so its files can be deleted. Room keeps the
         * file handles open, and on some filesystems deleting underneath a live connection
         * leaves the -wal/-shm siblings behind, so this must run before a vault reset.
         */
        fun closeInstance() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }
    }
}

val DEFAULT_CATEGORIES = listOf(
    Triple("Banks", "bank", "#2E7D32"),
    Triple("Apps", "apps", "#1565C0"),
    Triple("Websites", "language", "#6A1B9A"),
    Triple("Office", "work", "#EF6C00"),
    Triple("Emails", "email", "#AD1457")
)
