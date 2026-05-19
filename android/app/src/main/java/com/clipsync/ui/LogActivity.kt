package com.clipsync.ui

import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.clipsync.util.LogHelper
import com.google.android.material.button.MaterialButton

class LogActivity : AppCompatActivity() {
    private lateinit var tvLogs: TextView
    private lateinit var svLogs: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val btnClear = MaterialButton(this).apply { text = "清除日志" }
        val btnRefresh = MaterialButton(this).apply { text = "刷新" }

        val btnLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            addView(btnClear)
            addView(btnRefresh)
        }
        layout.addView(btnLayout)

        svLogs = ScrollView(this).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        tvLogs = TextView(this).apply {
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(8, 8, 8, 8)
        }
        svLogs.addView(tvLogs)
        layout.addView(svLogs)

        setContentView(layout)

        btnClear.setOnClickListener {
            LogHelper.clear()
            tvLogs.text = ""
        }
        btnRefresh.setOnClickListener { refreshLogs() }

        LogHelper.onLogAdded = { line ->
            runOnUiThread {
                tvLogs.append("$line\n")
                svLogs.post { svLogs.fullScroll(ScrollView.FOCUS_DOWN) }
            }
        }

        refreshLogs()
    }

    private fun refreshLogs() {
        tvLogs.text = LogHelper.getAll().joinToString("\n")
        svLogs.post { svLogs.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        LogHelper.onLogAdded = null
        super.onDestroy()
    }
}
