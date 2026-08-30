package com.sandeepraghav.passvault

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.sandeepraghav.passvault.navigation.PassVaultNavGraph
import com.sandeepraghav.passvault.session.LockState
import com.sandeepraghav.passvault.ui.theme.PassVaultTheme

class MainActivity : ComponentActivity() {

    private val app get() = application as VaultApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Blocks screenshots, screen recording, and the recents-screen thumbnail everywhere in the app.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            PassVaultTheme {
                androidx.compose.material3.Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                    PassVaultNavGraph()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app locks the vault immediately; anything already copied to the
        // clipboard stays usable, but the app itself must be re-authenticated to return.
        if (app.sessionManager.lockState.value == LockState.UNLOCKED) {
            app.sessionManager.lock()
        }
    }
}
