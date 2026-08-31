package com.sandeepraghav.passvault.repository

import android.content.Context
import com.sandeepraghav.passvault.crypto.Argon2Params
import com.sandeepraghav.passvault.crypto.CryptoManager
import com.sandeepraghav.passvault.data.CategoryEntity
import com.sandeepraghav.passvault.data.CsvTemplate
import com.sandeepraghav.passvault.data.DEFAULT_CATEGORIES
import com.sandeepraghav.passvault.data.PasswordEntryEntity
import com.sandeepraghav.passvault.data.VaultDatabase
import com.sandeepraghav.passvault.data.VaultHeader
import com.sandeepraghav.passvault.session.SessionManager
import kotlinx.coroutines.flow.Flow
import java.io.File

data class DecryptedEntry(
    val id: Long,
    val categoryId: Long,
    val title: String,
    val username: String,
    val password: String,
    val notes: String?,
    val url: String?,
    val favorite: Boolean,
    val updatedAt: Long
)

class VaultRepository(private val context: Context, val session: SessionManager) {

    private val db by lazy { VaultDatabase.getInstance(context) }
    private val categoryDao by lazy { db.categoryDao() }
    private val entryDao by lazy { db.passwordEntryDao() }

    private val canaryPlaintext = "PASSVAULT_CANARY_OK".toByteArray(Charsets.UTF_8)

    fun vaultExists(): Boolean = VaultHeader.exists(context)

    fun observeCategories(): Flow<List<CategoryEntity>> = categoryDao.observeAll()

    fun observeEntriesForCategory(categoryId: Long): Flow<List<PasswordEntryEntity>> =
        entryDao.observeByCategory(categoryId)

    fun observeEntryCount(categoryId: Long): Flow<Int> = entryDao.observeCountForCategory(categoryId)

    fun searchEntries(query: String): Flow<List<PasswordEntryEntity>> = entryDao.search(query)

    /** Creates a brand-new vault: master password, key hierarchy, and default categories. */
    suspend fun createVault(masterPassword: CharArray) {
        val salt = CryptoManager.generateSalt()
        val kek = CryptoManager.deriveKey(masterPassword, salt)
        val dek = CryptoManager.generateKey()
        try {
            val wrappedDek = CryptoManager.encrypt(kek, dek).toStorageString()
            val canary = CryptoManager.encrypt(dek, canaryPlaintext).toStorageString()

            VaultHeader.save(
                context,
                VaultHeader(
                    kdfSalt = salt,
                    argon2 = Argon2Params(),
                    wrappedDek = wrappedDek,
                    canary = canary,
                    recoveryEnabled = false,
                    recoveryWrappedDek = null,
                    recoveryKeyEncrypted = null,
                    recoveryPhone = null,
                    recoveryEmail = null,
                    smtpConfigEncrypted = null
                )
            )

            for ((index, defaults) in DEFAULT_CATEGORIES.withIndex()) {
                val (name, icon, color) = defaults
                categoryDao.insert(CategoryEntity(name = name, icon = icon, colorHex = color, sortOrder = index, isBuiltIn = true))
            }

            session.unlock(dek)
        } finally {
            CryptoManager.wipe(kek)
        }
    }

    /** Returns true and unlocks the session on success; false on a wrong password. */
    suspend fun unlock(masterPassword: CharArray): Boolean {
        val header = VaultHeader.load(context)
        val kek = CryptoManager.deriveKey(masterPassword, header.kdfSalt, header.argon2)
        return try {
            val dek = CryptoManager.decrypt(kek, com.sandeepraghav.passvault.crypto.EncryptedBlob.fromStorageString(header.wrappedDek))
            val canaryOk = CryptoManager.decrypt(dek, com.sandeepraghav.passvault.crypto.EncryptedBlob.fromStorageString(header.canary))
                .contentEquals(canaryPlaintext)
            if (canaryOk) {
                session.unlock(dek)
                true
            } else {
                CryptoManager.wipe(dek)
                false
            }
        } catch (e: Exception) {
            false
        } finally {
            CryptoManager.wipe(kek)
        }
    }

    /** Confirms the master password without changing session state — used to re-authorize show/copy. */
    suspend fun verifyMasterPassword(masterPassword: CharArray): Boolean {
        val header = VaultHeader.load(context)
        val kek = CryptoManager.deriveKey(masterPassword, header.kdfSalt, header.argon2)
        return try {
            val dek = CryptoManager.decrypt(kek, com.sandeepraghav.passvault.crypto.EncryptedBlob.fromStorageString(header.wrappedDek))
            val ok = CryptoManager.decrypt(dek, com.sandeepraghav.passvault.crypto.EncryptedBlob.fromStorageString(header.canary))
                .contentEquals(canaryPlaintext)
            CryptoManager.wipe(dek)
            ok
        } catch (e: Exception) {
            false
        } finally {
            CryptoManager.wipe(kek)
        }
    }

    /** Re-wraps the DEK under a new master password without touching any entry ciphertext. */
    suspend fun changeMasterPassword(newPassword: CharArray) {
        val dek = session.currentKey() ?: throw IllegalStateException("Vault is locked")
        val header = VaultHeader.load(context)
        val newSalt = CryptoManager.generateSalt()
        val newKek = CryptoManager.deriveKey(newPassword, newSalt)
        try {
            val wrappedDek = CryptoManager.encrypt(newKek, dek).toStorageString()
            VaultHeader.save(context, header.copy(kdfSalt = newSalt, wrappedDek = wrappedDek))
        } finally {
            CryptoManager.wipe(newKek)
        }
    }

    /**
     * Sets a new master password after any recovery path (SMS+email, or the recovery kit)
     * has already put the real DEK into the session. Deliberately the same operation as
     * [changeMasterPassword]: the DEK itself never needs to change, only which master
     * password re-wraps it, which means every other escrow (SMS+email, the recovery kit)
     * stays valid without having to be touched.
     */
    suspend fun setNewPasswordAfterRecovery(newPassword: CharArray) = changeMasterPassword(newPassword)

    /** Enables the SMS+email dual-approval recovery path. Requires the vault to be unlocked. */
    suspend fun enableRecovery(phone: String, email: String, smtpHost: String, smtpPort: Int, smtpUsername: String, smtpAppPassword: String) {
        val dek = session.currentKey() ?: throw IllegalStateException("Vault is locked")
        val recoveryKey = CryptoManager.generateKey()
        try {
            val recoveryWrappedDek = CryptoManager.encrypt(recoveryKey, dek).toStorageString()
            val recoveryKeyEncrypted = com.sandeepraghav.passvault.crypto.KeystoreHelper.encryptText(
                android.util.Base64.encodeToString(recoveryKey, android.util.Base64.NO_WRAP)
            )
            val smtpJson = org.json.JSONObject().apply {
                put("host", smtpHost)
                put("port", smtpPort)
                put("username", smtpUsername)
                put("appPassword", smtpAppPassword)
            }.toString()
            val smtpEncrypted = com.sandeepraghav.passvault.crypto.KeystoreHelper.encryptText(smtpJson)

            val header = VaultHeader.load(context)
            VaultHeader.save(
                context,
                header.copy(
                    recoveryEnabled = true,
                    recoveryWrappedDek = recoveryWrappedDek,
                    recoveryKeyEncrypted = recoveryKeyEncrypted,
                    recoveryPhone = phone,
                    recoveryEmail = email,
                    smtpConfigEncrypted = smtpEncrypted
                )
            )
        } finally {
            CryptoManager.wipe(recoveryKey)
        }
    }

    fun disableRecovery() {
        val header = VaultHeader.load(context)
        VaultHeader.save(
            context,
            header.copy(
                recoveryEnabled = false,
                recoveryWrappedDek = null,
                recoveryKeyEncrypted = null,
                recoveryPhone = null,
                recoveryEmail = null,
                smtpConfigEncrypted = null
            )
        )
    }

    /**
     * Generates a brand-new one-time recovery key, wraps the current DEK with its raw bytes,
     * and returns the formatted string to show the user exactly once. Nothing about the raw
     * key is persisted anywhere — only [VaultHeader.recoveryKitWrappedDek] is. Calling this
     * again (regeneration) silently invalidates any previously issued key.
     */
    suspend fun generateRecoveryKit(): String {
        val dek = session.currentKey() ?: throw IllegalStateException("Vault is locked")
        val keyBytes = CryptoManager.generateKey()
        try {
            val wrapped = CryptoManager.encrypt(keyBytes, dek).toStorageString()
            val header = VaultHeader.load(context)
            VaultHeader.save(context, header.copy(recoveryKitEnabled = true, recoveryKitWrappedDek = wrapped))
            return com.sandeepraghav.passvault.crypto.RecoveryKitCodec.format(keyBytes)
        } finally {
            CryptoManager.wipe(keyBytes)
        }
    }

    fun disableRecoveryKit() {
        val header = VaultHeader.load(context)
        VaultHeader.save(context, header.copy(recoveryKitEnabled = false, recoveryKitWrappedDek = null))
    }

    fun isRecoveryKitEnabled(): Boolean = vaultExists() && VaultHeader.load(context).recoveryKitEnabled

    /** Unlocks the session directly from a saved recovery-key string. No master password involved. */
    suspend fun unlockWithRecoveryKit(code: String): Boolean {
        val header = VaultHeader.load(context)
        val wrapped = header.recoveryKitWrappedDek ?: return false
        val keyBytes = try {
            com.sandeepraghav.passvault.crypto.RecoveryKitCodec.parse(code)
        } catch (e: Exception) {
            return false
        }
        if (keyBytes.size != CryptoManager.KEY_LENGTH_BYTES) return false

        return try {
            val dek = CryptoManager.decrypt(keyBytes, com.sandeepraghav.passvault.crypto.EncryptedBlob.fromStorageString(wrapped))
            val canaryOk = CryptoManager.decrypt(dek, com.sandeepraghav.passvault.crypto.EncryptedBlob.fromStorageString(header.canary))
                .contentEquals(canaryPlaintext)
            if (canaryOk) {
                session.unlock(dek)
                true
            } else {
                CryptoManager.wipe(dek)
                false
            }
        } catch (e: Exception) {
            false
        } finally {
            CryptoManager.wipe(keyBytes)
        }
    }

    // ---------------------------------------------------------------- biometric unlock

    fun isBiometricEnabled(): Boolean = vaultExists() && VaultHeader.load(context).biometricEnabled

    /** The IV the DEK was sealed with, needed to build the decrypt cipher before prompting. */
    fun biometricIv(): ByteArray? {
        val wrapped = VaultHeader.load(context).biometricWrappedDek ?: return null
        return runCatching {
            com.sandeepraghav.passvault.crypto.EncryptedBlob.fromStorageString(wrapped).iv
        }.getOrNull()
    }

    /**
     * Seals the in-memory DEK with a biometric-gated Keystore key. [cipher] must be the
     * ENCRYPT-mode cipher that BiometricPrompt has just authenticated, and the vault must
     * already be unlocked — enabling biometrics is never a way to obtain the DEK.
     */
    fun enableBiometricUnlock(cipher: javax.crypto.Cipher) {
        val dek = session.currentKey() ?: throw IllegalStateException("Vault is locked")
        val ciphertext = cipher.doFinal(dek)
        val blob = com.sandeepraghav.passvault.crypto.EncryptedBlob(cipher.iv, ciphertext).toStorageString()
        val header = VaultHeader.load(context)
        VaultHeader.save(context, header.copy(biometricEnabled = true, biometricWrappedDek = blob))
    }

    fun disableBiometricUnlock() {
        com.sandeepraghav.passvault.crypto.BiometricKeystore.deleteKey()
        val header = VaultHeader.load(context)
        VaultHeader.save(context, header.copy(biometricEnabled = false, biometricWrappedDek = null))
    }

    /**
     * Unwraps the DEK with an already-authenticated DECRYPT cipher and starts a session.
     * The canary is still checked, so a blob that somehow does not belong to this vault is
     * rejected rather than producing a garbage key.
     */
    suspend fun unlockWithBiometric(cipher: javax.crypto.Cipher): Boolean {
        val header = VaultHeader.load(context)
        val wrapped = header.biometricWrappedDek ?: return false
        return try {
            val blob = com.sandeepraghav.passvault.crypto.EncryptedBlob.fromStorageString(wrapped)
            val dek = cipher.doFinal(blob.ciphertext)
            val canaryOk = CryptoManager.decrypt(dek, com.sandeepraghav.passvault.crypto.EncryptedBlob.fromStorageString(header.canary))
                .contentEquals(canaryPlaintext)
            if (canaryOk) {
                session.unlock(dek)
                true
            } else {
                CryptoManager.wipe(dek)
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /** True when a per-entry reveal/copy can be re-authorised with biometrics instead of typing. */
    fun isBiometricReAuthAvailable(): Boolean = isBiometricEnabled() && session.currentKey() != null

    /**
     * Re-authorises revealing or copying a single password.
     *
     * The vault is already unlocked at this point, so this is not about obtaining the DEK — it
     * re-proves that the enrolled human is still the one holding the phone. It does so
     * cryptographically rather than trusting a boolean callback: the Keystore key only operates
     * after a successful prompt, and the DEK it unwraps has to match the one already in the
     * session, so a blob from some other vault cannot satisfy the gate.
     */
    fun verifyBiometric(cipher: javax.crypto.Cipher): Boolean {
        val sessionKey = session.currentKey() ?: return false
        val wrapped = VaultHeader.load(context).biometricWrappedDek ?: return false
        return try {
            val blob = com.sandeepraghav.passvault.crypto.EncryptedBlob.fromStorageString(wrapped)
            val unwrapped = cipher.doFinal(blob.ciphertext)
            // constant-time compare; these are both key material
            val ok = java.security.MessageDigest.isEqual(unwrapped, sessionKey)
            CryptoManager.wipe(unwrapped)
            ok
        } catch (e: Exception) {
            false
        }
    }

    // ---------------------------------------------------------------- bulk CSV import

    /**
     * Outcome of an import. Rows are independent: a malformed row is reported and skipped, and
     * everything else still lands, because a 200-row file failing wholesale over one bad line is
     * far more annoying than a partial import with a list of what to fix.
     */
    data class CsvImportResult(
        val imported: Int = 0,
        val skippedDuplicates: Int = 0,
        val categoriesCreated: List<String> = emptyList(),
        val errors: List<String> = emptyList(),
        val fatalError: String? = null
    )

    /**
     * Imports entries from a CSV in the [CsvTemplate] shape. Requires an unlocked vault: each
     * password is encrypted with the session DEK as it is inserted, exactly like a hand-typed
     * entry, so nothing is ever stored in the clear.
     *
     * Duplicates are judged on (category, title, username) — re-importing the same file will not
     * double up. Missing categories are created as you go.
     */
    suspend fun importEntriesFromCsv(csvText: String): CsvImportResult {
        session.currentKey() ?: return CsvImportResult(fatalError = "The vault is locked.")

        val rows = com.sandeepraghav.passvault.util.CsvParser.parse(csvText)
        if (rows.isEmpty()) return CsvImportResult(fatalError = "That file is empty.")

        val header = rows.first().map { it.trim().lowercase() }
        fun col(name: String) = header.indexOf(name)

        val titleIdx = col(CsvTemplate.TITLE)
        val passwordIdx = col(CsvTemplate.PASSWORD)
        if (titleIdx < 0 || passwordIdx < 0) {
            return CsvImportResult(
                fatalError = "The header row needs at least '${CsvTemplate.TITLE}' and " +
                    "'${CsvTemplate.PASSWORD}' columns. Found: ${header.joinToString(", ")}"
            )
        }
        val categoryIdx = col(CsvTemplate.CATEGORY)
        val usernameIdx = col(CsvTemplate.USERNAME)
        val urlIdx = col(CsvTemplate.URL)
        val notesIdx = col(CsvTemplate.NOTES)

        // name -> id, matched case-insensitively so "banks" lands in the existing "Banks"
        val categories = categoryDao.getAll().associateTo(mutableMapOf()) { it.name.lowercase() to it.id }
        val existing = entryDao.getAll()
            .mapTo(mutableSetOf()) { "${it.categoryId}|${it.title.lowercase()}|${it.username.lowercase()}" }

        var imported = 0
        var duplicates = 0
        val created = mutableListOf<String>()
        val errors = mutableListOf<String>()

        rows.drop(1).forEachIndexed { index, row ->
            val lineNumber = index + 2   // header is line 1
            fun field(i: Int): String = if (i >= 0 && i < row.size) row[i].trim() else ""

            val title = field(titleIdx)
            val password = field(passwordIdx)
            when {
                title.isEmpty() && password.isEmpty() -> return@forEachIndexed   // blank filler row
                title.isEmpty() -> { errors.add("Line $lineNumber: missing title"); return@forEachIndexed }
                password.isEmpty() -> { errors.add("Line $lineNumber: missing password for \"$title\""); return@forEachIndexed }
            }

            val categoryName = field(categoryIdx).ifEmpty { CsvTemplate.DEFAULT_CATEGORY }
            val categoryId = categories[categoryName.lowercase()] ?: run {
                val newId = categoryDao.insert(
                    CategoryEntity(
                        name = categoryName,
                        icon = "folder",
                        colorHex = "#546E7A",
                        sortOrder = categoryDao.count(),
                        isBuiltIn = false
                    )
                )
                categories[categoryName.lowercase()] = newId
                created.add(categoryName)
                newId
            }

            val username = field(usernameIdx)
            val key = "$categoryId|${title.lowercase()}|${username.lowercase()}"
            if (!existing.add(key)) {
                duplicates++
                return@forEachIndexed
            }

            addEntry(
                categoryId = categoryId,
                title = title,
                username = username,
                password = password,
                notes = field(notesIdx).ifEmpty { null },
                url = field(urlIdx).ifEmpty { null }
            )
            imported++
        }

        return CsvImportResult(
            imported = imported,
            skippedDuplicates = duplicates,
            categoriesCreated = created,
            errors = errors
        )
    }

    // ---------------------------------------------------------------- destructive reset

    /**
     * Deletes the vault outright: both files, every escrowed copy of the DEK, and the
     * device-bound Keystore material behind them. There is no undo and no recovery path
     * afterwards — every stored password is gone, and any previously exported backup stays
     * encrypted under the old master password, so this does not make those readable either.
     */
    suspend fun resetVault() {
        session.lock()
        VaultDatabase.closeInstance()

        // deleteDatabase also removes the -wal and -shm siblings
        context.deleteDatabase(VaultDatabase.FILE_NAME)
        VaultHeader.file(context).delete()

        com.sandeepraghav.passvault.crypto.BiometricKeystore.deleteKey()
        com.sandeepraghav.passvault.crypto.KeystoreHelper.deleteKey()

        session.resetToNoVault()
    }

    suspend fun addCategory(name: String, icon: String, colorHex: String) {
        val count = categoryDao.count()
        categoryDao.insert(CategoryEntity(name = name, icon = icon, colorHex = colorHex, sortOrder = count, isBuiltIn = false))
    }

    suspend fun deleteCategory(category: CategoryEntity) = categoryDao.delete(category)

    suspend fun addEntry(
        categoryId: Long,
        title: String,
        username: String,
        password: String,
        notes: String?,
        url: String?
    ) {
        val dek = session.currentKey() ?: throw IllegalStateException("Vault is locked")
        val now = System.currentTimeMillis()
        entryDao.insert(
            PasswordEntryEntity(
                categoryId = categoryId,
                title = title,
                username = username,
                encryptedPassword = CryptoManager.encryptText(dek, password),
                encryptedNotes = notes?.takeIf { it.isNotBlank() }?.let { CryptoManager.encryptText(dek, it) },
                url = url,
                favorite = false,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun updateEntry(
        id: Long,
        categoryId: Long,
        title: String,
        username: String,
        password: String,
        notes: String?,
        url: String?,
        favorite: Boolean
    ) {
        val dek = session.currentKey() ?: throw IllegalStateException("Vault is locked")
        val existing = entryDao.getById(id) ?: return
        entryDao.update(
            existing.copy(
                categoryId = categoryId,
                title = title,
                username = username,
                encryptedPassword = CryptoManager.encryptText(dek, password),
                encryptedNotes = notes?.takeIf { it.isNotBlank() }?.let { CryptoManager.encryptText(dek, it) },
                url = url,
                favorite = favorite,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteEntry(entry: PasswordEntryEntity) = entryDao.delete(entry)

    suspend fun deleteEntryById(id: Long) {
        entryDao.getById(id)?.let { entryDao.delete(it) }
    }

    /** Decrypts a single entry's secret fields. Requires the vault to be unlocked. */
    suspend fun decryptEntry(id: Long): DecryptedEntry? {
        val dek = session.currentKey() ?: throw IllegalStateException("Vault is locked")
        val entry = entryDao.getById(id) ?: return null
        return DecryptedEntry(
            id = entry.id,
            categoryId = entry.categoryId,
            title = entry.title,
            username = entry.username,
            password = CryptoManager.decryptText(dek, entry.encryptedPassword),
            notes = entry.encryptedNotes?.let { CryptoManager.decryptText(dek, it) },
            url = entry.url,
            favorite = entry.favorite,
            updatedAt = entry.updatedAt
        )
    }

    /**
     * The two files that make up a full vault backup (see VaultHeader / VaultDatabase).
     * Checkpoints Room's write-ahead log into the main .db file first so the exported
     * copy isn't missing recently-written rows still sitting in the -wal file.
     */
    suspend fun vaultFilesForBackup(): List<File> {
        db.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)")
        return listOf(VaultHeader.file(context), VaultDatabase.dbFile(context))
    }
}
