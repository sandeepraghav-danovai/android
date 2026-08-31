package com.danovai.passvault.ui.notes

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.danovai.passvault.data.NoteImageEntity
import com.danovai.passvault.ui.rememberVaultApp
import kotlinx.coroutines.launch

/**
 * Create or edit a note.
 *
 * A note is saved before an image can be attached, because images are stored against a note id.
 * When the screen is opened for a new note it therefore creates an empty one on the first attach
 * rather than making the user save first and come back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(noteId: Long?, onDone: () -> Unit) {
    val app = rememberVaultApp()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentId by remember { mutableStateOf(noteId) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var tagInput by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf(listOf<String>()) }
    var images by remember { mutableStateOf(listOf<NoteImageEntity>()) }
    var message by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(noteId == null) }

    LaunchedEffect(noteId) {
        if (noteId != null) {
            app.notesRepository.note(noteId)?.let { note ->
                title = note.title
                body = note.body
                tags = note.tags.map { it.name }
                images = note.images
            }
            loaded = true
        }
    }

    /** Persists whatever is on screen, creating the note the first time. Returns its id. */
    suspend fun persist(): Long {
        val id = currentId
        return if (id == null) {
            val newId = app.notesRepository.createNote(title, body, tags)
            currentId = newId
            newId
        } else {
            app.notesRepository.updateNote(id, title, body, tags)
            id
        }
    }

    suspend fun attach(bytes: ByteArray) {
        val id = persist()
        if (app.notesRepository.addImage(id, bytes)) {
            images = app.notesRepository.note(id)?.images ?: images
        } else {
            message = "Couldn't attach that image."
        }
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
                if (bytes == null) message = "Couldn't read that image." else attach(bytes)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (noteId == null) "New note" else "Edit note") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())
        ) {
            if (!loaded) {
                Text("Loading…", style = MaterialTheme.typography.bodySmall)
                return@Column
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Left blank, the first line of the note becomes its title.",
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Note") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp)
            )
            Spacer(Modifier.height(12.dp))

            // ---- tags
            Text("Tags", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            if (tags.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(tags, key = { it }) { tag ->
                        InputChip(
                            selected = false,
                            onClick = { tags = tags - tag },
                            label = { Text(tag) },
                            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove $tag") }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { tagInput = it },
                    label = { Text("Add a tag") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.height(0.dp))
                OutlinedButton(
                    onClick = {
                        val clean = tagInput.trim()
                        if (clean.isNotEmpty() && tags.none { it.equals(clean, ignoreCase = true) }) {
                            tags = tags + clean
                        }
                        tagInput = ""
                    }
                ) { Text("Add") }
            }
            Spacer(Modifier.height(16.dp))

            // ---- images
            Text("Images", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Row {
                OutlinedButton(onClick = {
                    // the picker backgrounds us; keep the session so the note can still be saved
                    app.sessionManager.expectDeliberateBackground()
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Add image") }
                Spacer(Modifier.height(0.dp))
                OutlinedButton(onClick = {
                    scope.launch {
                        val bytes = imageFromClipboard(context)
                        if (bytes == null) message = "No image on the clipboard." else attach(bytes)
                    }
                }) { Text("Paste image") }
            }

            images.forEach { image ->
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth()) {
                    EditorImage(image) { app.notesRepository.imageBytes(it) }
                    IconButton(onClick = {
                        scope.launch {
                            app.notesRepository.deleteImage(image)
                            images = currentId?.let { app.notesRepository.note(it)?.images } ?: emptyList()
                        }
                    }) { Icon(Icons.Filled.Close, contentDescription = "Remove image") }
                }
            }

            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    scope.launch {
                        if (title.isBlank() && body.isBlank() && images.isEmpty()) {
                            message = "Nothing to save yet."
                        } else {
                            persist()
                            onDone()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save note") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EditorImage(image: NoteImageEntity, load: suspend (NoteImageEntity) -> ByteArray?) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, image.id) {
        val bytes = load(image)
        value = bytes?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    bitmap?.let {
        Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Pulls an image off the clipboard if one is there. Android puts a content URI on the clip
 * rather than the bytes, so this resolves it and reads through the resolver.
 */
private fun imageFromClipboard(context: Context): ByteArray? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    val clip = clipboard.primaryClip ?: return null
    for (i in 0 until clip.itemCount) {
        val uri = clip.getItemAt(i).uri ?: continue
        val type = context.contentResolver.getType(uri) ?: continue
        if (!type.startsWith("image/")) continue
        return runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
    }
    return null
}
