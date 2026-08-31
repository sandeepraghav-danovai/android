package com.danovai.passvault.navigation

object Routes {
    const val SETUP = "setup"
    const val LOCK = "lock"
    const val RECOVERY = "recovery"
    const val RECOVERY_KEY_REVEAL = "recovery_key_reveal?then={then}"
    fun recoveryKeyRevealAfterSetup() = "recovery_key_reveal?then=home"
    fun recoveryKeyRevealFromSettings() = "recovery_key_reveal?then=settings"

    const val RECOVERY_KEY_UNLOCK = "recovery_key_unlock"
    /** Landing screen after unlock: secrets or notes. */
    const val CHOOSER = "chooser"
    const val HOME = "home"
    const val NOTES = "notes"
    const val NOTE_EDIT = "note_edit?noteId={noteId}"
    fun noteEditNew() = "note_edit"
    fun noteEditExisting(noteId: Long) = "note_edit?noteId=$noteId"
    const val SETTINGS = "settings"

    const val CATEGORY = "category/{categoryId}/{categoryName}"
    fun category(categoryId: Long, categoryName: String) = "category/$categoryId/${android.net.Uri.encode(categoryName)}"

    const val ENTRY_DETAIL = "entry/{entryId}"
    fun entryDetail(entryId: Long) = "entry/$entryId"

    const val ENTRY_EDIT = "entry_edit/{categoryId}?entryId={entryId}"
    fun entryEditNew(categoryId: Long) = "entry_edit/$categoryId"
    fun entryEditExisting(categoryId: Long, entryId: Long) = "entry_edit/$categoryId?entryId=$entryId"
}
