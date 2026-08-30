package com.sandeepraghav.passvault.data

import android.content.Context
import android.util.Base64
import com.sandeepraghav.passvault.crypto.Argon2Params
import org.json.JSONObject
import java.io.File

/**
 * The vault "envelope": everything needed to unlock the database, but none of the
 * secrets themselves in the clear. This file plus the Room database file together
 * make up the full backup unit (see VaultRepository.vaultFilesForBackup).
 */
data class VaultHeader(
    val kdfSalt: ByteArray,
    val argon2: Argon2Params,
    val wrappedDek: String,
    val canary: String,
    val recoveryEnabled: Boolean,
    /** The DEK wrapped by a persistent RecoveryKey (independent of the master password). */
    val recoveryWrappedDek: String?,
    /** That RecoveryKey, wrapped by a device-bound Android Keystore key (see KeystoreHelper). */
    val recoveryKeyEncrypted: String?,
    val recoveryPhone: String?,
    val recoveryEmail: String?,
    val smtpConfigEncrypted: String?,
    /**
     * The DEK wrapped directly by a one-time recovery key's raw bytes. The key itself is shown
     * to the user exactly once at generation time and never stored anywhere — only this wrapped
     * blob is. Unlike [recoveryWrappedDek] it isn't tied to this device's Keystore, so it works
     * from a reinstall on a different phone too (paired with a restored backup, see
     * VaultRepository.vaultFilesForBackup).
     */
    val recoveryKitEnabled: Boolean = false,
    val recoveryKitWrappedDek: String? = null
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("version", 1)
        obj.put("kdfSalt", Base64.encodeToString(kdfSalt, Base64.NO_WRAP))
        obj.put("argon2", JSONObject().apply {
            put("t", argon2.tCostIterations)
            put("m", argon2.mCostKibibytes)
            put("p", argon2.parallelism)
            put("len", argon2.keyLengthBytes)
        })
        obj.put("wrappedDek", wrappedDek)
        obj.put("canary", canary)
        obj.put("recoveryEnabled", recoveryEnabled)
        obj.put("recoveryWrappedDek", recoveryWrappedDek)
        obj.put("recoveryKeyEncrypted", recoveryKeyEncrypted)
        obj.put("recoveryPhone", recoveryPhone)
        obj.put("recoveryEmail", recoveryEmail)
        obj.put("smtpConfigEncrypted", smtpConfigEncrypted)
        obj.put("recoveryKitEnabled", recoveryKitEnabled)
        obj.put("recoveryKitWrappedDek", recoveryKitWrappedDek)
        return obj.toString()
    }

    companion object {
        private const val FILE_NAME = "vault_header.json"

        fun file(context: Context): File = File(context.filesDir, FILE_NAME)

        fun exists(context: Context): Boolean = file(context).exists()

        fun load(context: Context): VaultHeader {
            val obj = JSONObject(file(context).readText())
            val argon2Obj = obj.getJSONObject("argon2")
            return VaultHeader(
                kdfSalt = Base64.decode(obj.getString("kdfSalt"), Base64.NO_WRAP),
                argon2 = Argon2Params(
                    tCostIterations = argon2Obj.getInt("t"),
                    mCostKibibytes = argon2Obj.getInt("m"),
                    parallelism = argon2Obj.getInt("p"),
                    keyLengthBytes = argon2Obj.getInt("len")
                ),
                wrappedDek = obj.getString("wrappedDek"),
                canary = obj.getString("canary"),
                recoveryEnabled = obj.optBoolean("recoveryEnabled", false),
                recoveryWrappedDek = obj.optString("recoveryWrappedDek", null),
                recoveryKeyEncrypted = obj.optString("recoveryKeyEncrypted", null),
                recoveryPhone = obj.optString("recoveryPhone", null),
                recoveryEmail = obj.optString("recoveryEmail", null),
                smtpConfigEncrypted = obj.optString("smtpConfigEncrypted", null),
                recoveryKitEnabled = obj.optBoolean("recoveryKitEnabled", false),
                recoveryKitWrappedDek = obj.optString("recoveryKitWrappedDek", null)
            )
        }

        fun save(context: Context, header: VaultHeader) {
            file(context).writeText(header.toJson())
        }
    }
}
