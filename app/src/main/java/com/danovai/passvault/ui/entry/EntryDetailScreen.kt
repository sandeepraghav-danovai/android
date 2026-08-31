package com.danovai.passvault.ui.entry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danovai.passvault.VaultApplication
import com.danovai.passvault.repository.DecryptedEntry
import com.danovai.passvault.ui.components.ReAuthDialog
import com.danovai.passvault.ui.vaultViewModel
import com.danovai.passvault.util.ClipboardUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class EntryDetailViewModel(private val app: VaultApplication, private val entryId: Long) : ViewModel() {
    private val _entry = MutableStateFlow<DecryptedEntry?>(null)
    val entry: StateFlow<DecryptedEntry?> = _entry

    private val _passwordRevealed = MutableStateFlow(false)
    val passwordRevealed: StateFlow<Boolean> = _passwordRevealed

    init {
        viewModelScope.launch { _entry.value = app.repository.decryptEntry(entryId) }
    }

    fun reveal() { _passwordRevealed.value = true }
    fun hide() { _passwordRevealed.value = false }

    fun deleteEntry(onDone: () -> Unit) {
        viewModelScope.launch {
            app.repository.deleteEntryById(entryId)
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryDetailScreen(entryId: Long, onBack: () -> Unit, onEdit: (Long, Long) -> Unit) {
    val viewModel = vaultViewModel { EntryDetailViewModel(it, entryId) }
    val entry by viewModel.entry.collectAsState()
    val revealed by viewModel.passwordRevealed.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingAction by remember { mutableStateOf<PendingAction?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(revealed) {
        if (revealed) {
            kotlinx.coroutines.delay(20_000)
            viewModel.hide()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(entry?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    entry?.let { e ->
                        IconButton(onClick = { onEdit(e.categoryId, e.id) }) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                        IconButton(onClick = { showDeleteConfirm = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                    }
                }
            )
        }
    ) { padding ->
        entry?.let { e ->
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp)) {
                LabeledValue("Username / Email", e.username, showCopy = true) {
                    ClipboardUtil.copyThenAutoClear(context, "username", e.username, scope)
                }

                Spacer(Modifier.height(16.dp))

                Text("Password", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        if (revealed) e.password else "•".repeat(minOf(e.password.length, 16)),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (revealed) viewModel.hide() else pendingAction = PendingAction.REVEAL
                    }) {
                        Icon(Icons.Filled.Visibility, contentDescription = "Show")
                    }
                    IconButton(onClick = { pendingAction = PendingAction.COPY }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                    }
                }

                e.url?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(16.dp))
                    LabeledValue("Website / App", it, showCopy = false) {}
                }

                e.notes?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(16.dp))
                    LabeledValue("Notes", it, showCopy = false) {}
                }
            }
        }
    }

    if (pendingAction != null) {
        val action = pendingAction!!
        ReAuthDialog(
            reason = if (action == PendingAction.REVEAL) "Confirm it's you to view this password." else "Confirm it's you to copy this password.",
            onDismiss = { pendingAction = null },
            onVerified = {
                pendingAction = null
                when (action) {
                    PendingAction.REVEAL -> viewModel.reveal()
                    PendingAction.COPY -> entry?.let {
                        ClipboardUtil.copyThenAutoClear(context, "password", it.password, scope)
                    }
                }
            }
        )
    }

    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this entry?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.deleteEntry(onBack)
                }) { Text("Delete") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

private enum class PendingAction { REVEAL, COPY }

@Composable
private fun LabeledValue(label: String, value: String, showCopy: Boolean, onCopy: () -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (showCopy) {
                IconButton(onClick = onCopy) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                }
            }
        }
    }
}
