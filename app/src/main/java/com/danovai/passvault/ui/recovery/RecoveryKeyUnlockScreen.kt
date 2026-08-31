package com.danovai.passvault.ui.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.danovai.passvault.VaultApplication
import com.danovai.passvault.ui.components.PasswordOutlinedField
import com.danovai.passvault.ui.vaultViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class KeyStep { ENTER_KEY, NEW_PASSWORD, DONE }

class RecoveryKeyUnlockViewModel(private val app: VaultApplication) : ViewModel() {
    data class UiState(
        val step: KeyStep = KeyStep.ENTER_KEY,
        val code: String = "",
        val newPassword: String = "",
        val confirmPassword: String = "",
        val error: String? = null,
        val busy: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun onCodeChange(v: String) { _state.value = _state.value.copy(code = v, error = null) }

    fun verifyKey() {
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            val ok = app.repository.unlockWithRecoveryKit(_state.value.code)
            _state.value = if (ok) {
                _state.value.copy(busy = false, step = KeyStep.NEW_PASSWORD)
            } else {
                _state.value.copy(busy = false, error = "That recovery key doesn't match this vault.")
            }
        }
    }

    fun onNewPasswordChange(v: String) { _state.value = _state.value.copy(newPassword = v, error = null) }
    fun onConfirmPasswordChange(v: String) { _state.value = _state.value.copy(confirmPassword = v, error = null) }

    fun setNewPassword(onDone: () -> Unit) {
        val s = _state.value
        if (s.newPassword.length < 10) {
            _state.value = s.copy(error = "Use at least 10 characters.")
            return
        }
        if (s.newPassword != s.confirmPassword) {
            _state.value = s.copy(error = "Passwords don't match.")
            return
        }
        _state.value = s.copy(busy = true)
        viewModelScope.launch {
            app.repository.setNewPasswordAfterRecovery(s.newPassword.toCharArray())
            _state.value = s.copy(busy = false, step = KeyStep.DONE)
            onDone()
        }
    }
}

@Composable
fun RecoveryKeyUnlockScreen(onRecovered: () -> Unit, onCancel: () -> Unit) {
    val viewModel = vaultViewModel { RecoveryKeyUnlockViewModel(it) }
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Recover with your key", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        when (state.step) {
            KeyStep.ENTER_KEY -> {
                Text(
                    "Enter the one-time recovery key you saved when you set this up.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = state.code,
                    onValueChange = viewModel::onCodeChange,
                    label = { Text("Recovery key") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = viewModel::verifyKey, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.busy) "Checking…" else "Unlock")
                }
            }

            KeyStep.NEW_PASSWORD -> {
                Text("Key verified. Set a new master password.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                PasswordOutlinedField(
                    value = state.newPassword,
                    onValueChange = viewModel::onNewPasswordChange,
                    label = "New master password",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                PasswordOutlinedField(
                    value = state.confirmPassword,
                    onValueChange = viewModel::onConfirmPasswordChange,
                    label = "Confirm new password",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { viewModel.setNewPassword(onRecovered) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.busy) "Resetting…" else "Reset password")
                }
            }

            KeyStep.DONE -> {
                Text("Done.")
            }
        }

        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onCancel) { Text("Cancel") }
    }
}
