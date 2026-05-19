package com.clipsync.mqtt

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.clipsync.model.Message
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import com.clipsync.util.LogHelper
import com.clipsync.util.PrefsHelper
import java.util.Timer
import java.util.TimerTask

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
    private var resubTimer: Timer? = null

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
            disconnect()

            val clientId = "android-${android.os.Build.MODEL}-${System.currentTimeMillis()}"
            client = MqttClient(serverUri, clientId, MemoryPersistence())

            val options = MqttConnectOptions().apply {
                isCleanSession = true
                keepAliveInterval = 30
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

            // 立即订阅
            doSubscribe(topic)

            // 每30秒重新订阅一次，防止订阅丢失
            startResubTimer(topic)

        } catch (e: MqttException) {
            setState(false)
            LogHelper.e(TAG, "连接失败: code=${e.reasonCode} ${e.message}")
        }
    }

    private fun doSubscribe(topic: String) {
        try {
            client?.subscribe(topic, 1)
            LogHelper.i(TAG, "订阅成功: topic=$topic")
        } catch (e: Exception) {
            LogHelper.e(TAG, "订阅失败: ${e.message}")
        }
    }

    private fun startResubTimer(topic: String) {
        resubTimer?.cancel()
        resubTimer = Timer()
        resubTimer?.schedule(object : TimerTask() {
            override fun run() {
                if (connected) {
                    doSubscribe(topic)
                }
            }
        }, 30000, 30000)
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
        resubTimer?.cancel()
        resubTimer = null
        try { client?.disconnect() } catch (_: Exception) {}
        setState(false)
    }

    fun isConnected() = connected
}
