package com.danovai.passvault.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danovai.passvault.VaultApplication
import com.danovai.passvault.data.NoteImageEntity
import com.danovai.passvault.data.TagEntity
import com.danovai.passvault.notes.DecryptedNote
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class NotesViewModel(private val app: VaultApplication) : ViewModel() {

    data class UiState(
        val query: String = "",
        val selectedTagIds: Set<Long> = emptySet(),
        val allTags: List<TagEntity> = emptyList(),
        val notes: List<DecryptedNote> = emptyList(),
        val loading: Boolean = true,
        val openNote: DecryptedNote? = null,
        val message: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private var searchJob: Job? = null

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val notes = app.notesRepository.search(_state.value.query, _state.value.selectedTagIds)
            val tags = app.notesRepository.allTags()
            _state.value = _state.value.copy(notes = notes, allTags = tags, loading = false)
        }
    }

    /**
     * Search decrypts every note, so a keystroke-per-query would be wasteful. Debounce briefly
     * and let the last one win.
     */
    fun onQueryChange(value: String) {
        _state.value = _state.value.copy(query = value)
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(200)
            refresh()
        }
    }

    fun toggleTag(tagId: Long) {
        val selected = _state.value.selectedTagIds.toMutableSet()
        if (!selected.add(tagId)) selected.remove(tagId)
        _state.value = _state.value.copy(selectedTagIds = selected)
        refresh()
    }

    fun clearFilters() {
        _state.value = _state.value.copy(query = "", selectedTagIds = emptySet())
        refresh()
    }

    fun open(note: DecryptedNote) { _state.value = _state.value.copy(openNote = note) }
    fun closeOpenNote() { _state.value = _state.value.copy(openNote = null) }
    fun clearMessage() { _state.value = _state.value.copy(message = null) }

    fun delete(note: DecryptedNote) {
        viewModelScope.launch {
            app.notesRepository.deleteNote(note.id)
            _state.value = _state.value.copy(openNote = null, message = "Note deleted.")
            refresh()
        }
    }

    suspend fun imageBytes(image: NoteImageEntity): ByteArray? = app.notesRepository.imageBytes(image)
}
