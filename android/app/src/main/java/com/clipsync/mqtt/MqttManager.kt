package com.clipsync.mqtt

import android.content.Context
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

    fun connect() {
        val prefs = PrefsHelper(context)
        val serverUri = prefs.mqttServer
        val topic = prefs.mqttTopic

        if (serverUri.isBlank() || topic.isBlank()) return

        try {
            val clientId = "android-${android.os.Build.MODEL}-${System.currentTimeMillis()}"
            client = MqttClient(serverUri, clientId, MemoryPersistence())

            val options = MqttConnectOptions().apply {
                isCleanSession = true
                keepAliveInterval = 600
                connectionTimeout = 10
                isAutomaticReconnect = true
                userName = prefs.mqttUsername
                password = prefs.mqttPassword.toCharArray()
            }

            client?.setCallback(object : MqttCallback {
                override fun connectionLost(cause: Throwable?) {
                    connected = false
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val payload = message?.toString() ?: return
                    val msg = Message.fromJson(payload) ?: return
                    if (msg.type == "clip") {
                        onMessage(msg.content)
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            client?.connect(options)
            client?.subscribe(topic, 1)
            connected = true
        } catch (e: MqttException) {
            connected = false
        }
    }

    fun publish(content: String) {
        val prefs = PrefsHelper(context)
        val topic = prefs.mqttTopic
        if (!connected || topic.isBlank()) return

        try {
            val msg = Message.clipUpdate(content)
            val mqttMsg = MqttMessage(msg.toJson().toByteArray())
            mqttMsg.qos = 1
            client?.publish(topic, mqttMsg)
        } catch (e: MqttException) {
            // 丢弃，下次剪贴板变化会重发
        }
    }

    fun disconnect() {
        try {
            client?.disconnect()
        } catch (_: Exception) {}
        connected = false
    }

    fun isConnected() = connected
}
