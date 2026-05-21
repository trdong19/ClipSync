package com.clipsync.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.clipsync.mqtt.MqttManager
import com.clipsync.util.LogHelper
import com.clipsync.util.PrefsHelper

class ClipAccessibilityService : AccessibilityService() {

    private val TAG = "A11Y"
    private var lastSentText: String? = null
    private var mqttManager: MqttManager? = null
    private lateinit var clipboard: ClipboardManager
    private val handler = Handler(Looper.getMainLooper())

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        readAndPublish()
    }

    // 轮询降级：防止 MIUI 不触发回调
    private val pollRunnable = object : Runnable {
        override fun run() {
            readAndPublish()
            handler.postDelayed(this, 3000)
        }
    }

    override fun onServiceConnected() {
        LogHelper.i(TAG, "无障碍服务已连接")
        clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.addPrimaryClipChangedListener(clipListener)
        handler.postDelayed(pollRunnable, 3000)
        connectMqtt()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 用户操作时触发一次检查
        event ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED,
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                handler.postDelayed({ readAndPublish() }, 300)
            }
        }
    }

    private fun readAndPublish() {
        val prefs = PrefsHelper(this)
        if (!prefs.serviceEnabled) return
        try {
            if (!clipboard.hasPrimaryClip()) return
            val clip = clipboard.primaryClip ?: return
            if (clip.itemCount == 0) return
            val text = clip.getItemAt(0).text?.toString() ?: return
            if (text.isEmpty() || text == lastSentText) return
            lastSentText = text
            LogHelper.i(TAG, "检测到复制: ${text.take(50)}")
            publishToMqtt(text)
        } catch (e: Exception) {
            LogHelper.w(TAG, "读取剪贴板失败: ${e.message}")
        }
    }

    private fun publishToMqtt(text: String) {
        if (mqttManager == null || !mqttManager!!.isConnected()) {
            connectMqtt()
        }
        mqttManager?.publish(text)
    }

    private fun connectMqtt() {
        val prefs = PrefsHelper(this)
        if (prefs.mqttServer.isBlank()) return
        mqttManager = MqttManager(this) { }
        mqttManager?.connect()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        clipboard.removePrimaryClipChangedListener(clipListener)
        handler.removeCallbacks(pollRunnable)
        mqttManager?.disconnect()
        super.onDestroy()
    }

    companion object {
        fun isEnabled(context: android.content.Context): Boolean {
            val serviceName = "${context.packageName}/${ClipAccessibilityService::class.java.name}"
            val enabledServices = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabledServices.contains(serviceName)
        }
    }
}
