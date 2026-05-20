package com.clipsync.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipboardManager
import android.content.Context
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
        when (event.eventType) {
            // 长按 → 可能是复制操作，检查剪贴板
            AccessibilityEvent.TYPE_VIEW_LONG_CLICKED -> {
                LogHelper.i(TAG, "检测到长按，检查剪贴板")
                // 延迟一小段时间等待复制操作完成
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    checkClipboard()
                }, 500)
            }
            // 窗口内容变化 → 可能是文本选择变化
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED -> {
                checkSelectedText(event)
            }
        }
    }

    /**
     * 方法1：从无障碍节点树直接读取选中的文本
     * 不依赖ClipboardManager，绕过系统限制
     */
    private fun checkSelectedText(event: AccessibilityEvent) {
        val prefs = PrefsHelper(this)
        if (!prefs.serviceEnabled) return

        try {
            val source = event.source ?: return
            val selectedText = source.getText()
            if (selectedText.isNullOrEmpty()) {
                source.recycle()
                return
            }

            val text = selectedText.toString()
            if (text == lastSentText) {
                source.recycle()
                return
            }

            // 检查是否有选中的文本范围
            val selStart = source.textSelectionStart
            val selEnd = source.textSelectionEnd
            if (selStart >= 0 && selEnd > selStart && selEnd <= text.length) {
                val selected = text.substring(selStart, selEnd)
                if (selected.length > 1 && selected != lastSentText) {
                    lastSentText = selected
                    LogHelper.i(TAG, "从节点树读取选中文本: ${selected.take(50)}")
                    publishToMqtt(selected)
                }
            }
            source.recycle()
        } catch (e: Exception) {
            LogHelper.w(TAG, "读取选中文本失败: ${e.message}")
        }
    }

    /**
     * 方法2：通过ClipboardManager读取剪贴板
     * 在MIUI/Android 16后台可能被阻止
     */
    private fun checkClipboard() {
        val prefs = PrefsHelper(this)
        if (!prefs.serviceEnabled) return

        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            if (!clipboard.hasPrimaryClip()) {
                LogHelper.w(TAG, "hasPrimaryClip=false")
                return
            }

            val clip = clipboard.primaryClip ?: return
            if (clip.itemCount == 0) return
            val text = clip.getItemAt(0).text?.toString() ?: return
            if (text == lastSentText) return
            lastSentText = text

            LogHelper.i(TAG, "从剪贴板读取: ${text.take(50)}")
            publishToMqtt(text)
        } catch (e: SecurityException) {
            LogHelper.e(TAG, "SecurityException: ${e.message}")
        } catch (e: Exception) {
            LogHelper.e(TAG, "剪贴板读取失败: ${e.message}")
        }
    }

    private fun publishToMqtt(text: String) {
        if (mqttManager == null || !mqttManager!!.isConnected()) {
            LogHelper.w(TAG, "MQTT未连接，尝试重连")
            connectMqtt()
        }
        mqttManager?.publish(text)
    }

    private fun connectMqtt() {
        val prefs = PrefsHelper(this)
        if (prefs.mqttServer.isBlank()) {
            LogHelper.w(TAG, "MQTT服务器未配置")
            return
        }
        mqttManager = MqttManager(this) { text ->
            LogHelper.i(TAG, "收到MQTT消息: ${text.take(50)}")
        }
        mqttManager?.connect()
    }

    private fun startStickyNotification() {
        try {
            val channelId = "clipsync_a11y"
            val channel = NotificationChannel(
                channelId,
                "ClipSync 无障碍服务",
                NotificationManager.IMPORTANCE_LOW
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
            LogHelper.i(TAG, "常驻通知已启动")
        } catch (e: Exception) {
            LogHelper.w(TAG, "通知启动失败: ${e.message}")
        }
    }

    override fun onInterrupt() {
        LogHelper.w(TAG, "无障碍服务中断")
    }

    override fun onDestroy() {
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
