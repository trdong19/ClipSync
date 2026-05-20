package com.clipsync.util

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.clipsync.shizuku.IClipSyncUserService
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.UserServiceArgs

class ShizukuHelper(private val context: Context) {

    private val TAG = "SHIZUKU"
    private var userService: IClipSyncUserService? = null
    private var userServiceConnected = false

    interface Callback {
        fun onSuccess(message: String)
        fun onError(message: String)
        fun onNeedPermission()
    }

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(component: ComponentName, binder: IBinder) {
            userService = IClipSyncUserService.Stub.asInterface(binder)
            userServiceConnected = true
            LogHelper.i(TAG, "UserService 已连接")
        }

        override fun onServiceDisconnected(component: ComponentName) {
            userService = null
            userServiceConnected = false
            LogHelper.w(TAG, "UserService 已断开")
        }
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

    private fun bindUserService() {
        if (userServiceConnected) return
        try {
            val args = UserServiceArgs(
                ComponentName(context.packageName, "com.clipsync.shizuku.ClipSyncUserService")
            )
                .daemon(false)
                .processNameSuffix("shizuku")
                .debuggable(false)
                .version(1)
            Shizuku.bindUserService(args, userServiceConnection)
            LogHelper.i(TAG, "正在绑定 UserService...")
        } catch (e: Exception) {
            LogHelper.e(TAG, "绑定 UserService 失败: ${e.message}")
        }
    }

    private fun unbindUserService() {
        try {
            Shizuku.unbindUserService(userServiceConnection)
        } catch (_: Exception) {}
        userServiceConnected = false
        userService = null
    }

    fun grantClipboardPermission(callback: Callback) {
        if (!isShizukuAvailable()) {
            callback.onError("Shizuku 未运行，请先安装并启动 Shizuku")
            return
        }

        if (!hasPermission()) {
            callback.onNeedPermission()
            return
        }

        try {
            // 绑定 UserService
            bindUserService()

            // 等待 UserService 连接
            val packageName = context.packageName

            // 使用 Binder 异步处理
            Thread {
                var waitCount = 0
                while (!userServiceConnected && waitCount < 50) {
                    Thread.sleep(100)
                    waitCount++
                }

                if (!userServiceConnected) {
                    callback.onError("UserService 连接超时")
                    return@Thread
                }

                try {
                    val result = userService?.grantClipboardPermission(packageName) ?: false
                    if (result) {
                        LogHelper.i(TAG, "后台剪贴板权限授予成功")
                        callback.onSuccess("后台剪贴板权限已开启，请重启同步服务")
                    } else {
                        callback.onError("权限授予失败")
                    }
                } catch (e: Exception) {
                    LogHelper.e(TAG, "调用 UserService 失败: ${e.message}")
                    callback.onError("调用失败: ${e.message}")
                }
            }.start()
        } catch (e: Exception) {
            LogHelper.e(TAG, "授权异常: ${e.message}")
            callback.onError("授权异常: ${e.message}")
        }
    }

    fun destroy() {
        unbindUserService()
    }
}
