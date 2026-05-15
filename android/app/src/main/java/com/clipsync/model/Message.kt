package com.clipsync.model

import com.google.gson.Gson

data class Message(
    val type: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String = Gson().toJson(this)

    companion object {
        fun fromJson(json: String): Message? = try {
            Gson().fromJson(json, Message::class.java)
        } catch (e: Exception) {
            null
        }

        fun clipUpdate(content: String) = Message("clip", content)
    }
}
