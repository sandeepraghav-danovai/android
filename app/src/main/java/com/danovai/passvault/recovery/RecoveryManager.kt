package com.danovai.passvault.recovery

import android.content.Context
import android.telephony.SmsManager
import android.util.Base64
import com.danovai.passvault.crypto.CryptoManager
import com.danovai.passvault.crypto.EncryptedBlob
import com.danovai.passvault.crypto.KeystoreHelper
import com.danovai.passvault.data.VaultHeader
import com.danovai.passvault.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Properties
import javax.mail.Message
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

sealed class RecoveryOutcome {
    object Success : RecoveryOutcome()
    object InvalidCodes : RecoveryOutcome()
    object Expired : RecoveryOutcome()
    object TooManyAttempts : RecoveryOutcome()
    data class Error(val message: String) : RecoveryOutcome()
}

/**
 * Dual-channel (SMS + email) approval gate for a forgotten-password reset.
 *
 * This does NOT cryptographically derive the unlock key from the OTPs (that would require a
 * server; a purely offline app cannot verify an ephemeral shared secret without pre-agreeing on
 * it). Instead, a persistent RecoveryKey is created once when recovery is enabled and kept
 * device-bound behind Android Keystore; entering both fresh OTPs is an app-level gate that must
 * pass before that key is ever touched. On a device an attacker fully controls (root, unlocked
 * phone with your email already logged in) this gate can be bypassed — it protects against
 * someone else attempting recovery, not against a fully compromised device.
 *
 * The one-time recovery key (see VaultRepository.generateRecoveryKit /
 * unlockWithRecoveryKit) is the alternative for that stronger case: its raw bytes are never
 * stored on the device at all, so possessing it is sufficient on its own — no phone, email,
 * or even this device required, just the saved key plus a copy of the vault files.
 */
class RecoveryManager(private val context: Context, private val session: SessionManager) {

    private val otpCharset = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0/O/1/I
    private val secureRandom = SecureRandom()

    private var pendingOtpSms: String? = null
    private var pendingOtpEmail: String? = null
    private var issuedAtMillis: Long = 0
    private var attemptsRemaining: Int = MAX_ATTEMPTS

    fun isRecoveryAvailable(): Boolean =
        VaultHeader.exists(context) && VaultHeader.load(context).recoveryEnabled

    fun recoveryContactHints(): Pair<String?, String?> {
        val header = VaultHeader.load(context)
        return header.recoveryPhone?.let { maskPhone(it) } to header.recoveryEmail?.let { maskEmail(it) }
    }

    private fun generateOtp(): String = (1..OTP_LENGTH).map { otpCharset[secureRandom.nextInt(otpCharset.length)] }.joinToString("")

    /** Generates two fresh OTPs and dispatches them over SMS and email. */
    suspend fun requestRecovery(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val header = VaultHeader.load(context)
            val phone = header.recoveryPhone ?: return@withContext Result.failure(IllegalStateException("No recovery phone configured"))
            val email = header.recoveryEmail ?: return@withContext Result.failure(IllegalStateException("No recovery email configured"))
            val smtpEncrypted = header.smtpConfigEncrypted ?: return@withContext Result.failure(IllegalStateException("No SMTP config"))

            val otpA = generateOtp()
            val otpB = generateOtp()

            sendSms(phone, "PassVault recovery code: $otpA (valid ${VALIDITY_MINUTES} min). Ignore if you didn't request this.")
            sendEmail(smtpEncrypted, email, otpB)

            pendingOtpSms = otpA
            pendingOtpEmail = otpB
            issuedAtMillis = System.currentTimeMillis()
            attemptsRemaining = MAX_ATTEMPTS
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Verifies both codes; on success, unlocks the session (caller must then set a new master password). */
    fun verify(enteredSmsCode: String, enteredEmailCode: String): RecoveryOutcome {
        val expectedSms = pendingOtpSms
        val expectedEmail = pendingOtpEmail
        if (expectedSms == null || expectedEmail == null) return RecoveryOutcome.Error("No recovery in progress")

        if (System.currentTimeMillis() - issuedAtMillis > VALIDITY_MINUTES * 60_000L) {
            clearPending()
            return RecoveryOutcome.Expired
        }

        if (attemptsRemaining <= 0) return RecoveryOutcome.TooManyAttempts

        val matches = expectedSms.equals(enteredSmsCode.trim(), ignoreCase = true) &&
            expectedEmail.equals(enteredEmailCode.trim(), ignoreCase = true)

        if (!matches) {
            attemptsRemaining--
            return RecoveryOutcome.InvalidCodes
        }

        return try {
            val header = VaultHeader.load(context)
            val recoveryKeyEncrypted = header.recoveryKeyEncrypted ?: return RecoveryOutcome.Error("Recovery not configured")
            val recoveryWrappedDek = header.recoveryWrappedDek ?: return RecoveryOutcome.Error("Recovery not configured")

            val recoveryKeyBytes = Base64.decode(KeystoreHelper.decryptText(recoveryKeyEncrypted), Base64.NO_WRAP)
            val dek = CryptoManager.decrypt(recoveryKeyBytes, EncryptedBlob.fromStorageString(recoveryWrappedDek))
            CryptoManager.wipe(recoveryKeyBytes)

            session.unlock(dek)
            clearPending()
            RecoveryOutcome.Success
        } catch (e: Exception) {
            RecoveryOutcome.Error(e.message ?: "Recovery failed")
        }
    }

    private fun clearPending() {
        pendingOtpSms = null
        pendingOtpEmail = null
    }

    @Suppress("DEPRECATION")
    private fun sendSms(phone: String, message: String) {
        val smsManager = SmsManager.getDefault()
        val parts = smsManager.divideMessage(message)
        smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
    }

    private fun sendEmail(smtpEncrypted: String, toEmail: String, code: String) {
        val config = JSONObject(KeystoreHelper.decryptText(smtpEncrypted))
        val host = config.getString("host")
        val port = config.getInt("port")
        val username = config.getString("username")
        val appPassword = config.getString("appPassword")

        val props = Properties().apply {
            put("mail.smtp.host", host)
            put("mail.smtp.port", port.toString())
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.ssl.trust", host)
        }
        val mailSession = Session.getInstance(props)
        val message = MimeMessage(mailSession).apply {
            setFrom(InternetAddress(username))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail))
            subject = "PassVault recovery code"
            setText("Your PassVault recovery code is: $code\n\nIt is valid for $VALIDITY_MINUTES minutes. Ignore this email if you did not request it.")
        }
        Transport.send(message, username, appPassword)
    }

    private fun maskPhone(phone: String): String = if (phone.length > 4) "•••${phone.takeLast(4)}" else "••••"

    private fun maskEmail(email: String): String {
        val at = email.indexOf('@')
        if (at <= 1) return "••••"
        return "${email.first()}•••${email.substring(at)}"
    }

    companion object {
        const val OTP_LENGTH = 8
        const val VALIDITY_MINUTES = 10
        const val MAX_ATTEMPTS = 5
    }
}
