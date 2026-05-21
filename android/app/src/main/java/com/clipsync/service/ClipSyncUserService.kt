package com.clipsync.service

import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import com.clipsync.shizuku.IClipboardService

/**
 * 运行在 Shizuku 进程中的 UserService，拥有 shell UID。
 * 绕过 MIUI 对应用进程的剪贴板读取限制。
 */
class ClipSyncUserService(private val base: Context) : IClipboardService.Stub() {

    override fun readClipboard(): String {
        val ctx = try {
            val method = ContextWrapper::class.java.getMethod("getBaseContext")
            method.invoke(base) as Context
        } catch (e: Exception) {
            base
        }
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return ""
        if (clip.itemCount == 0) return ""
        val item = clip.getItemAt(0)
        val text = item.coerceToText(ctx)?.toString() ?: ""
        return text
    }
}
