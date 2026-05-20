package com.clipsync.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import rikka.shizuku.Shizuku

class ShizukuHelper(private val context: Context) {

    private val TAG = "SHIZUKU"

    interface Callback {
        fun onSuccess(message: String)
        fun onError(message: String)
        fun onNeedPermission()
    }

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    fun hasPermission(): Boolean {
        return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    fun requestPermission(activity: android.app.Activity) {
        Shizuku.requestPermission(1001)
    }

    /**
     * 获取 ADB 命令文本，用户可通过 Shizuku 终端或电脑 ADB 执行
     */
    fun getAdbCommand(): String {
        return "appops set ${context.packageName} READ_CLIPBOARD_IN_BACKGROUND allow"
    }

    /**
     * 复制 ADB 命令到剪贴板
     */
    fun copyCommandToClipboard() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ADB Command", getAdbCommand()))
        Toast.makeText(context, "命令已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    fun destroy() {}
}
