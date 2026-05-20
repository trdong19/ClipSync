package com.clipsync.service

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.clipsync.mqtt.MqttManager
import com.clipsync.ui.MainActivity
import com.clipsync.util.LogHelper
import com.clipsync.util.PrefsHelper

class ClipAccessibilityService : AccessibilityService() {

    private val TAG = "A11Y"
    private var lastClipText: String? = null
    private var mqttManager: MqttManager? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        LogHelper.i(TAG, "OnPrimaryClipChangedListener 触发")
        handleClipChange()
    }

    override fun onServiceConnected() {
        LogHelper.i(TAG, "无障碍服务已连接，注册剪贴板监听")
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.addPrimaryClipChangedListener(clipListener)
        connectMqtt()
        startStickyNotification()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 记录所有事件用于诊断
        event?.let {
            LogHelper.i(TAG, "收到无障碍事件: type=${it.eventType}")
        }
    }

    private fun connectMqtt() {
        val prefs = PrefsHelper(this)
        if (prefs.mqttServer.isBlank()) {
            LogHelper.w(TAG, "MQTT服务器未配置")
            return
        }
        mqttManager = MqttManager(this) { text ->
            LogHelper.i(TAG, "收到MQTT消息写入剪贴板: ${text.take(50)}")
        }
        mqttManager?.connect()
    }

    private fun handleClipChange() {
        val prefs = PrefsHelper(this)
        if (!prefs.serviceEnabled) {
            LogHelper.w(TAG, "同步未启用，跳过")
            return
        }

        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            // 诊断：检查剪贴板是否可用
            val hasClip = clipboard.hasPrimaryClip()
            LogHelper.i(TAG, "hasPrimaryClip=$hasClip")

            if (!hasClip) {
                LogHelper.w(TAG, "无法读取剪贴板（可能被系统限制）")
                return
            }

            val clip = clipboard.primaryClip
            if (clip == null || clip.itemCount == 0) {
                LogHelper.w(TAG, "剪贴板内容为空")
                return
            }

            val text = clip.getItemAt(0).text?.toString()
            if (text == null) {
                LogHelper.w(TAG, "剪贴板文本为null")
                return
            }

            // 避免重复发送相同内容
            if (text == lastClipText) {
                LogHelper.i(TAG, "内容相同，跳过")
                return
            }
            lastClipText = text

            LogHelper.i(TAG, "剪贴板内容: ${text.take(50)}")

            // 确保MQTT已连接
            if (mqttManager == null || !mqttManager!!.isConnected()) {
                LogHelper.w(TAG, "MQTT未连接，尝试重连")
                connectMqtt()
            }

            mqttManager?.publish(text)
        } catch (e: SecurityException) {
            LogHelper.e(TAG, "安全异常 - 剪贴板读取被系统拒绝: ${e.message}")
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
            ).apply {
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)

            val intent = Intent(this, MainActivity::class.java)
            val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(this, channelId)
                .setContentTitle("ClipSync")
                .setContentText("无障碍剪贴板监听运行中")
                .setSmallIcon(android.R.drawable.ic_menu_share)
                .setContentIntent(pi)
                .setOngoing(true)
                .build()

            startForeground(1002, notification)
            LogHelper.i(TAG, "常驻通知已启动")
        } catch (e: Exception) {
            LogHelper.w(TAG, "启动常驻通知失败: ${e.message}")
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
