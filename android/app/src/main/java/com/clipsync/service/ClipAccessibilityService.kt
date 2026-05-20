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
import com.clipsync.mqtt.MqttManager
import com.clipsync.ui.MainActivity
import com.clipsync.util.LogHelper
import com.clipsync.util.PrefsHelper

class ClipAccessibilityService : AccessibilityService() {

    private val TAG = "A11Y"
    private var lastClipText: String? = null
    private var mqttManager: MqttManager? = null

    override fun onServiceConnected() {
        LogHelper.i(TAG, "无障碍服务已连接")
        connectMqtt()
        startStickyNotification()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        // 长按（type=2）是复制操作的信号，立即检查剪贴板
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED) {
            LogHelper.i(TAG, "检测到长按事件，检查剪贴板")
            checkClipboard()
        }
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

    private fun checkClipboard() {
        val prefs = PrefsHelper(this)
        if (!prefs.serviceEnabled) return

        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            if (!clipboard.hasPrimaryClip()) {
                LogHelper.w(TAG, "hasPrimaryClip=false，系统阻止了读取")
                return
            }

            val clip = clipboard.primaryClip
            if (clip == null || clip.itemCount == 0) return

            val text = clip.getItemAt(0).text?.toString() ?: return
            if (text == lastClipText) return
            lastClipText = text

            LogHelper.i(TAG, "读取到剪贴板: ${text.take(50)}")

            if (mqttManager == null || !mqttManager!!.isConnected()) {
                LogHelper.w(TAG, "MQTT未连接，尝试重连")
                connectMqtt()
            }

            mqttManager?.publish(text)
        } catch (e: SecurityException) {
            LogHelper.e(TAG, "SecurityException - 剪贴板读取被拒绝: ${e.message}")
        } catch (e: Exception) {
            LogHelper.e(TAG, "剪贴板处理失败: ${e.message}")
        }
    }

    /**
     * 启动常驻通知，防止MIUI杀死无障碍服务
     */
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
            LogHelper.w(TAG, "启动常驻通知失败: ${e.message}")
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
