package com.danovai.passvault.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class,
        PasswordEntryEntity::class,
        NoteEntity::class,
        TagEntity::class,
        NoteTagCrossRef::class,
        NoteImageEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun passwordEntryDao(): PasswordEntryDao
    abstract fun noteDao(): NoteDao
    abstract fun tagDao(): TagDao
    abstract fun noteImageDao(): NoteImageDao

    companion object {
        const val FILE_NAME = "passvault.db"

        @Volatile private var instance: VaultDatabase? = null

        /**
         * v1 -> v2 adds the notes feature. Purely additive: it creates new tables and touches
         * nothing that already exists, so an installed vault keeps every password through the
         * upgrade.
         *
         * This must never be replaced with fallbackToDestructiveMigration(). Room's "fallback"
         * for a missing migration is to drop and recreate the database, which here means
         * silently deleting the user's entire vault on first launch after an update.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `notes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `encryptedTitle` TEXT NOT NULL,
                        `encryptedBody` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tags` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `note_tags` (
                        `noteId` INTEGER NOT NULL,
                        `tagId` INTEGER NOT NULL,
                        PRIMARY KEY(`noteId`, `tagId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_tags_tagId` ON `note_tags` (`tagId`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `note_images` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `noteId` INTEGER NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_note_images_noteId` ON `note_images` (`noteId`)")
            }
        }

        fun getInstance(context: Context): VaultDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, VaultDatabase::class.java, FILE_NAME)
                    .addMigrations(MIGRATION_1_2)
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
