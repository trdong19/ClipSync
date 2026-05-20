package com.clipsync.util

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

class ShizukuHelper(private val context: Context) {

    private val TAG = "SHIZUKU"

    interface Callback {
        fun onSuccess(message: String)
        fun onError(message: String)
        fun onNeedPermission()
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

    /**
     * 通过 Shizuku 获取 shell 权限进程执行 appops 命令
     */
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
            val packageName = context.packageName
            val command = "appops set $packageName $OPSTR_READ_CLIPBOARD_IN_BACKGROUND allow"

            // 尝试通过反射使用 ShizukuRemoteProcess
            val success = execViaShizuku(command)

            if (success) {
                LogHelper.i(TAG, "后台剪贴板权限授予成功")
                callback.onSuccess("后台剪贴板权限已开启，请重启同步服务")
            } else {
                // 备选方案：直接调用 AppOpsManager.setUidMode（需要 Shizuku 运行中）
                val uid = context.packageManager.getApplicationInfo(packageName, 0).uid
                val method = AppOpsManager::class.java.getDeclaredMethod(
                    "setUidMode",
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                method.isAccessible = true
                method.invoke(null, OPSTR_READ_CLIPBOARD_IN_BACKGROUND, uid, 0)
                LogHelper.i(TAG, "通过 setUidMode 授权成功")
                callback.onSuccess("后台剪贴板权限已开启，请重启同步服务")
            }
        } catch (e: Exception) {
            LogHelper.e(TAG, "授权失败: ${e.message}")
            callback.onError("授权失败: ${e.message}")
        }
    }

    private fun execViaShizuku(command: String): Boolean {
        return try {
            // 反射调用 Shizuku.newProcess
            val shizukuClass = Shizuku::class.java
            val method = shizukuClass.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            val process = method.invoke(null, arrayOf("sh", "-c", command), null, null)
            // 反射调用 waitFor()
            val waitForMethod = process!!.javaClass.getMethod("waitFor")
            val exitCode = waitForMethod.invoke(process) as Int
            exitCode == 0
        } catch (e: Exception) {
            LogHelper.w(TAG, "ShizukuRemoteProcess 不可用: ${e.message}")
            false
        }
    }

    companion object {
        private const val OPSTR_READ_CLIPBOARD_IN_BACKGROUND = "READ_CLIPBOARD_IN_BACKGROUND"
    }
}
