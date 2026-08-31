package com.danovai.passvault.notes

import android.content.Context
import com.danovai.passvault.crypto.CryptoManager
import com.danovai.passvault.crypto.EncryptedBlob
import com.danovai.passvault.data.NoteEntity
import com.danovai.passvault.data.NoteImageEntity
import com.danovai.passvault.data.NoteTagCrossRef
import com.danovai.passvault.data.TagEntity
import com.danovai.passvault.data.VaultDatabase
import com.danovai.passvault.session.SessionManager
import java.io.File

/** A note with its content decrypted, ready to show. */
data class DecryptedNote(
    val id: Long,
    val title: String,
    val body: String,
    val tags: List<TagEntity>,
    val images: List<NoteImageEntity>,
    val createdAt: Long,
    val updatedAt: Long
) {
    /** First slice of the body, for the list row. */
    fun snippet(max: Int = 120): String =
        body.replace('\n', ' ').trim().let { if (it.length <= max) it else it.take(max).trimEnd() + "…" }
}

/**
 * Notes live in the same database and under the same DEK as the vault, but deliberately in their
 * own repository: the password side is untouched by anything here.
 *
 * Notes sit behind the same initial unlock as the vault — there is no separate passphrase — but
 * unlike a stored password they are not gated again on every read. Opening a note after you have
 * already unlocked is just reading, which is the whole point of a notebook.
 */
class NotesRepository(
    private val context: Context,
    private val session: SessionManager
) {
    private val db by lazy { VaultDatabase.getInstance(context) }
    private val noteDao by lazy { db.noteDao() }
    private val tagDao by lazy { db.tagDao() }
    private val imageDao by lazy { db.noteImageDao() }

    private fun requireKey(): ByteArray =
        session.currentKey() ?: throw IllegalStateException("Vault is locked")

    private fun imageDir(): File = File(context.filesDir, "note_images").apply { mkdirs() }

    // ---------------------------------------------------------------- reading

    suspend fun allNotes(): List<DecryptedNote> {
        val key = requireKey()
        return noteDao.getAll().mapNotNull { entity -> decrypt(entity, key) }
    }

    suspend fun note(id: Long): DecryptedNote? {
        val key = requireKey()
        return noteDao.getById(id)?.let { decrypt(it, key) }
    }

    private suspend fun decrypt(entity: NoteEntity, key: ByteArray): DecryptedNote? = runCatching {
        val tagIds = tagDao.tagIdsForNote(entity.id).toSet()
        val tags = tagDao.getAll().filter { it.id in tagIds }
        DecryptedNote(
            id = entity.id,
            title = CryptoManager.decryptText(key, entity.encryptedTitle),
            body = CryptoManager.decryptText(key, entity.encryptedBody),
            tags = tags,
            images = imageDao.forNote(entity.id),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }.getOrNull()

    /**
     * Full-text search across titles and bodies, plus an optional tag filter.
     *
     * Content is encrypted, so this cannot be a SQL LIKE — every note is decrypted and matched in
     * memory. At personal scale that is a few milliseconds; it would need revisiting for tens of
     * thousands of notes, which is not what this is.
     */
    suspend fun search(query: String, tagIds: Set<Long>): List<DecryptedNote> {
        val notes = allNotes()
        val byTag = if (tagIds.isEmpty()) notes else notes.filter { note ->
            note.tags.any { it.id in tagIds }
        }
        val q = query.trim()
        if (q.isEmpty()) return byTag
        return byTag.filter {
            it.title.contains(q, ignoreCase = true) || it.body.contains(q, ignoreCase = true)
        }
    }

    // ---------------------------------------------------------------- writing

    /**
     * Falls back to a title derived from the opening line when none is given, so a note jotted
     * down in a hurry still has something recognisable in the list.
     */
    fun deriveTitle(title: String, body: String): String {
        if (title.isNotBlank()) return title.trim()
        val firstLine = body.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (firstLine.isEmpty()) return "Untitled note"
        return if (firstLine.length <= 60) firstLine else firstLine.take(60).trimEnd() + "…"
    }

    suspend fun createNote(title: String, body: String, tagNames: List<String>): Long {
        val key = requireKey()
        val now = System.currentTimeMillis()
        val id = noteDao.insert(
            NoteEntity(
                encryptedTitle = CryptoManager.encryptText(key, deriveTitle(title, body)),
                encryptedBody = CryptoManager.encryptText(key, body),
                createdAt = now,
                updatedAt = now
            )
        )
        applyTags(id, tagNames)
        return id
    }

    suspend fun updateNote(id: Long, title: String, body: String, tagNames: List<String>) {
        val key = requireKey()
        val existing = noteDao.getById(id) ?: return
        noteDao.update(
            existing.copy(
                encryptedTitle = CryptoManager.encryptText(key, deriveTitle(title, body)),
                encryptedBody = CryptoManager.encryptText(key, body),
                updatedAt = System.currentTimeMillis()
            )
        )
        applyTags(id, tagNames)
    }

    suspend fun deleteNote(id: Long) {
        imageDao.imagesToDelete(id).forEach { File(imageDir(), it.fileName).delete() }
        tagDao.clearTagsForNote(id)
        noteDao.deleteById(id)
        pruneUnusedTags()
    }

    // ---------------------------------------------------------------- tags

    suspend fun allTags(): List<TagEntity> = tagDao.getAll()

    suspend fun createTag(name: String): TagEntity? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        tagDao.findByName(clean)?.let { return it }
        tagDao.insert(TagEntity(name = clean))
        return tagDao.findByName(clean)
    }

    private suspend fun applyTags(noteId: Long, tagNames: List<String>) {
        tagDao.clearTagsForNote(noteId)
        tagNames.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.forEach { name ->
            val tag = tagDao.findByName(name) ?: run {
                tagDao.insert(TagEntity(name = name))
                tagDao.findByName(name)
            }
            tag?.let { tagDao.link(NoteTagCrossRef(noteId = noteId, tagId = it.id)) }
        }
        pruneUnusedTags()
    }

    /** Drops tags nothing references, so the filter row stays meaningful. */
    private suspend fun pruneUnusedTags() {
        tagDao.unusedTags().forEach { tagDao.delete(it) }
    }

    // ---------------------------------------------------------------- images

    /** Stores [bytes] encrypted under the DEK and attaches it to the note. */
    suspend fun addImage(noteId: Long, bytes: ByteArray): Boolean = runCatching {
        val key = requireKey()
        val blob = CryptoManager.encrypt(key, bytes)
        val fileName = "img_${noteId}_${System.currentTimeMillis()}_${(0..9999).random()}.bin"
        File(imageDir(), fileName).writeText(blob.toStorageString())
        imageDao.insert(
            NoteImageEntity(noteId = noteId, fileName = fileName, createdAt = System.currentTimeMillis())
        )
        true
    }.getOrDefault(false)

    /** Decrypted bytes for display. Returns null if the file is missing or unreadable. */
    suspend fun imageBytes(image: NoteImageEntity): ByteArray? = runCatching {
        val key = requireKey()
        val stored = File(imageDir(), image.fileName).readText()
        CryptoManager.decrypt(key, EncryptedBlob.fromStorageString(stored))
    }.getOrNull()

    suspend fun deleteImage(image: NoteImageEntity) {
        File(imageDir(), image.fileName).delete()
        imageDao.deleteById(image.id)
    }
}
