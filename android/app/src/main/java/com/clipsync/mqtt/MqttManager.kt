package com.clipsync.mqtt

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.clipsync.model.Message
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import com.clipsync.util.PrefsHelper

class MqttManager(
    private val context: Context,
    private val onMessage: (String) -> Unit
) {
    private var client: MqttClient? = null
    private var connected = false
    var onStateChanged: ((Boolean) -> Unit)? = null
    private val prefs = PrefsHelper(context)
    private val handler = Handler(Looper.getMainLooper())

    private fun setState(connected: Boolean) {
        this.connected = connected
        prefs.mqttConnected = connected
        onStateChanged?.invoke(connected)
    }

    fun connect() {
        val serverUri = prefs.mqttServer
        val topic = prefs.mqttTopic

        if (serverUri.isBlank() || topic.isBlank()) {
            Log.w("MqttManager", "服务器地址或Topic为空，跳过连接")
            setState(false)
            return
        }

        try {
            // 断开旧连接
            try { client?.disconnect() } catch (_: Exception) {}

            val clientId = "android-${android.os.Build.MODEL}-${System.currentTimeMillis()}"
            client = MqttClient(serverUri, clientId, MemoryPersistence())

            val options = MqttConnectOptions().apply {
                isCleanSession = false
                keepAliveInterval = 60
                connectionTimeout = 10
                isAutomaticReconnect = true
                userName = prefs.mqttUsername
                password = prefs.mqttPassword.toCharArray()
            }

            client?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    setState(false)
                    Log.w("MqttManager", "连接断开: ${cause?.message}")
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val payload = message?.toString() ?: return
                    Log.i("MqttManager", "收到MQTT消息: ${payload.take(100)}")
                    val msg = Message.fromJson(payload)
                    if (msg == null) {
                        Log.w("MqttManager", "消息解析失败: $payload")
                        return
                    }
                    if (msg.type == "clip") {
                        Log.i("MqttManager", "写入剪贴板: ${msg.content.take(50)}")
                        onMessage(msg.content)
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            client?.connect(options)
            setState(true)
            Log.i("MqttManager", "MQTT连接成功: $serverUri topic=$topic")

            // 延迟订阅，确保连接完全建立
            handler.postDelayed({
                try {
                    client?.subscribe(topic, 1)
                    Log.i("MqttManager", "订阅成功: $topic")
                } catch (e: Exception) {
                    Log.e("MqttManager", "订阅失败: ${e.message}")
                }
            }, 1000)

        } catch (e: MqttException) {
            setState(false)
            Log.e("MqttManager", "MQTT连接失败: reasonCode=${e.reasonCode} message=${e.message}", e)
        }
    }

    fun publish(content: String) {
        val topic = prefs.mqttTopic
        if (!connected || topic.isBlank()) return

        try {
            val msg = Message.clipUpdate(content)
            val mqttMsg = MqttMessage(msg.toJson().toByteArray())
            mqttMsg.qos = 1
            client?.publish(topic, mqttMsg)
        } catch (e: MqttException) {
            Log.e("MqttManager", "发布失败: ${e.message}")
        }
    }

    fun disconnect() {
        handler.removeCallbacksAndMessages(null)
        try {
            client?.disconnect()
        } catch (_: Exception) {}
        setState(false)
    }

    fun isConnected() = connected
}
