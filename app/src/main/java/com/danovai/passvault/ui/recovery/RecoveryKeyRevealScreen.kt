package com.danovai.passvault.ui.recovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.danovai.passvault.VaultApplication
import com.danovai.passvault.ui.vaultViewModel
import com.danovai.passvault.util.ClipboardUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RecoveryKeyRevealViewModel(private val app: VaultApplication) : ViewModel() {
    private val _key = MutableStateFlow<String?>(null)
    val key: StateFlow<String?> = _key

    init {
        viewModelScope.launch { _key.value = app.repository.generateRecoveryKit() }
    }
}

/**
 * Shown exactly once right after the key is generated — the raw key is never stored, so this
 * is the only chance to see it. Reused both right after vault setup and from Settings when
 * regenerating.
 */
@Composable
fun RecoveryKeyRevealScreen(onSaved: () -> Unit) {
    val viewModel = vaultViewModel { RecoveryKeyRevealViewModel(it) }
    val key by viewModel.key.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Your recovery key", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "This is the only time you'll see it. Write it down or save it somewhere safe — anyone with this key and your vault files can recover your vault without your master password.",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(24.dp))

        if (key == null) {
            CircularProgressIndicator()
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        key.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        key?.let { ClipboardUtil.copyThenAutoClear(context, "recovery key", it, scope) }
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Button(onClick = onSaved, modifier = Modifier.fillMaxWidth()) {
                Text("I've saved my recovery key")
            }
        }
    }
}
