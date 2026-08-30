package com.sandeepraghav.passvault.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The Keystore key that guards biometric unlock.
 *
 * Biometrics do not replace the master password and never derive the DEK. What actually
 * happens is: while the vault is already unlocked, the DEK is encrypted with a hardware-backed
 * AES key that carries `setUserAuthenticationRequired(true)`, so the key is unusable until a
 * fingerprint or face has satisfied the system. The wrapped blob goes in the vault header
 * ([com.sandeepraghav.passvault.data.VaultHeader.biometricWrappedDek]); the DEK itself is
 * still never written to disk in the clear.
 *
 * The key is created with `setInvalidatedByBiometricEnrollment(true)`, so enrolling a new
 * fingerprint or face permanently destroys it. That is deliberate: someone who can add their
 * own biometric to the device must not thereby inherit access to the vault. When it happens,
 * [decryptCipher] raises [KeyPermanentlyInvalidatedException] and the caller is expected to
 * turn the feature off and fall back to the master password.
 */
object BiometricKeystore {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "passvault_biometric_dek_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun hasKey(): Boolean = runCatching { keyStore().containsAlias(KEY_ALIAS) }.getOrDefault(false)

    fun deleteKey() {
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    /**
     * Drops any previous key and mints a fresh one, returning a cipher ready to encrypt the DEK.
     * Must be handed to BiometricPrompt as a CryptoObject — `doFinal` only works after the
     * prompt has authenticated the user.
     */
    fun encryptCipher(): Cipher {
        deleteKey()
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
        )
        val key = generator.generateKey()
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    /**
     * Cipher for unwrapping the DEK, bound to the IV the blob was sealed with.
     *
     * @throws KeyPermanentlyInvalidatedException if biometrics were re-enrolled since the key
     *   was created, or the device's secure lock screen was removed.
     */
    fun decryptCipher(iv: ByteArray): Cipher {
        val key = keyStore().getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw KeyPermanentlyInvalidatedException()
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        }
    }
}
