package com.danovai.passvault.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danovai.passvault.VaultApplication
import com.danovai.passvault.data.CategoryEntity
import com.danovai.passvault.ui.components.iconForCategory
import com.danovai.passvault.ui.vaultViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(private val app: VaultApplication) : ViewModel() {
    val categories: StateFlow<List<CategoryEntity>> = app.repository.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun countFor(categoryId: Long) = app.repository.observeEntryCount(categoryId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun addCategory(name: String, icon: String, colorHex: String) {
        viewModelScope.launch { app.repository.addCategory(name, icon, colorHex) }
    }

    fun lock() = app.sessionManager.lock()
}

private val availableIcons = listOf("bank", "apps", "language", "work", "email", "folder")
private val availableColors = listOf("#2E7D32", "#1565C0", "#6A1B9A", "#EF6C00", "#AD1457", "#00838F", "#5D4037")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenCategory: (Long, String) -> Unit,
    onOpenSettings: () -> Unit
) {
    val viewModel = vaultViewModel { HomeViewModel(it) }
    val categories by viewModel.categories.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PassVault") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add category")
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            items(categories, key = { it.id }) { category ->
                CategoryCard(category = category, viewModel = viewModel, onClick = { onOpenCategory(category.id, category.name) })
            }
        }
    }

    if (showAddDialog) {
        AddCategoryDialog(
            onDismiss = { showAddDialog = false },
            onCreate = { name, icon, color ->
                viewModel.addCategory(name, icon, color)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun CategoryCard(category: CategoryEntity, viewModel: HomeViewModel, onClick: () -> Unit) {
    val count by viewModel.countFor(category.id).collectAsState()
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().aspectRatio(1.1f),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(android.graphics.Color.parseColor(category.colorHex)).copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    iconForCategory(category.icon),
                    contentDescription = null,
                    tint = Color(android.graphics.Color.parseColor(category.colorHex))
                )
            }
            Column {
                Text(category.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("$count saved", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AddCategoryDialog(onDismiss: () -> Unit, onCreate: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf(availableIcons.first()) }
    var selectedColor by remember { mutableStateOf(availableColors.first()) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New category") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                Spacer(Modifier.height(12.dp))
                Text("Icon", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableIcons.forEach { icon ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (icon == selectedIcon) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                    CircleShape
                                )
                                .clickable { selectedIcon = icon },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(iconForCategory(icon), contentDescription = icon)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Color", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableColors.forEach { hex ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(android.graphics.Color.parseColor(hex)), CircleShape)
                                .clickable { selectedColor = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim(), selectedIcon, selectedColor) }
            ) { Text("Create") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
