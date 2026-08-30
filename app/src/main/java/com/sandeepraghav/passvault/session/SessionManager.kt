package com.sandeepraghav.passvault.session

import com.sandeepraghav.passvault.crypto.CryptoManager
import kotlinx.coroutines.CoroutineScope
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
