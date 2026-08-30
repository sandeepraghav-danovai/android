package com.sandeepraghav.passvault.ui.entry

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sandeepraghav.passvault.VaultApplication
import com.sandeepraghav.passvault.ui.components.PasswordOutlinedField
import com.sandeepraghav.passvault.ui.vaultViewModel
import com.sandeepraghav.passvault.util.PasswordGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AddEditEntryViewModel(private val app: VaultApplication, private val categoryId: Long, private val entryId: Long?) : ViewModel() {
    data class UiState(
        val title: String = "",
        val username: String = "",
        val password: String = "",
        val url: String = "",
        val notes: String = "",
        val loaded: Boolean = false
    )

    private val _state = MutableStateFlow(UiState(loaded = entryId == null))
    val state: StateFlow<UiState> = _state

    init {
        if (entryId != null) {
            viewModelScope.launch {
                app.repository.decryptEntry(entryId)?.let { e ->
                    _state.value = UiState(
                        title = e.title,
                        username = e.username,
                        password = e.password,
                        url = e.url.orEmpty(),
                        notes = e.notes.orEmpty(),
                        loaded = true
                    )
                }
            }
        }
    }

    fun update(transform: (UiState) -> UiState) { _state.value = transform(_state.value) }

    fun generatePassword() { update { it.copy(password = PasswordGenerator.generate()) } }

    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (s.title.isBlank() || s.password.isBlank()) return
        viewModelScope.launch {
            if (entryId == null) {
                app.repository.addEntry(categoryId, s.title.trim(), s.username.trim(), s.password, s.notes, s.url.trim())
            } else {
                app.repository.updateEntry(entryId, categoryId, s.title.trim(), s.username.trim(), s.password, s.notes, s.url.trim(), favorite = false)
            }
            onDone()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditEntryScreen(categoryId: Long, entryId: Long?, onBack: () -> Unit, onSaved: () -> Unit) {
    val viewModel = vaultViewModel { AddEditEntryViewModel(it, categoryId, entryId) }
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (entryId == null) "Add entry" else "Edit entry") },
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
            OutlinedTextField(
                value = state.title,
                onValueChange = { v -> viewModel.update { it.copy(title = v) } },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.username,
                onValueChange = { v -> viewModel.update { it.copy(username = v) } },
                label = { Text("Username / Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                PasswordOutlinedField(
                    value = state.password,
                    onValueChange = { v -> viewModel.update { it.copy(password = v) } },
                    label = "Password",
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = viewModel::generatePassword) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Generate password")
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.url,
                onValueChange = { v -> viewModel.update { it.copy(url = v) } },
                label = { Text("Website / App (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.notes,
                onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { viewModel.save(onSaved) }, modifier = Modifier.fillMaxWidth()) {
                Text("Save")
            }
        }
    }
}
