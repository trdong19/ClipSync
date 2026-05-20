package com.clipsync.util

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import com.clipsync.BuildConfig
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
     * 通过 Shizuku binder 身份直接调用 AppOpsManager.setUidMode
     * 无需 AIDL，无需 shell 命令
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

            // 使用 Shizuku binder 身份执行 AppOps 调用
            val token: IBinder = Binder()
            val orig = Binder.clearCallingIdentity()
            try {
                Binder.restoreCallingIdentity(Shizuku.getBinderToken())
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

                // 反射调用隐藏 API: AppOpsManager.setUidMode(op, uid, mode)
                val method = AppOpsManager::class.java.getDeclaredMethod(
                    "setUidMode",
                    String::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                method.isAccessible = true
                method.invoke(appOps, OPSTR_READ_CLIPBOARD_IN_BACKGROUND, uid, 0) // 0 = MODE_ALLOWED

                LogHelper.i(TAG, "后台剪贴板权限授予成功 uid=$uid")
                callback.onSuccess("后台剪贴板权限已开启，请重启同步服务")
            } finally {
                Binder.restoreCallingIdentity(orig)
            }
        } catch (e: Exception) {
            LogHelper.e(TAG, "授权失败: ${e.message}")
            callback.onError("授权失败: ${e.message}")
        }
    }

    companion object {
        private const val OPSTR_READ_CLIPBOARD_IN_BACKGROUND = "READ_CLIPBOARD_IN_BACKGROUND"
    }
}
