package com.sandeepraghav.passvault.ui.components

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/**
 * Thin wrapper over BiometricPrompt. Only BIOMETRIC_STRONG (Class 3) is accepted, because a
 * weaker class cannot gate an Android Keystore key — and the whole design here depends on the
 * DEK being unusable until the hardware is satisfied. Device credential (PIN/pattern) is
 * deliberately not offered as a fallback: the master password already fills that role, and
 * accepting the device PIN would let anyone who can unlock the phone open the vault.
 */
/**
 * The hosting activity, supplied by MainActivity itself.
 *
 * BiometricPrompt needs a FragmentActivity. Resolving it by casting `LocalContext.current`
 * works but fails silently when it doesn't — the switch simply greys out with no error, which
 * is a miserable thing to debug. Passing the activity down with its compile-time type means a
 * mismatch becomes a build error instead of a mystery at runtime.
 */
val LocalFragmentActivity = staticCompositionLocalOf<FragmentActivity?> { null }

/**
 * Fallback for contexts that did not come through [LocalFragmentActivity]. Walks the
 * ContextWrapper chain to find the hosting activity.
 *
 * `LocalContext.current` is not guaranteed to be the Activity itself — it is frequently a
 * ContextThemeWrapper around it, and a plain `as?` cast then silently yields null. That failure
 * is invisible in a debug build and shows up as a permanently greyed-out biometric switch, so
 * unwrap properly rather than casting.
 */
fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context = this
    while (true) {
        when (current) {
            is FragmentActivity -> return current
            is ContextWrapper -> current = current.baseContext
            else -> return null
        }
    }
}

object BiometricAuth {

    private const val AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_STRONG

    sealed interface Availability {
        data object Ready : Availability
        data object NoHardware : Availability
        data object NotEnrolled : Availability
        data class Unavailable(val reason: String) : Availability
    }

    fun availability(context: Context): Availability =
        when (BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Availability.Ready
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NotEnrolled
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> Availability.NoHardware
            else -> Availability.Unavailable("Biometric unlock isn't available on this device.")
        }

    fun isReady(context: Context): Boolean = availability(context) is Availability.Ready

    /**
     * Prompts, then hands back the same [cipher] once the system has unlocked it. The caller
     * does the actual encrypt/decrypt — this object never sees the DEK.
     */
    fun authenticate(
        activity: FragmentActivity,
        cipher: Cipher,
        title: String,
        subtitle: String,
        negativeLabel: String,
        onSuccess: (Cipher) -> Unit,
        onFailed: (String?) -> Unit
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authenticated = result.cryptoObject?.cipher
                    if (authenticated == null) onFailed("Biometric result had no cipher attached.")
                    else onSuccess(authenticated)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // A deliberate cancel isn't an error worth surfacing as a failure message.
                    val cancelled = errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.ERROR_CANCELED
                    onFailed(if (cancelled) null else errString.toString())
                }
            }
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeLabel)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .setConfirmationRequired(false)
            .build()

        prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
    }
}
