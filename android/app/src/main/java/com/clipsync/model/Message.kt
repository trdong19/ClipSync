package com.clipsync.model

import com.google.gson.Gson
import com.google.gson.JsonObject

data class Message(
    val type: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String = Gson().toJson(this)

    companion object {
        private val gson = Gson()

        fun fromJson(json: String): Message? = try {
            val obj = gson.fromJson(json, JsonObject::class.java)
            when {
                // Android 格式: {"type": "clip", "content": "..."}
                obj.has("type") && obj.has("content") ->
                    Message(obj.get("type").asString, obj.get("content").asString)

                // Windows 格式: {"id": "...", "name": "...", "value": "..."}
                obj.has("value") ->
                    Message("clip", obj.get("value").asString)

                else -> null
            }
        } catch (e: Exception) {
            null
        }

        fun clipUpdate(content: String) = Message("clip", content)
    }
}
