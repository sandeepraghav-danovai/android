package com.danovai.passvault.ui.category

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danovai.passvault.VaultApplication
import com.danovai.passvault.data.PasswordEntryEntity
import com.danovai.passvault.ui.vaultViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class CategoryViewModel(app: VaultApplication, private val categoryId: Long) : ViewModel() {
    val entries: StateFlow<List<PasswordEntryEntity>> = app.repository.observeEntriesForCategory(categoryId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    categoryId: Long,
    categoryName: String,
    onBack: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onAddEntry: () -> Unit
) {
    val viewModel = vaultViewModel { CategoryViewModel(it, categoryId) }
    val entries by viewModel.entries.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(categoryName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddEntry) {
                Icon(Icons.Filled.Add, contentDescription = "Add entry")
            }
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("Nothing saved in $categoryName yet.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp), modifier = Modifier.fillMaxSize().padding(padding)) {
                items(entries, key = { it.id }) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.title) },
                        supportingContent = { Text(entry.username) },
                        leadingContent = { Icon(Icons.Filled.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth().clickable { onOpenEntry(entry.id) }
                    )
                }
            }
        }
    }
}
