package com.clipsync.shizuku

import android.app.AppOpsManager
import android.content.Context
import android.os.Binder

class ClipSyncUserService(private val context: Context) : IClipSyncUserService.Stub() {

    companion object {
        private const val OPSTR_READ_CLIPBOARD_IN_BACKGROUND = "READ_CLIPBOARD_IN_BACKGROUND"
        private const val OP_ALLOW = 0
    }

    override fun grantClipboardPermission(packageName: String): Boolean {
        return try {
            val uid = context.packageManager.getApplicationInfo(packageName, 0).uid
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

            // 使用反射调用隐藏 API setUidMode
            val method = AppOpsManager::class.java.getDeclaredMethod(
                "setUidMode",
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            method.invoke(appOps, OPSTR_READ_CLIPBOARD_IN_BACKGROUND, uid, OP_ALLOW)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override fun checkClipboardPermission(packageName: String): Boolean {
        return try {
            val uid = context.packageManager.getApplicationInfo(packageName, 0).uid
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager

            val method = AppOpsManager::class.java.getDeclaredMethod(
                "unsafeCheckOpNoThrow",
                String::class.java,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            val mode = method.invoke(appOps, OPSTR_READ_CLIPBOARD_IN_BACKGROUND, uid, packageName) as Int
            mode == OP_ALLOW
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
