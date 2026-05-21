package com.clipsync.util

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.clipsync.shizuku.IClipboardService
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper

class ShizukuHelper(private val context: Context) {

    private val TAG = "SHIZUKU"
    private var clipboardService: IClipboardService? = null
    private var isBound = false

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, "com.clipsync.service.ClipSyncUserService")
    ).daemon(false)
        .processNameSuffix("shizuku")

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            LogHelper.i(TAG, "UserService 已连接")
            clipboardService = IClipboardService.Stub.asInterface(ShizukuBinderWrapper(service))
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName) {
            LogHelper.w(TAG, "UserService 断开")
            clipboardService = null
            isBound = false
        }
    }

    fun isShizukuAvailable(): Boolean {
        return try { Shizuku.pingBinder() } catch (e: Exception) { false }
    }

    fun hasPermission(): Boolean {
        return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("UNUSED_PARAMETER")
    fun requestPermission(activity: android.app.Activity) {
        Shizuku.requestPermission(1001)
    }

    fun bindService() {
        if (!isShizukuAvailable() || !hasPermission()) return
        try {
            Shizuku.bindUserService(userServiceArgs, connection)
        } catch (e: Exception) {
            LogHelper.e(TAG, "绑定失败: ${e.message}")
        }
    }

    fun unbindService() {
        try {
            if (isBound) {
                Shizuku.unbindUserService(userServiceArgs, connection, true)
                clipboardService = null
                isBound = false
            }
        } catch (e: Exception) {
            LogHelper.w(TAG, "解绑失败: ${e.message}")
        }
    }

    fun readClipboard(): String? {
        if (!isShizukuAvailable() || !hasPermission()) return null
        if (!isBound || clipboardService == null) {
            bindService()
            return null
        }
        return try {
            clipboardService?.readClipboard()
        } catch (e: Exception) {
            clipboardService = null
            isBound = false
            null
        }
    }
}
