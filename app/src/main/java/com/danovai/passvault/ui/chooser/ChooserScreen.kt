package com.danovai.passvault.ui.chooser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.danovai.passvault.ui.components.DanovAiByline
import com.danovai.passvault.ui.rememberVaultApp

/**
 * Landing screen once the vault is unlocked: passwords on one side, notes on the other.
 *
 * Both sides sit behind the single unlock that already happened. The difference is what each
 * does afterwards — a stored password still asks you to confirm before it will reveal itself,
 * while notes simply open.
 */
@Composable
fun ChooserScreen(onOpenSecrets: () -> Unit, onOpenNotes: () -> Unit, onLock: () -> Unit) {
    val app = rememberVaultApp()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("PassVault", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text("Unlocked — pick where you're going.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))

        ChooserCard(
            icon = Icons.Filled.Lock,
            title = "Secrets",
            subtitle = "Saved passwords, organised by category. Viewing or copying one asks you to confirm.",
            onClick = onOpenSecrets
        )
        Spacer(Modifier.height(16.dp))
        ChooserCard(
            icon = Icons.Filled.StickyNote2,
            title = "Notes",
            subtitle = "Text and images, tagged and searchable. Open freely while unlocked.",
            onClick = onOpenNotes
        )

        Spacer(Modifier.height(32.dp))
        TextButton(onClick = { app.sessionManager.lock(); onLock() }) { Text("Lock now") }
        Spacer(Modifier.height(24.dp))
        DanovAiByline()
    }
}

@Composable
private fun ChooserCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
