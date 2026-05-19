package com.clipsync.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LogHelper {
    private val logs = mutableListOf<String>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    var onLogAdded: ((String) -> Unit)? = null

    fun d(tag: String, msg: String) = add("D", tag, msg)
    fun i(tag: String, msg: String) = add("I", tag, msg)
    fun w(tag: String, msg: String) = add("W", tag, msg)
    fun e(tag: String, msg: String) = add("E", tag, msg)

    private fun add(level: String, tag: String, msg: String) {
        val time = dateFormat.format(Date())
        val line = "[$time] $level/$tag: $msg"
        synchronized(logs) {
            logs.add(line)
            if (logs.size > 200) logs.removeAt(0)
        }
        android.util.Log.println(
            when (level) {
                "D" -> android.util.Log.DEBUG
                "I" -> android.util.Log.INFO
                "W" -> android.util.Log.WARN
                else -> android.util.Log.ERROR
            }, tag, msg
        )
        onLogAdded?.invoke(line)
    }

    fun getAll(): List<String> = synchronized(logs) { logs.toList() }

    fun clear() = synchronized(logs) { logs.clear() }
}
