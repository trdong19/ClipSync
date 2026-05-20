package com.clipsync.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess

class ShizukuHelper(private val context: Context) {

    private val TAG = "SHIZUKU"

    companion object {
        private const val REQUEST_CODE = 1001
        private const val OPSTR_READ_CLIPBOARD_IN_BACKGROUND = "READ_CLIPBOARD_IN_BACKGROUND"
    }

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
        Shizuku.requestPermission(REQUEST_CODE)
    }

    /**
     * 通过 Shizuku 执行 appops 命令，授予后台读取剪贴板权限
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
            val uid = context.packageManager.getApplicationInfo(packageName, 0).uid

            // 方式1: 通过 shell 命令执行
            val success = execShellCommand(
                "appops set $packageName $OPSTR_READ_CLIPBOARD_IN_BACKGROUND allow"
            )

            if (success) {
                LogHelper.i(TAG, "后台剪贴板权限授予成功 (uid=$uid)")
                callback.onSuccess("后台剪贴板权限已开启，请重启 ClipSync 同步服务")
            } else {
                // 方式2: 通过 AppOps API
                val apiSuccess = grantViaAppOps(uid)
                if (apiSuccess) {
                    LogHelper.i(TAG, "通过AppOps API授权成功")
                    callback.onSuccess("后台剪贴板权限已开启，请重启 ClipSync 同步服务")
                } else {
                    callback.onError("授权失败，请确保 Shizuku 已正常运行")
                }
            }
        } catch (e: Exception) {
            LogHelper.e(TAG, "授权异常: ${e.message}")
            callback.onError("授权异常: ${e.message}")
        }
    }

    private fun execShellCommand(command: String): Boolean {
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val exitCode = process.waitFor()
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()

            if (output.isNotEmpty()) Log.d(TAG, "stdout: $output")
            if (error.isNotEmpty()) Log.w(TAG, "stderr: $error")

            exitCode == 0
        } catch (e: Exception) {
            Log.e(TAG, "Shell命令执行失败: ${e.message}")
            false
        }
    }

    private fun grantViaAppOps(uid: Int): Boolean {
        return try {
            val command = "appops set --uid $uid $OPSTR_READ_CLIPBOARD_IN_BACKGROUND allow"
            execShellCommand(command)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查后台剪贴板读取权限是否已授予
     */
    fun checkClipboardPermission(): Boolean {
        return try {
            val packageName = context.packageName
            val process = Shizuku.newProcess(
                arrayOf("sh", "-c", "appops get $packageName $OPSTR_READ_CLIPBOARD_IN_BACKGROUND"),
                null, null
            )
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.contains("allow")
        } catch (e: Exception) {
            false
        }
    }
}
