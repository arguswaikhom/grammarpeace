package com.grammarpeace.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import com.grammarpeace.app.R

/** Puts [text] on the clipboard. Android 13+ shows its own confirmation, older versions get a toast. */
fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }
}

/**
 * The text on the clipboard, or null when there is none.
 * Android only allows this while the app's window has focus.
 */
fun Context.readClipboardText(): String? {
    val clipboard = getSystemService(ClipboardManager::class.java) ?: return null
    val clip = try {
        clipboard.primaryClip
    } catch (e: SecurityException) {
        null
    } ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(this)?.toString()?.takeIf { it.isNotBlank() }
}
