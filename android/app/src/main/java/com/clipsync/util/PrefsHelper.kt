package com.clipsync.util

import android.content.Context
import android.content.SharedPreferences

class PrefsHelper(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("clipsync", Context.MODE_PRIVATE)

    var mqttServer: String
        get() = prefs.getString("mqtt_server", "") ?: ""
        set(v) = prefs.edit().putString("mqtt_server", v).apply()

    var mqttTopic: String
        get() = prefs.getString("mqtt_topic", "") ?: ""
        set(v) = prefs.edit().putString("mqtt_topic", v).apply()

    var mqttUsername: String
        get() = prefs.getString("mqtt_username", "") ?: ""
        set(v) = prefs.edit().putString("mqtt_username", v).apply()

    var mqttPassword: String
        get() = prefs.getString("mqtt_password", "") ?: ""
        set(v) = prefs.edit().putString("mqtt_password", v).apply()

    var serviceEnabled: Boolean
        get() = prefs.getBoolean("service_enabled", false)
        set(v) = prefs.edit().putBoolean("service_enabled", v).apply()

    var mqttConnected: Boolean
        get() = prefs.getBoolean("mqtt_connected", false)
        set(v) = prefs.edit().putBoolean("mqtt_connected", v).apply()
}
