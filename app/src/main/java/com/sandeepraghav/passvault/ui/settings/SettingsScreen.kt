package com.sandeepraghav.passvault.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandeepraghav.passvault.VaultApplication
import com.sandeepraghav.passvault.data.VaultHeader
import com.sandeepraghav.passvault.ui.components.PasswordOutlinedField
import com.sandeepraghav.passvault.ui.vaultViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(private val app: VaultApplication) : ViewModel() {
    data class UiState(
        val recoveryEnabled: Boolean = false,
        val recoveryKitEnabled: Boolean = false,
        val newPassword: String = "",
        val confirmPassword: String = "",
        val message: String? = null,
        val recoveryPhone: String = "",
        val recoveryEmail: String = "",
        val smtpHost: String = "smtp.gmail.com",
        val smtpPort: String = "587",
        val smtpUsername: String = "",
        val smtpAppPassword: String = ""
    )

    private val _state = MutableStateFlow(
        UiState(recoveryEnabled = loadRecoveryEnabled(), recoveryKitEnabled = app.repository.isRecoveryKitEnabled())
    )
    val state: StateFlow<UiState> = _state

    private fun loadRecoveryEnabled(): Boolean =
        if (VaultHeader.exists(app)) VaultHeader.load(app).recoveryEnabled else false

    fun refreshRecoveryKitStatus() {
        update { it.copy(recoveryKitEnabled = app.repository.isRecoveryKitEnabled()) }
    }

    fun disableRecoveryKit() {
        app.repository.disableRecoveryKit()
        update { it.copy(recoveryKitEnabled = false, message = "Recovery key disabled.") }
    }

    fun update(transform: (UiState) -> UiState) { _state.value = transform(_state.value) }

    fun changePassword() {
        val s = _state.value
        if (s.newPassword.length < 10 || s.newPassword != s.confirmPassword) {
            update { it.copy(message = "Passwords must match and be at least 10 characters.") }
            return
        }
        viewModelScope.launch {
            app.repository.changeMasterPassword(s.newPassword.toCharArray())
            update { it.copy(newPassword = "", confirmPassword = "", message = "Master password updated.") }
        }
    }

    fun enableRecovery() {
        val s = _state.value
        if (s.recoveryPhone.isBlank() || s.recoveryEmail.isBlank() || s.smtpUsername.isBlank() || s.smtpAppPassword.isBlank()) {
            update { it.copy(message = "Fill in phone, email, and SMTP credentials.") }
            return
        }
        viewModelScope.launch {
            app.repository.enableRecovery(
                s.recoveryPhone.trim(), s.recoveryEmail.trim(),
                s.smtpHost.trim(), s.smtpPort.trim().toIntOrNull() ?: 587,
                s.smtpUsername.trim(), s.smtpAppPassword
            )
            update { it.copy(recoveryEnabled = true, message = "Recovery enabled.") }
        }
    }

    fun disableRecovery() {
        app.repository.disableRecovery()
        update { it.copy(recoveryEnabled = false, message = "Recovery disabled.") }
    }

    fun lockNow() = app.sessionManager.lock()

    fun exportBackup(context: android.content.Context) {
        viewModelScope.launch {
            val files = app.repository.vaultFilesForBackup()
            shareBackup(context, files)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onLocked: () -> Unit, onRegenerateRecoveryKey: () -> Unit) {
    val viewModel = vaultViewModel { SettingsViewModel(it) }
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val smsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.enableRecovery() else viewModel.update { it.copy(message = "SMS permission is required to send the recovery code.") }
    }

    LaunchedEffect(Unit) { viewModel.refreshRecoveryKitStatus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Change master password", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            PasswordOutlinedField(
                value = state.newPassword,
                onValueChange = { v -> viewModel.update { it.copy(newPassword = v) } },
                label = "New master password",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            PasswordOutlinedField(
                value = state.confirmPassword,
                onValueChange = { v -> viewModel.update { it.copy(confirmPassword = v) } },
                label = "Confirm new password",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = viewModel::changePassword, modifier = Modifier.fillMaxWidth()) {
                Text("Update password")
            }

            Spacer(Modifier.height(24.dp))
            Divider()
            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text("SMS + email recovery", style = MaterialTheme.typography.titleMedium)
                Switch(checked = state.recoveryEnabled, onCheckedChange = { if (!it) viewModel.disableRecovery() })
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "If you forget your master password, PassVault sends a code to this phone and this email. Both are required to reset it. This is weaker than your master password itself — pick a device and email you trust.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))

            if (!state.recoveryEnabled) {
                OutlinedTextField(
                    value = state.recoveryPhone,
                    onValueChange = { v -> viewModel.update { it.copy(recoveryPhone = v) } },
                    label = { Text("Recovery phone (this device's number)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.recoveryEmail,
                    onValueChange = { v -> viewModel.update { it.copy(recoveryEmail = v) } },
                    label = { Text("Recovery email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text("SMTP sender (used only to send the recovery email)", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = state.smtpHost,
                    onValueChange = { v -> viewModel.update { it.copy(smtpHost = v) } },
                    label = { Text("SMTP host") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.smtpPort,
                    onValueChange = { v -> viewModel.update { it.copy(smtpPort = v) } },
                    label = { Text("SMTP port") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.smtpUsername,
                    onValueChange = { v -> viewModel.update { it.copy(smtpUsername = v) } },
                    label = { Text("SMTP username (your email address)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                PasswordOutlinedField(
                    value = state.smtpAppPassword,
                    onValueChange = { v -> viewModel.update { it.copy(smtpAppPassword = v) } },
                    label = "SMTP app password",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { smsPermissionLauncher.launch(android.Manifest.permission.SEND_SMS) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enable recovery")
                }
            }

            Spacer(Modifier.height(24.dp))
            Divider()
            Spacer(Modifier.height(24.dp))

            Text("One-time recovery key", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                if (state.recoveryKitEnabled)
                    "A recovery key is active. It works on its own, on any device, as long as you still have it saved — it doesn't depend on SMS or email."
                else
                    "Generates a key shown to you exactly once. Save it somewhere safe (password manager, printed copy) — it recovers your vault on its own, no phone or email needed.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            if (state.recoveryKitEnabled) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onRegenerateRecoveryKey, modifier = Modifier.weight(1f)) {
                        Text("Regenerate")
                    }
                    OutlinedButton(onClick = viewModel::disableRecoveryKit, modifier = Modifier.weight(1f)) {
                        Text("Turn off")
                    }
                }
            } else {
                Button(onClick = onRegenerateRecoveryKey, modifier = Modifier.fillMaxWidth()) {
                    Text("Generate recovery key")
                }
            }

            Spacer(Modifier.height(24.dp))
            Divider()
            Spacer(Modifier.height(24.dp))

            Text("Backup", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Exports the encrypted vault files as-is — still unreadable without your master password. Save them to Drive or wherever you keep backups.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { viewModel.exportBackup(context) }, modifier = Modifier.fillMaxWidth()) {
                Text("Export vault backup")
            }

            Spacer(Modifier.height(24.dp))
            Divider()
            Spacer(Modifier.height(24.dp))

            OutlinedButton(
                onClick = { viewModel.lockNow(); onLocked() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Lock now")
            }

            state.message?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun shareBackup(context: android.content.Context, files: List<File>) {
    val uris = files.filter { it.exists() }.map {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
    }
    if (uris.isEmpty()) return
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "application/octet-stream"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export PassVault backup"))
}
