package com.clipsync.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.clipsync.util.LogHelper
import com.clipsync.util.PrefsHelper

class ClipAccessibilityService : AccessibilityService() {

    private val TAG = "A11Y"

    override fun onServiceConnected() {
        LogHelper.i(TAG, "无障碍服务已连接")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                checkClipboard()
            }
        }
    }

    private fun checkClipboard() {
        val prefs = PrefsHelper(this)
        if (!prefs.serviceEnabled) return

        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (!clipboard.hasPrimaryClip()) return
            val clip = clipboard.primaryClip ?: return
            if (clip.itemCount == 0) return
            val text = clip.getItemAt(0).text?.toString() ?: return

            // 通过广播通知 ClipSyncService 发送剪贴板内容
            val intent = android.content.Intent(ACTION_CLIP_CHANGED).apply {
                setPackage(packageName)
                putExtra(EXTRA_CLIP_TEXT, text)
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "读取剪贴板失败: ${e.message}")
        }
    }

    override fun onInterrupt() {
        LogHelper.w(TAG, "无障碍服务中断")
    }

    override fun onDestroy() {
        LogHelper.i(TAG, "无障碍服务已销毁")
        super.onDestroy()
    }

    companion object {
        const val ACTION_CLIP_CHANGED = "com.clipsync.CLIP_CHANGED"
        const val EXTRA_CLIP_TEXT = "clip_text"

        fun isEnabled(context: Context): Boolean {
            val serviceName = "${context.packageName}/${ClipAccessibilityService::class.java.name}"
            val enabledServices = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.contains(serviceName)
        }
    }
}
