package com.clipsync.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast

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
        try {
            lastContent = text
            val clip = ClipData.newPlainText("ClipSync", text)
            clipboard.setPrimaryClip(clip)
            Log.i("ClipboardMonitor", "剪贴板写入成功: ${text.take(50)}")
        } catch (e: Exception) {
            Log.e("ClipboardMonitor", "剪贴板写入失败: ${e.message}", e)
            // MIUI 可能需要通过 Toast 提示用户手动粘贴
            Toast.makeText(context, "收到: $text", Toast.LENGTH_SHORT).show()
        }
    }
}
