package com.clipsync.clipboard

import android.content.ClipboardManager
import android.content.Context

class ClipboardMonitor(
    private val context: Context,
    private val onClipChanged: (String) -> Unit
) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var lastContent: String? = null
    private var listening = false

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        readClip()
    }

    fun start() {
        if (listening) return
        clipboard.addPrimaryClipChangedListener(listener)
        listening = true
    }

    fun stop() {
        if (!listening) return
        clipboard.removePrimaryClipChangedListener(listener)
        listening = false
    }

    fun readClip() {
        if (!clipboard.hasPrimaryClip()) return
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).text?.toString() ?: return
        if (text == lastContent) return
        lastContent = text
        onClipChanged(text)
    }

    fun setClip(text: String) {
        lastContent = text
        val clip = android.content.ClipData.newPlainText("ClipSync", text)
        clipboard.setPrimaryClip(clip)
    }
}
