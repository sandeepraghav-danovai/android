package com.sandeepraghav.passvault

import android.app.Application
import com.sandeepraghav.passvault.recovery.RecoveryManager
import com.sandeepraghav.passvault.repository.VaultRepository
import com.sandeepraghav.passvault.session.SessionManager

class VaultApplication : Application() {

    lateinit var sessionManager: SessionManager
        private set
    lateinit var repository: VaultRepository
        private set
    lateinit var recoveryManager: RecoveryManager
        private set

    override fun onCreate() {
        super.onCreate()
        sessionManager = SessionManager()
        repository = VaultRepository(this, sessionManager)
        recoveryManager = RecoveryManager(this, sessionManager)
        if (repository.vaultExists()) {
            sessionManager.markVaultExists()
        }
    }
}
