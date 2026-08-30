package com.sandeepraghav.passvault.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sandeepraghav.passvault.ui.rememberVaultApp
import kotlinx.coroutines.launch

@Composable
fun ReAuthDialog(reason: String, onDismiss: () -> Unit, onVerified: () -> Unit) {
    val app = rememberVaultApp()
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm master password") },
        text = {
            Column {
                Text(reason)
                Spacer(Modifier.height(8.dp))
                PasswordOutlinedField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = "Master password",
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    if (app.repository.verifyMasterPassword(password.toCharArray())) {
                        onVerified()
                    } else {
                        error = "Incorrect password."
                    }
                }
            }) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
