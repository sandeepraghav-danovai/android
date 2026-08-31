package com.danovai.passvault.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.danovai.passvault.VaultApplication

@Composable
fun rememberVaultApp(): VaultApplication = LocalContext.current.applicationContext as VaultApplication

@Composable
inline fun <reified VM : ViewModel> vaultViewModel(crossinline create: (VaultApplication) -> VM): VM {
    val app = rememberVaultApp()
    val factory = remember(app) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = create(app) as T
        }
    }
    return viewModel(factory = factory)
}
