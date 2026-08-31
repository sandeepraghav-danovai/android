package com.danovai.passvault

import android.app.Application
import com.danovai.passvault.recovery.RecoveryManager
import com.danovai.passvault.notes.NotesRepository
import com.danovai.passvault.repository.VaultRepository
import com.danovai.passvault.session.SessionManager

class VaultApplication : Application() {

    lateinit var sessionManager: SessionManager
        private set
    lateinit var repository: VaultRepository
        private set
    lateinit var recoveryManager: RecoveryManager
        private set
    lateinit var notesRepository: NotesRepository
        private set

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager()
        repository = VaultRepository(this, sessionManager)
        recoveryManager = RecoveryManager(this, sessionManager)
        notesRepository = NotesRepository(this, sessionManager)
        if (repository.vaultExists()) {
            sessionManager.markVaultExists()
        }
    }
}
