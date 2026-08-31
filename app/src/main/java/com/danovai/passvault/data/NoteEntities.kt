package com.danovai.passvault.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * A note. Both the title and the body are ciphertext under the vault's DEK — unlike password
 * entries, where the title is deliberately left in the clear for list and search UX, a note's
 * title is usually just as revealing as its contents, so neither is stored readable.
 *
 * The consequence is that titles and full-text search cannot be done in SQL. Rows are ordered by
 * [updatedAt], which is plain metadata the list needs anyway, and matching happens in memory
 * after decryption. That is fine at personal scale and keeps the storage story simple: if you
 * copy the database off the device, it tells you how many notes exist and when they changed,
 * and nothing else.
 */
@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val encryptedTitle: String,
    val encryptedBody: String,
    val createdAt: Long,
    val updatedAt: Long
)

/**
 * Tag names are stored in the clear, unlike note content. They have to be compared for
 * uniqueness and rendered as filter chips before any particular note is opened, and encrypting
 * them would mean decrypting every tag on every keystroke for no real gain — the label "taxes"
 * leaks far less than the note behind it. Worth knowing rather than assuming otherwise.
 */
@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "note_tags",
    primaryKeys = ["noteId", "tagId"],
    indices = [Index("tagId")]
)
data class NoteTagCrossRef(val noteId: Long, val tagId: Long)

/** An image pasted or picked into a note. The bytes live encrypted in files/note_images. */
@Entity(tableName = "note_images", indices = [Index("noteId")])
data class NoteImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val fileName: String,
    val createdAt: Long
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    suspend fun getAll(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM notes")
    fun observeCount(): Flow<Int>
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY name ASC")
    suspend fun getAll(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Delete
    suspend fun delete(tag: TagEntity)

    @Query("SELECT tagId FROM note_tags WHERE noteId = :noteId")
    suspend fun tagIdsForNote(noteId: Long): List<Long>

    @Query("SELECT noteId FROM note_tags WHERE tagId IN (:tagIds)")
    suspend fun noteIdsForTags(tagIds: List<Long>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(ref: NoteTagCrossRef)

    @Query("DELETE FROM note_tags WHERE noteId = :noteId")
    suspend fun clearTagsForNote(noteId: Long)

    @Query("DELETE FROM note_tags WHERE tagId = :tagId")
    suspend fun clearNotesForTag(tagId: Long)

    /** Tags no note references any more — surfaced so the filter row doesn't fill with dead chips. */
    @Query("SELECT * FROM tags WHERE id NOT IN (SELECT DISTINCT tagId FROM note_tags)")
    suspend fun unusedTags(): List<TagEntity>
}

@Dao
interface NoteImageDao {
    @Query("SELECT * FROM note_images WHERE noteId = :noteId ORDER BY createdAt ASC")
    suspend fun forNote(noteId: Long): List<NoteImageEntity>

    @Insert
    suspend fun insert(image: NoteImageEntity): Long

    @Query("SELECT * FROM note_images WHERE id = :id")
    suspend fun getById(id: Long): NoteImageEntity?

    @Query("DELETE FROM note_images WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM note_images WHERE noteId = :noteId")
    suspend fun imagesToDelete(noteId: Long): List<NoteImageEntity>
}
