package com.clipsync.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipboardManager
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import com.clipsync.mqtt.MqttManager
import com.clipsync.util.LogHelper
import com.clipsync.util.PrefsHelper

class ClipAccessibilityService : AccessibilityService() {

    private val TAG = "A11Y"
    private var lastClipText: String? = null
    private var mqttManager: MqttManager? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        handleClipChange()
    }

    override fun onServiceConnected() {
        LogHelper.i(TAG, "无障碍服务已连接，注册剪贴板监听")
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.addPrimaryClipChangedListener(clipListener)
        connectMqtt()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不依赖UI事件，使用OnPrimaryClipChangedListener
    }

    private fun connectMqtt() {
        val prefs = PrefsHelper(this)
        if (prefs.mqttServer.isBlank()) {
            LogHelper.w(TAG, "MQTT服务器未配置")
            return
        }
        mqttManager = MqttManager(this) { text ->
            // 无障碍服务也可以处理收到的消息写入剪贴板
            LogHelper.i(TAG, "收到MQTT消息写入剪贴板: ${text.take(50)}")
        }
        mqttManager?.connect()
    }

    private fun handleClipChange() {
        val prefs = PrefsHelper(this)
        if (!prefs.serviceEnabled) return

        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (!clipboard.hasPrimaryClip()) return
            val clip = clipboard.primaryClip ?: return
            if (clip.itemCount == 0) return
            val text = clip.getItemAt(0).text?.toString() ?: return

            // 避免重复发送相同内容
            if (text == lastClipText) return
            lastClipText = text

            // 确保MQTT已连接
            if (mqttManager == null || !mqttManager!!.isConnected()) {
                connectMqtt()
            }

            mqttManager?.publish(text)
        } catch (e: Exception) {
            LogHelper.e(TAG, "剪贴板处理失败: ${e.message}")
        }
    }

    override fun onInterrupt() {
        LogHelper.w(TAG, "无障碍服务中断")
    }

    override fun onDestroy() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.removePrimaryClipChangedListener(clipListener)
        } catch (_: Exception) {}
        mqttManager?.disconnect()
        LogHelper.i(TAG, "无障碍服务已销毁")
        super.onDestroy()
    }

    companion object {
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
