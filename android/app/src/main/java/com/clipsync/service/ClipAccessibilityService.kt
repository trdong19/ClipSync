package com.clipsync.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.clipsync.mqtt.MqttManager
import com.clipsync.ui.MainActivity
import com.clipsync.util.LogHelper
import com.clipsync.util.PrefsHelper

class ClipAccessibilityService : AccessibilityService() {

    private val TAG = "A11Y"
    private var lastSentText: String? = null
    private var mqttManager: MqttManager? = null

    override fun onServiceConnected() {
        LogHelper.i(TAG, "无障碍服务已连接")
        connectMqtt()
        startStickyNotification()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val prefs = PrefsHelper(this)
        if (!prefs.serviceEnabled) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                LogHelper.i(TAG, "长按事件")
                // 延迟检查节点树中的选中文本
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    checkSelectedTextFromRoot()
                }, 300)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // 不频繁检查，只在长按后检查
            }
        }
    }

    /**
     * 从根节点遍历查找选中的文本
     * 不依赖 ClipboardManager，直接从 UI 节点树读取
     */
    private fun checkSelectedTextFromRoot() {
        try {
            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                LogHelper.w(TAG, "rootInActiveWindow=null，无法读取节点树")
                return
            }

            val selectedText = findSelectedText(rootNode)
            rootNode.recycle()

            if (selectedText.isNullOrEmpty()) {
                LogHelper.i(TAG, "节点树中未找到选中文本")
                return
            }

            if (selectedText == lastSentText) {
                LogHelper.i(TAG, "内容相同，跳过")
                return
            }
            lastSentText = selectedText

            LogHelper.i(TAG, "从节点树读取选中文本: ${selectedText.take(50)}")
            publishToMqtt(selectedText)
        } catch (e: Exception) {
            LogHelper.e(TAG, "读取节点树失败: ${e.message}")
        }
    }

    /**
     * 递归遍历无障碍节点树，查找选中的文本
     */
    private fun findSelectedText(node: AccessibilityNodeInfo): String? {
        try {
            // 检查当前节点是否有选中的文本
            if (node.isFocused && node.textSelectionStart >= 0 && node.textSelectionEnd > node.textSelectionStart) {
                val text = node.text?.toString()
                if (!text.isNullOrEmpty()) {
                    val selected = text.substring(node.textSelectionStart, node.textSelectionEnd)
                    if (selected.length > 1) {
                        return selected
                    }
                }
            }

            // 检查 isSelected 属性
            if (node.isSelected) {
                val text = node.text?.toString()
                if (!text.isNullOrEmpty() && text.length > 1) {
                    return text
                }
            }

            // 检查 contentDescription
            if (node.isSelected && !node.contentDescription.isNullOrEmpty()) {
                return node.contentDescription.toString()
            }

            // 递归检查子节点
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val result = findSelectedText(child)
                child.recycle()
                if (result != null) return result
            }
        } catch (e: Exception) {
            LogHelper.w(TAG, "节点遍历异常: ${e.message}")
        }
        return null
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

    private fun startStickyNotification() {
        try {
            val channelId = "clipsync_a11y"
            val channel = NotificationChannel(
                channelId, "ClipSync 无障碍服务", NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

            val intent = Intent(this, MainActivity::class.java)
            val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(this, channelId)
                .setContentTitle("ClipSync")
                .setContentText("无障碍监听运行中")
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setContentIntent(pi)
                .setOngoing(true)
                .build()

            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(1002, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1002, notification)
            }
        } catch (e: Exception) {
            LogHelper.w(TAG, "通知失败: ${e.message}")
        }
    }

    override fun onInterrupt() {}
    override fun onDestroy() {
        mqttManager?.disconnect()
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
