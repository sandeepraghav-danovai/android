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

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
