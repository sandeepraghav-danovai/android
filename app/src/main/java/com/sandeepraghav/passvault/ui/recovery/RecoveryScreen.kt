package com.sandeepraghav.passvault.ui.recovery

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
import com.sandeepraghav.passvault.VaultApplication
import com.sandeepraghav.passvault.recovery.RecoveryOutcome
import com.sandeepraghav.passvault.ui.components.PasswordOutlinedField
import com.sandeepraghav.passvault.ui.vaultViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private enum class Step { INTRO, ENTER_CODES, NEW_PASSWORD, DONE }

class RecoveryViewModel(private val app: VaultApplication) : ViewModel() {
    data class UiState(
        val step: Step = Step.INTRO,
        val smsHint: String? = null,
        val emailHint: String? = null,
        val smsCode: String = "",
        val emailCode: String = "",
        val newPassword: String = "",
        val confirmPassword: String = "",
        val error: String? = null,
        val busy: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    init {
        val (phone, email) = app.recoveryManager.recoveryContactHints()
        _state.value = _state.value.copy(smsHint = phone, emailHint = email)
    }

    fun sendCodes() {
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            val result = app.recoveryManager.requestRecovery()
            _state.value = if (result.isSuccess) {
                _state.value.copy(busy = false, step = Step.ENTER_CODES)
            } else {
                _state.value.copy(busy = false, error = result.exceptionOrNull()?.message ?: "Could not send codes.")
            }
        }
    }

    fun onSmsCodeChange(v: String) { _state.value = _state.value.copy(smsCode = v, error = null) }
    fun onEmailCodeChange(v: String) { _state.value = _state.value.copy(emailCode = v, error = null) }

    fun verifyCodes() {
        val outcome = app.recoveryManager.verify(_state.value.smsCode, _state.value.emailCode)
        _state.value = when (outcome) {
            RecoveryOutcome.Success -> _state.value.copy(step = Step.NEW_PASSWORD, error = null)
            RecoveryOutcome.InvalidCodes -> _state.value.copy(error = "One or both codes are wrong.")
            RecoveryOutcome.Expired -> _state.value.copy(error = "Codes expired — request new ones.", step = Step.INTRO)
            RecoveryOutcome.TooManyAttempts -> _state.value.copy(error = "Too many attempts. Request new codes.", step = Step.INTRO)
            is RecoveryOutcome.Error -> _state.value.copy(error = outcome.message)
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
            _state.value = s.copy(busy = false, step = Step.DONE)
            onDone()
        }
    }
}

@Composable
fun RecoveryScreen(onRecovered: () -> Unit, onCancel: () -> Unit) {
    val viewModel = vaultViewModel { RecoveryViewModel(it) }
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Recover your vault", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        when (state.step) {
            Step.INTRO -> {
                Text(
                    "We'll send a code to your recovery phone (${state.smsHint ?: "not set"}) and another to your recovery email (${state.emailHint ?: "not set"}). Both are required to reset your master password.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Your data stays exactly as it is — this only changes the password used to unlock it.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = viewModel::sendCodes, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.busy) "Sending…" else "Send recovery codes")
                }
            }

            Step.ENTER_CODES -> {
                OutlinedTextField(
                    value = state.smsCode,
                    onValueChange = viewModel::onSmsCodeChange,
                    label = { Text("Code from SMS") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.emailCode,
                    onValueChange = viewModel::onEmailCodeChange,
                    label = { Text("Code from email") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = viewModel::verifyCodes, modifier = Modifier.fillMaxWidth()) {
                    Text("Verify")
                }
            }

            Step.NEW_PASSWORD -> {
                Text("Both codes verified. Set a new master password.", style = MaterialTheme.typography.bodyMedium)
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

            Step.DONE -> {
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
