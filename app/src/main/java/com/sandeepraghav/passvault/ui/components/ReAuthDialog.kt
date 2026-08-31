package com.sandeepraghav.passvault.ui.components

import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sandeepraghav.passvault.crypto.BiometricKeystore
import com.sandeepraghav.passvault.ui.rememberVaultApp
import kotlinx.coroutines.launch

/**
 * Gate in front of revealing or copying a single password.
 *
 * Biometrics are offered whenever biometric unlock is switched on, and the prompt is raised
 * automatically so the common path is "tap Show, touch sensor". The master password stays a
 * first-class alternative rather than a fallback of last resort: a sensor that refuses to read
 * must never be able to lock someone out of their own entry.
 */
@Composable
fun ReAuthDialog(reason: String, onDismiss: () -> Unit, onVerified: () -> Unit) {
    val app = rememberVaultApp()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = LocalFragmentActivity.current ?: context.findFragmentActivity()

    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var biometricOffered by remember {
        mutableStateOf(app.repository.isBiometricReAuthAvailable() && activity != null && BiometricAuth.isReady(context))
    }

    val promptForBiometric: () -> Unit = {
        val iv = app.repository.biometricIv()
        if (activity == null || iv == null) {
            biometricOffered = false
        } else {
            try {
                val cipher = BiometricKeystore.decryptCipher(iv)
                BiometricAuth.authenticate(
                    activity = activity,
                    cipher = cipher,
                    title = "Confirm it's you",
                    subtitle = reason,
                    negativeLabel = "Use master password",
                    onSuccess = { authenticated ->
                        if (app.repository.verifyBiometric(authenticated)) {
                            onVerified()
                        } else {
                            error = "Biometric check didn't match this vault. Use your master password."
                        }
                    },
                    onFailed = { message -> error = message }
                )
            } catch (e: KeyPermanentlyInvalidatedException) {
                // Biometrics were re-enrolled since setup; the key is gone by design.
                app.repository.disableBiometricUnlock()
                biometricOffered = false
                error = "Biometrics changed on this device, so biometric unlock was turned off. Use your master password."
            } catch (e: Exception) {
                biometricOffered = false
            }
        }
    }

    // Raise the prompt as the dialog appears, so the usual case needs no extra tap.
    LaunchedEffect(Unit) {
        if (biometricOffered) promptForBiometric()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (biometricOffered) "Confirm it's you" else "Confirm master password") },
        text = {
            Column {
                Text(reason)
                if (biometricOffered) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = promptForBiometric, modifier = Modifier.fillMaxWidth()) {
                        Text("Use biometrics")
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("or enter your master password", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                PasswordOutlinedField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = "Master password",
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    if (app.repository.verifyMasterPassword(password.toCharArray())) {
                        onVerified()
                    } else {
                        error = "Incorrect password."
                    }
                }
            }) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
