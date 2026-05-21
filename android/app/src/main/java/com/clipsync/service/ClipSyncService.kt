package com.clipsync.service

import android.app.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.clipsync.mqtt.MqttManager
import com.clipsync.ui.MainActivity
import com.clipsync.util.LogHelper
import com.clipsync.util.PrefsHelper
import com.clipsync.util.ShizukuHelper

class ClipSyncService : Service() {

    private lateinit var mqttManager: MqttManager
    private lateinit var clipboard: ClipboardManager
    private var shizukuHelper: ShizukuHelper? = null
    private var lastContent: String? = null
    private var isRunning = false
    private var useShizuku = false
    private val TAG = "SERVICE"
    private val handler = Handler(Looper.getMainLooper())

    // 定时轮询：Shizuku 模式 2 秒，降级模式 3 秒
    private val pollRunnable = object : Runnable {
        override fun run() {
            if (useShizuku) {
                readClipboardViaShizuku()
            } else {
                readClipboardDirect()
            }
            handler.postDelayed(this, if (useShizuku) 2000 else 3000)
        }
    }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        readClipboardDirect()
    }

    override fun onCreate() {
        super.onCreate()
        clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        shizukuHelper = ShizukuHelper(this)
        mqttManager = MqttManager(this) { text ->
            setClip(text)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning) return START_STICKY

        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        mqttManager.connect()

        // 优先 Shizuku，不可用时降级到系统回调
        val shizuku = shizukuHelper
        if (shizuku != null && shizuku.isShizukuAvailable() && shizuku.hasPermission()) {
            useShizuku = true
            shizuku.bindService()
            LogHelper.i(TAG, "使用 Shizuku 轮询模式")
        } else {
            useShizuku = false
            clipboard.addPrimaryClipChangedListener(clipListener)
            LogHelper.i(TAG, "Shizuku 不可用，使用系统监听+轮询模式")
        }
        handler.postDelayed(pollRunnable, 3000)

        isRunning = true
        return START_STICKY
    }

    private fun readClipboardViaShizuku() {
        val text = shizukuHelper?.readClipboard() ?: return
        if (text.isEmpty()) return
        publishIfChanged(text)
    }

    private fun readClipboardDirect() {
        if (!clipboard.hasPrimaryClip()) return
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).text?.toString() ?: return
        publishIfChanged(text)
    }

    private fun publishIfChanged(text: String) {
        if (text == lastContent) return
        lastContent = text
        LogHelper.i(TAG, "检测到复制: ${text.take(50)}")
        mqttManager.publish(text)
    }

    private fun setClip(text: String) {
        try {
            lastContent = text
            clipboard.setPrimaryClip(ClipData.newPlainText("ClipSync", text))
            LogHelper.i(TAG, "写入成功: ${text.take(50)}")
        } catch (e: Exception) {
            LogHelper.e(TAG, "写入失败: ${e.message}")
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollRunnable)
        clipboard.removePrimaryClipChangedListener(clipListener)
        shizukuHelper?.unbindService()
        mqttManager.disconnect()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "ClipSync 同步服务", NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
            description = "剪贴板同步后台服务"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("ClipSync")
            .setContentText("剪贴板同步运行中")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "clipsync_service"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, ClipSyncService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ClipSyncService::class.java))
        }
    }
}
