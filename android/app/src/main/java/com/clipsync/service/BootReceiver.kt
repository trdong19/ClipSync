package com.clipsync.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.clipsync.util.PrefsHelper

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = PrefsHelper(context)
            if (prefs.serviceEnabled) {
                ClipSyncService.start(context)
            }
        }
    }
}
