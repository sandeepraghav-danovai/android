package com.danovai.passvault.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.danovai.passvault.ui.components.DanovAiLockup
import com.danovai.passvault.ui.components.LockGlyph
import com.danovai.passvault.ui.components.PasswordOutlinedField
import com.danovai.passvault.ui.vaultViewModel
import com.danovai.passvault.util.PasswordStrength
import com.danovai.passvault.util.PasswordStrengthEstimator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SetupViewModel(private val app: VaultApplication) : ViewModel() {
    data class UiState(
        val password: String = "",
        val confirm: String = "",
        val error: String? = null,
        val creating: Boolean = false
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun onPasswordChange(value: String) { _state.value = _state.value.copy(password = value, error = null) }
    fun onConfirmChange(value: String) { _state.value = _state.value.copy(confirm = value, error = null) }

    fun strength(): PasswordStrength = PasswordStrengthEstimator.estimate(_state.value.password)

    fun createVault(onDone: () -> Unit) {
        val s = _state.value
        if (s.password.length < 10) {
            _state.value = s.copy(error = "Use at least 10 characters — this password protects everything.")
            return
        }
        if (s.password != s.confirm) {
            _state.value = s.copy(error = "Passwords don't match.")
            return
        }
        _state.value = s.copy(creating = true, error = null)
        viewModelScope.launch {
            app.repository.createVault(s.password.toCharArray())
            onDone()
        }
    }
}

@Composable
fun SetupScreen(onVaultCreated: () -> Unit) {
    val app = com.danovai.passvault.ui.rememberVaultApp()
    val viewModel = vaultViewModel { SetupViewModel(it) }
    val state by viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DanovAiLockup(width = 168.dp)
        Spacer(Modifier.height(20.dp))
        LockGlyph()
        Spacer(Modifier.height(16.dp))
        Text("Create your master password", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "This unlocks every password you store. There is no cloud reset — if you forget it, you'll rely on the SMS + email recovery you can set up next, or lose the vault.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))

        PasswordOutlinedField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = "Master password",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        val strength = viewModel.strength()
        if (state.password.isNotEmpty()) {
            LinearProgressIndicator(
                progress = { strengthProgress(strength) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(strengthLabel(strength), style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(16.dp))

        PasswordOutlinedField(
            value = state.confirm,
            onValueChange = viewModel::onConfirmChange,
            label = "Confirm master password",
            modifier = Modifier.fillMaxWidth()
        )

        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { viewModel.createVault(onVaultCreated) },
            enabled = !state.creating,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.creating) "Creating…" else "Create vault")
        }
    }
}

private fun strengthProgress(s: PasswordStrength): Float = when (s) {
    PasswordStrength.WEAK -> 0.25f
    PasswordStrength.FAIR -> 0.5f
    PasswordStrength.STRONG -> 0.75f
    PasswordStrength.VERY_STRONG -> 1f
}

private fun strengthLabel(s: PasswordStrength): String = when (s) {
    PasswordStrength.WEAK -> "Weak"
    PasswordStrength.FAIR -> "Fair"
    PasswordStrength.STRONG -> "Strong"
    PasswordStrength.VERY_STRONG -> "Very strong"
}
