package com.sandeepraghav.passvault

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.fragment.app.FragmentActivity
import com.sandeepraghav.passvault.navigation.PassVaultNavGraph
import com.sandeepraghav.passvault.ui.components.LocalFragmentActivity
import com.sandeepraghav.passvault.session.LockState
import com.sandeepraghav.passvault.ui.theme.PassVaultTheme

// FragmentActivity rather than ComponentActivity: BiometricPrompt requires a fragment host.
class MainActivity : FragmentActivity() {

    private val app get() = application as VaultApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Blocks screenshots, screen recording, and the recents-screen thumbnail everywhere in the app.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            PassVaultTheme {
                androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                    CompositionLocalProvider(LocalFragmentActivity provides this@MainActivity) {
                        PassVaultNavGraph()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Back in the foreground: drop any timeout armed on the way out.
        app.sessionManager.cancelAutoLock()
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app locks the vault immediately; anything already copied to the
        // clipboard stays usable, but the app itself must be re-authenticated to return.
        if (app.sessionManager.lockState.value != LockState.UNLOCKED) return

        if (app.sessionManager.consumeDeliberateBackground()) {
            // We sent the user out to the system file picker and need the key when they come
            // back, so hold the session — but only for the normal auto-lock window, never
            // indefinitely, in case they wander off with the picker open.
            app.sessionManager.scheduleAutoLock()
        } else {
            app.sessionManager.lock()
        }
    }
}
