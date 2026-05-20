package com.clipsync.util

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

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
            val command = "appops set $packageName READ_CLIPBOARD_IN_BACKGROUND allow"

            // 通过反射调用 Shizuku.newProcess
            val process = createShizukuProcess(arrayOf("sh", "-c", command))
            if (process != null) {
                // 反射获取 inputStream 和 errorStream
                val getStream = { name: String ->
                    val method = process.javaClass.getMethod(name)
                    val stream = method.invoke(process) as java.io.InputStream
                    BufferedReader(InputStreamReader(stream)).readText()
                }

                val output = getStream("getInputStream")
                val error = getStream("getErrorStream")

                // 反射调用 waitFor
                val waitForMethod = process.javaClass.getMethod("waitFor")
                val exitCode = waitForMethod.invoke(process) as Int

                if (output.isNotEmpty()) LogHelper.i(TAG, "stdout: $output")
                if (error.isNotEmpty()) LogHelper.w(TAG, "stderr: $error")

                if (exitCode == 0) {
                    LogHelper.i(TAG, "权限授予成功")
                    callback.onSuccess("后台剪贴板权限已开启，请重启同步服务")
                } else {
                    LogHelper.e(TAG, "命令执行失败 exitCode=$exitCode")
                    callback.onError("执行失败: ${error.ifEmpty { "exit code $exitCode" }}")
                }
            } else {
                callback.onError("无法创建 Shizuku 进程，请检查 Shizuku 是否正常运行")
            }
        } catch (e: Exception) {
            LogHelper.e(TAG, "授权异常: ${e.message}")
            callback.onError("授权异常: ${e.message}")
        }
    }

    /**
     * 通过反射调用 Shizuku.newProcess 创建特权进程
     */
    private fun createShizukuProcess(cmd: Array<String>): Any? {
        return try {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            method.invoke(null, cmd, null, null)
        } catch (e: NoSuchMethodException) {
            LogHelper.w(TAG, "Shizuku.newProcess 方法不存在: ${e.message}")
            null
        } catch (e: Exception) {
            LogHelper.w(TAG, "Shizuku.newProcess 调用失败: ${e.message}")
            null
        }
    }
}
