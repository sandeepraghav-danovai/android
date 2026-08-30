package com.sandeepraghav.passvault.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.sandeepraghav.passvault.R
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun iconForCategory(icon: String): ImageVector = when (icon) {
    "bank" -> Icons.Filled.AccountBalance
    "apps" -> Icons.Filled.Apps
    "language" -> Icons.Filled.Language
    "work" -> Icons.Filled.Work
    "email" -> Icons.Filled.Email
    else -> Icons.Filled.Folder
}

@Composable
fun PasswordOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Done
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = if (visible) "Hide" else "Show")
            }
        }
    )
}

@Composable
fun LockGlyph() {
    Row {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

/**
 * The full DanovAI lockup (mark + wordmark + tagline). Android picks the light or dark
 * artwork automatically via drawable-nodpi / drawable-night-nodpi, so this stays legible
 * in both themes without any conditional logic here.
 */
@Composable
fun DanovAiLockup(modifier: Modifier = Modifier, width: Dp = 200.dp) {
    Image(
        painter = painterResource(R.drawable.danov_ai_lockup),
        contentDescription = stringResource(R.string.company_logo_description),
        contentScale = ContentScale.Fit,
        modifier = modifier.width(width)
    )
}

/**
 * Compact "by DanovAI" attribution: the D mark next to the company name. Meant to sit at
 * the bottom of a screen without competing with the primary content.
 */
@Composable
fun DanovAiByline(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.danov_ai_mark),
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = stringResource(R.string.company_byline),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}
