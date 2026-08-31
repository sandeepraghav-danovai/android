package com.danovai.passvault.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.ClipboardManager
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ClipboardUtil {

    private const val CLEAR_AFTER_MILLIS = 25_000L

    /** Copies [text] and best-effort clears the clipboard again after a delay. */
    fun copyThenAutoClear(context: Context, label: String, text: String, scope: CoroutineScope) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)

        scope.launch {
            delay(CLEAR_AFTER_MILLIS)
            val current = clipboard.primaryClip
            val currentText = current?.getItemAt(0)?.text?.toString()
            if (currentText == text) {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }
}
