package com.danovai.passvault.ui.notes

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.danovai.passvault.data.NoteImageEntity
import com.danovai.passvault.notes.DecryptedNote
import com.danovai.passvault.ui.vaultViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    onBack: () -> Unit,
    onAddNote: () -> Unit,
    onEditNote: (Long) -> Unit
) {
    val viewModel = vaultViewModel { NotesViewModel(it) }
    val state by viewModel.state.collectAsState()

    // Coming back from the editor should show what changed.
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notes") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNote) {
                Icon(Icons.Filled.Add, contentDescription = "New note")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Search notes") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            if (state.allTags.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.allTags, key = { it.id }) { tag ->
                        FilterChip(
                            selected = tag.id in state.selectedTagIds,
                            onClick = { viewModel.toggleTag(tag.id) },
                            label = { Text(tag.name) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (state.query.isNotBlank() || state.selectedTagIds.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${state.notes.size} matching",
                        style = MaterialTheme.typography.labelMedium
                    )
                    TextButton(onClick = viewModel::clearFilters) { Text("Clear") }
                }
            }

            when {
                state.loading -> Text("Loading…", style = MaterialTheme.typography.bodySmall)
                state.notes.isEmpty() -> {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        if (state.query.isBlank() && state.selectedTagIds.isEmpty()) {
                            "No notes yet. Tap + to write one."
                        } else {
                            "Nothing matches that."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.notes, key = { it.id }) { note ->
                        NoteRow(note) { viewModel.open(note) }
                    }
                    item { Spacer(Modifier.height(72.dp)) }   // clear the FAB
                }
            }
        }
    }

    state.openNote?.let { note ->
        NotePopup(
            note = note,
            loadImage = { viewModel.imageBytes(it) },
            onEdit = { viewModel.closeOpenNote(); onEditNote(note.id) },
            onDelete = { viewModel.delete(note) },
            onDismiss = viewModel::closeOpenNote
        )
    }
}

@Composable
private fun NoteRow(note: DecryptedNote, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(note.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (note.body.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(note.snippet(), style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Edited ${formatWhen(note.updatedAt)}",
                    style = MaterialTheme.typography.labelSmall
                )
                if (note.images.isNotEmpty()) {
                    Text(
                        "  ·  ${note.images.size} image${if (note.images.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            if (note.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(note.tags, key = { it.id }) { tag ->
                        AssistChip(onClick = {}, label = { Text(tag.name) })
                    }
                }
            }
        }
    }
}

/** The note itself, opened over the list rather than as a separate screen. */
@Composable
private fun NotePopup(
    note: DecryptedNote,
    loadImage: suspend (NoteImageEntity) -> ByteArray?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(note.title) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "Edited ${formatWhen(note.updatedAt)}  ·  created ${formatWhen(note.createdAt)}",
                    style = MaterialTheme.typography.labelSmall
                )
                if (note.tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(note.tags, key = { it.id }) { tag ->
                            AssistChip(onClick = {}, label = { Text(tag.name) })
                        }
                    }
                }
                if (note.body.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(note.body, style = MaterialTheme.typography.bodyMedium)
                }
                note.images.forEach { image ->
                    Spacer(Modifier.height(12.dp))
                    NoteImage(image, loadImage)
                }
            }
        },
        confirmButton = { TextButton(onClick = onEdit) { Text("Edit") } },
        dismissButton = {
            Row {
                TextButton(onClick = { confirmDelete = true }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this note?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun NoteImage(image: NoteImageEntity, loadImage: suspend (NoteImageEntity) -> ByteArray?) {
    // decrypted lazily, and only while the note is on screen
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, image.id) {
        val bytes = loadImage(image)
        value = bytes?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = "Attached image",
            modifier = Modifier.fillMaxWidth()
        )
    } ?: Text("Loading image…", style = MaterialTheme.typography.labelSmall)
}

private fun formatWhen(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
