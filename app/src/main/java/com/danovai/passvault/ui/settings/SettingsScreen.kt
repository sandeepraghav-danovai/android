package com.danovai.passvault.ui.settings

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import com.danovai.passvault.ui.components.BiometricAuth
import com.danovai.passvault.ui.components.LocalFragmentActivity
import com.danovai.passvault.ui.components.findFragmentActivity
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import kotlinx.coroutines.withContext
import com.danovai.passvault.R
import com.danovai.passvault.ui.components.DanovAiLockup
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danovai.passvault.VaultApplication
import com.danovai.passvault.data.VaultHeader
import com.danovai.passvault.ui.components.PasswordOutlinedField
import com.danovai.passvault.ui.vaultViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(private val app: VaultApplication) : ViewModel() {
    data class UiState(
        val recoveryEnabled: Boolean = false,
        val recoveryKitEnabled: Boolean = false,
        val biometricEnabled: Boolean = false,
        val resetConfirmText: String = "",
        val importResult: com.danovai.passvault.repository.VaultRepository.CsvImportResult? = null,
        val importing: Boolean = false,
        val showResetDialog: Boolean = false,
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
        UiState(
            recoveryEnabled = loadRecoveryEnabled(),
            recoveryKitEnabled = app.repository.isRecoveryKitEnabled(),
            biometricEnabled = app.repository.isBiometricEnabled()
        )
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

    // ------------------------------------------------------------ biometric unlock

    fun onBiometricEnabled(cipher: javax.crypto.Cipher) {
        runCatching { app.repository.enableBiometricUnlock(cipher) }
            .onSuccess { update { it.copy(biometricEnabled = true, message = "Biometric unlock enabled.") } }
            .onFailure { error -> update { it.copy(message = "Couldn't enable biometric unlock: ${error.message}") } }
    }

    fun disableBiometric() {
        app.repository.disableBiometricUnlock()
        update { it.copy(biometricEnabled = false, message = "Biometric unlock disabled.") }
    }

    fun showMessage(message: String) = update { it.copy(message = message) }

    // ------------------------------------------------------------ bulk CSV import

    fun importCsv(context: android.content.Context, uri: android.net.Uri) {
        update { it.copy(importing = true, message = null) }
        viewModelScope.launch {
            val result = runCatching {
                val text = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        // guard against being handed something enormous by the picker
                        val bytes = stream.readBytes()
                        if (bytes.size > MAX_IMPORT_BYTES) null else String(bytes, Charsets.UTF_8)
                    }
                }
                when (text) {
                    null -> com.danovai.passvault.repository.VaultRepository.CsvImportResult(
                        fatalError = "Couldn't read that file, or it is larger than ${MAX_IMPORT_BYTES / (1024 * 1024)} MB."
                    )
                    else -> app.repository.importEntriesFromCsv(text)
                }
            }.getOrElse { error ->
                com.danovai.passvault.repository.VaultRepository.CsvImportResult(fatalError = "Import failed: ${error.message}")
            }
            update { it.copy(importing = false, importResult = result) }
        }
    }

    /** The picker backgrounds us; keep the session so the import can actually run on return. */
    fun aboutToOpenFilePicker() = app.sessionManager.expectDeliberateBackground()

    fun dismissImportResult() = update { it.copy(importResult = null) }

    /** Writes the template to cache and hands it to the share sheet. */
    fun shareImportTemplate(context: android.content.Context) {
        runCatching {
            val dir = java.io.File(context.cacheDir, "shared").apply { mkdirs() }
            val file = java.io.File(dir, com.danovai.passvault.data.CsvTemplate.FILE_NAME)
            file.writeText(com.danovai.passvault.data.CsvTemplate.sample())
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Save import template"))
        }.onFailure { error ->
            update { it.copy(message = "Couldn't create the template: ${error.message}") }
        }
    }

    // ------------------------------------------------------------ destructive reset

    fun openResetDialog() = update { it.copy(showResetDialog = true, resetConfirmText = "") }
    fun dismissResetDialog() = update { it.copy(showResetDialog = false, resetConfirmText = "") }
    fun onResetConfirmTextChange(value: String) = update { it.copy(resetConfirmText = value) }

    /** Only fires on an exact match, so the gesture can't be completed by accident. */
    fun confirmReset(onReset: () -> Unit) {
        if (_state.value.resetConfirmText.trim() != RESET_CONFIRM_PHRASE) {
            update { it.copy(message = "Type $RESET_CONFIRM_PHRASE exactly to confirm.") }
            return
        }
        viewModelScope.launch {
            app.repository.resetVault()
            update { it.copy(showResetDialog = false, resetConfirmText = "") }
            onReset()
        }
    }

    fun lockNow() = app.sessionManager.lock()

    companion object {
        const val RESET_CONFIRM_PHRASE = "DELETE"
        const val MAX_IMPORT_BYTES = 5 * 1024 * 1024
    }

    fun exportBackup(context: android.content.Context) {
        viewModelScope.launch {
            val files = app.repository.vaultFilesForBackup()
            shareBackup(context, files)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLocked: () -> Unit,
    onRegenerateRecoveryKey: () -> Unit,
    onVaultReset: () -> Unit
) {
    val viewModel = vaultViewModel { SettingsViewModel(it) }
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val csvPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importCsv(context, uri)
    }
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

            // ---------------------------------------------------------------- biometric unlock
            val activity = LocalFragmentActivity.current ?: context.findFragmentActivity()
            val biometricStatus = BiometricAuth.availability(context)

            Text("Biometric unlock", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                when (biometricStatus) {
                    is BiometricAuth.Availability.Ready ->
                        "Unlock with your fingerprint or face instead of typing the master password. " +
                            "Your master password still works, and is still required after a restart or " +
                            "if you enrol a new fingerprint."
                    is BiometricAuth.Availability.NotEnrolled ->
                        "No fingerprint or face is enrolled on this device yet. Add one in Android Settings first."
                    is BiometricAuth.Availability.NoHardware ->
                        "This device has no usable biometric hardware."
                    is BiometricAuth.Availability.Unavailable -> biometricStatus.reason
                },
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Use biometric unlock", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = state.biometricEnabled,
                    enabled = biometricStatus is BiometricAuth.Availability.Ready && activity != null,
                    onCheckedChange = { wantEnabled ->
                        if (!wantEnabled) {
                            viewModel.disableBiometric()
                        } else if (activity == null) {
                            viewModel.showMessage("Biometric unlock isn't available here.")
                        } else {
                            try {
                                val cipher = com.danovai.passvault.crypto.BiometricKeystore.encryptCipher()
                                BiometricAuth.authenticate(
                                    activity = activity,
                                    cipher = cipher,
                                    title = "Enable biometric unlock",
                                    subtitle = "Confirm it's you to link this vault to your biometrics",
                                    negativeLabel = "Cancel",
                                    onSuccess = { authenticated -> viewModel.onBiometricEnabled(authenticated) },
                                    onFailed = { message -> message?.let(viewModel::showMessage) }
                                )
                            } catch (e: Exception) {
                                viewModel.showMessage("Couldn't set up biometric unlock on this device.")
                            }
                        }
                    }
                )
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

            // ---------------------------------------------------------------- bulk import
            Spacer(Modifier.height(24.dp))
            Divider()
            Spacer(Modifier.height(24.dp))

            Text("Import from CSV", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Bulk-add entries from a spreadsheet. Columns: " +
                    com.danovai.passvault.data.CsvTemplate.COLUMNS.joinToString(", ") +
                    ". Order doesn't matter and headers are case-insensitive; only title and " +
                    "password are required. Missing categories are created, and rows that already " +
                    "exist (same category, title and username) are skipped.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "A CSV holds your passwords in the clear. Delete the file once the import is done, " +
                    "and be careful where you store it in the meantime.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.shareImportTemplate(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Get template CSV")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { viewModel.aboutToOpenFilePicker(); csvPickerLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*")) },
                enabled = !state.importing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.importing) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp))
                    Spacer(Modifier.height(0.dp))
                    Text("  Importing…")
                } else {
                    Text("Choose CSV file")
                }
            }

            state.importResult?.let { result ->
                AlertDialog(
                    onDismissRequest = viewModel::dismissImportResult,
                    title = { Text(if (result.fatalError != null) "Import failed" else "Import finished") },
                    text = {
                        Column {
                            if (result.fatalError != null) {
                                Text(result.fatalError!!, style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("Added ${result.imported} " + if (result.imported == 1) "entry." else "entries.")
                                if (result.skippedDuplicates > 0) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Skipped ${result.skippedDuplicates} already in the vault.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (result.categoriesCreated.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "New categories: ${result.categoriesCreated.distinct().joinToString(", ")}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (result.errors.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "${result.errors.size} row(s) skipped:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    // cap the list so one bad file cannot produce an endless dialog
                                    result.errors.take(8).forEach {
                                        Text(it, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (result.errors.size > 8) {
                                        Text(
                                            "…and ${result.errors.size - 8} more.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = viewModel::dismissImportResult) { Text("Done") }
                    }
                )
            }

            // ---------------------------------------------------------------- danger zone
            Spacer(Modifier.height(24.dp))
            Divider()
            Spacer(Modifier.height(24.dp))

            Text(
                "Reset vault",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Deletes the vault and every password in it, then starts over from a new master " +
                    "password. Use this if you've forgotten your master password and have no recovery " +
                    "key — there is no way to get the existing entries back, and old exported backups " +
                    "stay locked to the old password.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = viewModel::openResetDialog,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Delete vault and start over")
            }

            if (state.showResetDialog) {
                AlertDialog(
                    onDismissRequest = viewModel::dismissResetDialog,
                    title = { Text("Delete this vault?") },
                    text = {
                        Column {
                            Text(
                                "This erases the vault file, the database, and every escrowed copy of " +
                                    "the key — including biometric unlock and any recovery key. It cannot " +
                                    "be undone.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Type ${SettingsViewModel.RESET_CONFIRM_PHRASE} to confirm:",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = state.resetConfirmText,
                                onValueChange = viewModel::onResetConfirmTextChange,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = { viewModel.confirmReset(onVaultReset) },
                            enabled = state.resetConfirmText.trim() == SettingsViewModel.RESET_CONFIRM_PHRASE
                        ) {
                            Text("Delete vault", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = viewModel::dismissResetDialog) { Text("Cancel") }
                    }
                )
            }

            state.message?.let {
                Spacer(Modifier.height(16.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(32.dp))
            Divider()
            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DanovAiLockup(width = 180.dp)
                Spacer(Modifier.height(12.dp))
                val versionName = remember {
                    runCatching {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    }.getOrNull()
                }
                Text(
                    stringResource(R.string.app_name) + (versionName?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(16.dp))
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
