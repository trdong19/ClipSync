package com.clipsync.mqtt

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.clipsync.model.Message
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import com.clipsync.util.LogHelper
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
    private val TAG = "MQTT"

    private fun setState(connected: Boolean) {
        this.connected = connected
        prefs.mqttConnected = connected
        onStateChanged?.invoke(connected)
    }

    fun connect() {
        val serverUri = prefs.mqttServer
        val topic = prefs.mqttTopic

        if (serverUri.isBlank() || topic.isBlank()) {
            LogHelper.w(TAG, "服务器地址或Topic为空，跳过连接")
            setState(false)
            return
        }

        try {
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
                    LogHelper.w(TAG, "连接断开: ${cause?.message}")
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val payload = message?.toString() ?: return
                    LogHelper.i(TAG, "<< 收到: ${payload.take(100)}")
                    val msg = Message.fromJson(payload)
                    if (msg == null) {
                        LogHelper.w(TAG, "消息解析失败: $payload")
                        return
                    }
                    if (msg.type == "clip") {
                        LogHelper.i(TAG, "写入剪贴板: ${msg.content.take(50)}")
                        onMessage(msg.content)
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            client?.connect(options)
            setState(true)
            LogHelper.i(TAG, "连接成功: $serverUri")

            handler.postDelayed({
                try {
                    client?.subscribe(topic, 1)
                    LogHelper.i(TAG, "订阅成功: topic=$topic")
                } catch (e: Exception) {
                    LogHelper.e(TAG, "订阅失败: ${e.message}")
                }
            }, 1000)

        } catch (e: MqttException) {
            setState(false)
            LogHelper.e(TAG, "连接失败: code=${e.reasonCode} ${e.message}")
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
            LogHelper.i(TAG, ">> 发送: ${content.take(50)}")
        } catch (e: MqttException) {
            LogHelper.e(TAG, "发送失败: ${e.message}")
        }
    }

    fun disconnect() {
        handler.removeCallbacksAndMessages(null)
        try { client?.disconnect() } catch (_: Exception) {}
        setState(false)
        LogHelper.i(TAG, "已断开")
    }

    fun isConnected() = connected
}
