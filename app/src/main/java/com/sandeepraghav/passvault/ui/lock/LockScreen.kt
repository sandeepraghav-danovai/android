package com.sandeepraghav.passvault.ui.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandeepraghav.passvault.VaultApplication
import com.sandeepraghav.passvault.ui.components.BiometricAuth
import com.sandeepraghav.passvault.ui.components.LocalFragmentActivity
import com.sandeepraghav.passvault.ui.components.findFragmentActivity
import com.sandeepraghav.passvault.ui.components.DanovAiByline
import com.sandeepraghav.passvault.ui.components.LockGlyph
import com.sandeepraghav.passvault.ui.components.PasswordOutlinedField
import com.sandeepraghav.passvault.ui.vaultViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class LockViewModel(private val app: VaultApplication) : ViewModel() {
    data class UiState(val password: String = "", val error: String? = null, val checking: Boolean = false)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun onPasswordChange(value: String) { _state.value = _state.value.copy(password = value, error = null) }

    /** Unlocks from a BiometricPrompt-authenticated cipher. Falls back silently to the password field. */
    fun unlockWithBiometric(cipher: javax.crypto.Cipher, onSuccess: () -> Unit) {
        _state.value = _state.value.copy(checking = true, error = null)
        viewModelScope.launch {
            val ok = app.repository.unlockWithBiometric(cipher)
            if (ok) {
                _state.value = UiState()
                onSuccess()
            } else {
                _state.value = _state.value.copy(checking = false, error = "Biometric unlock failed. Use your master password.")
            }
        }
    }

    /**
     * Re-enrolling a fingerprint or face permanently invalidates the Keystore key, by design.
     * Clear the stale blob so the toggle reflects reality and the user can re-enable it.
     */
    fun onBiometricKeyInvalidated() {
        app.repository.disableBiometricUnlock()
        _state.value = _state.value.copy(
            checking = false,
            error = "Biometrics changed on this device, so biometric unlock was turned off. Use your master password, then re-enable it in Settings."
        )
    }

    fun showError(message: String) { _state.value = _state.value.copy(checking = false, error = message) }

    fun unlock(onSuccess: () -> Unit) {
        _state.value = _state.value.copy(checking = true, error = null)
        viewModelScope.launch {
            val ok = app.repository.unlock(_state.value.password.toCharArray())
            if (ok) {
                _state.value = UiState()
                onSuccess()
            } else {
                _state.value = _state.value.copy(checking = false, error = "Incorrect master password.")
            }
        }
    }
}

@Composable
fun LockScreen(onUnlocked: () -> Unit, onForgotPassword: () -> Unit, onUseRecoveryKey: () -> Unit) {
    val viewModel = vaultViewModel { LockViewModel(it) }
    val state by viewModel.state.collectAsState()
    val app = com.sandeepraghav.passvault.ui.rememberVaultApp()
    val recoveryAvailable = app.recoveryManager.isRecoveryAvailable()
    val recoveryKitAvailable = app.repository.isRecoveryKitEnabled()

    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = LocalFragmentActivity.current ?: context.findFragmentActivity()
    val biometricAvailable = app.repository.isBiometricEnabled() &&
        activity != null && BiometricAuth.isReady(context)

    val promptForBiometric: () -> Unit = {
        val iv = app.repository.biometricIv()
        if (activity == null || iv == null) {
            viewModel.showError("Biometric unlock isn't set up on this device.")
        } else {
            try {
                val cipher = com.sandeepraghav.passvault.crypto.BiometricKeystore.decryptCipher(iv)
                BiometricAuth.authenticate(
                    activity = activity,
                    cipher = cipher,
                    title = "Unlock PassVault",
                    subtitle = "Use your fingerprint or face to unlock the vault",
                    negativeLabel = "Use master password",
                    onSuccess = { authenticated -> viewModel.unlockWithBiometric(authenticated, onUnlocked) },
                    onFailed = { message -> message?.let(viewModel::showError) }
                )
            } catch (e: android.security.keystore.KeyPermanentlyInvalidatedException) {
                viewModel.onBiometricKeyInvalidated()
            } catch (e: Exception) {
                viewModel.showError("Biometric unlock is unavailable. Use your master password.")
            }
        }
    }

    // Offer the prompt straight away so the common case is a single tap-free unlock.
    androidx.compose.runtime.LaunchedEffect(biometricAvailable) {
        if (biometricAvailable) promptForBiometric()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DanovAiByline()
        Spacer(Modifier.height(20.dp))
        LockGlyph()
        Spacer(Modifier.height(16.dp))
        Text("PassVault is locked", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))

        PasswordOutlinedField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = "Master password",
            modifier = Modifier.fillMaxWidth()
        )

        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = { viewModel.unlock(onUnlocked) }, enabled = !state.checking, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.checking) "Unlocking…" else "Unlock")
        }

        if (biometricAvailable) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = promptForBiometric,
                enabled = !state.checking,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Unlock with biometrics")
            }
        }

        if (recoveryAvailable) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onForgotPassword) {
                Text("Forgot master password?")
            }
        }

        if (recoveryKitAvailable) {
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onUseRecoveryKey) {
                Text("Use my recovery key")
            }
        }
    }
}
