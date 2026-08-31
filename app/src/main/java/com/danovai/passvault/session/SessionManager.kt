package com.danovai.passvault.session

import com.danovai.passvault.crypto.CryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class LockState { NO_VAULT, LOCKED, UNLOCKED }

/**
 * Holds the data-encryption key only in memory for the duration of an unlocked
 * session. Never written to disk. Cleared on lock, background timeout, or process death.
 */
class SessionManager {

    private var dek: ByteArray? = null
    private var autoLockJob: Job? = null
    private var deliberateBackground = false
    private val internalScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _lockState = MutableStateFlow(LockState.NO_VAULT)
    val lockState: StateFlow<LockState> = _lockState

    var autoLockTimeoutMillis: Long = DEFAULT_AUTO_LOCK_MILLIS

    fun markVaultExists() {
        if (_lockState.value == LockState.NO_VAULT) _lockState.value = LockState.LOCKED
    }

    fun unlock(key: ByteArray) {
        dek = key
        _lockState.value = LockState.UNLOCKED
    }

    fun currentKey(): ByteArray? = dek

    fun lock() {
        CryptoManager.wipe(dek)
        dek = null
        autoLockJob?.cancel()
        _lockState.value = LockState.LOCKED
    }

    /** Wipes the session and reports that no vault exists at all — used after a vault reset. */
    fun resetToNoVault() {
        CryptoManager.wipe(dek)
        dek = null
        autoLockJob?.cancel()
        _lockState.value = LockState.NO_VAULT
    }

    /**
     * Marks the next trip to the background as one the app itself started — currently only the
     * system file picker used for CSV import, which cannot work if the vault locks the moment
     * the picker appears.
     *
     * This is a single-use flag, consumed by the next [consumeDeliberateBackground]. The session
     * is not left open indefinitely either: the caller falls back to the normal auto-lock
     * timeout, so walking away from an open picker still locks the vault.
     */
    fun expectDeliberateBackground() { deliberateBackground = true }

    fun consumeDeliberateBackground(): Boolean {
        val value = deliberateBackground
        deliberateBackground = false
        return value
    }

    /** Schedules the auto-lock on the manager's own scope, for callers without one. */
    fun scheduleAutoLock() = scheduleAutoLock(internalScope)

    fun scheduleAutoLock(scope: CoroutineScope) {
        autoLockJob?.cancel()
        autoLockJob = scope.launch {
            delay(autoLockTimeoutMillis)
            lock()
        }
    }

    fun cancelAutoLock() {
        autoLockJob?.cancel()
    }

    companion object {
        const val DEFAULT_AUTO_LOCK_MILLIS = 2 * 60 * 1000L
    }
}
