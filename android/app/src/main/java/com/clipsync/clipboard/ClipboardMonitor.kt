package com.clipsync.clipboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.clipsync.util.LogHelper

class ClipboardMonitor(
    private val context: Context,
    private val onClipChanged: (String) -> Unit
) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private var lastContent: String? = null
    private var listening = false
    private val TAG = "CLIP"

    private val listener = ClipboardManager.OnPrimaryClipChangedListener {
        readClip()
    }

    fun start() {
        if (listening) return
        clipboard.addPrimaryClipChangedListener(listener)
        listening = true
        LogHelper.i(TAG, "剪贴板监听已启动")
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
        LogHelper.i(TAG, "检测到复制: ${text.take(50)}")
        onClipChanged(text)
    }

    fun setClip(text: String) {
        try {
            lastContent = text
            val clip = ClipData.newPlainText("ClipSync", text)
            clipboard.setPrimaryClip(clip)
            LogHelper.i(TAG, "写入成功: ${text.take(50)}")
        } catch (e: Exception) {
            LogHelper.e(TAG, "写入失败: ${e.message}")
            Toast.makeText(context, "收到: $text", Toast.LENGTH_SHORT).show()
        }
    }
}
