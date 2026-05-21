package com.clipsync.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.clipsync.service.ClipSyncService
import com.clipsync.util.PrefsHelper
import com.clipsync.util.ShizukuHelper
import com.clipsync.R
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsHelper
    private lateinit var switchSync: SwitchMaterial
    private lateinit var tvStatus: TextView
    private lateinit var etServer: TextInputEditText
    private lateinit var etTopic: TextInputEditText
    private lateinit var etUsername: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnSave: MaterialButton
    private lateinit var btnMiuiGuide: MaterialButton
    private lateinit var btnShizuku: MaterialButton
    private lateinit var btnLogs: MaterialButton
    private var shizukuHelper: ShizukuHelper? = null
    private val handler = Handler(Looper.getMainLooper())
    private val shizukuPermCode = 1001
    private val shizukuPermListener = Shizuku.OnRequestPermissionResultListener { code, grantResult ->
        if (code == shizukuPermCode && grantResult == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Shizuku 权限已授予，开启同步即可使用", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Shizuku 权限被拒绝", Toast.LENGTH_SHORT).show()
        }
    }
    private val statusUpdater = object : Runnable {
        override fun run() {
            updateStatus()
            handler.postDelayed(this, 3000)
        }
    }

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "需要通知权限才能运行后台服务", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = PrefsHelper(this)

        switchSync = findViewById(R.id.switchSync)
        tvStatus = findViewById(R.id.tvStatus)
        etServer = findViewById(R.id.etServer)
        etTopic = findViewById(R.id.etTopic)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnSave = findViewById(R.id.btnSave)
        btnMiuiGuide = findViewById(R.id.btnMiuiGuide)
        btnShizuku = findViewById(R.id.btnShizuku)
        btnLogs = findViewById(R.id.btnLogs)

        loadConfig()
        setupListeners()
        requestPermissions()
        checkBatteryOptimization()
        Shizuku.addRequestPermissionResultListener(shizukuPermListener)
    }

    override fun onResume() {
        super.onResume()
        handler.post(statusUpdater)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(statusUpdater)
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermListener)
        super.onDestroy()
    }

    private fun updateStatus() {
        val running = prefs.serviceEnabled
        val connected = prefs.mqttConnected
        tvStatus.text = when {
            !running -> "状态: 未启用"
            connected -> "状态: 已连接"
            else -> "状态: 未连接"
        }
    }

    private fun loadConfig() {
        etServer.setText(prefs.mqttServer)
        etTopic.setText(prefs.mqttTopic)
        etUsername.setText(prefs.mqttUsername)
        etPassword.setText(prefs.mqttPassword)
        switchSync.isChecked = prefs.serviceEnabled
        updateStatus()
    }

    private fun setupListeners() {
        btnSave.setOnClickListener { saveConfig() }

        switchSync.setOnCheckedChangeListener { _, isChecked ->
            prefs.serviceEnabled = isChecked
            if (isChecked) {
                if (prefs.mqttServer.isBlank()) {
                    Toast.makeText(this, "请先配置 MQTT 服务器", Toast.LENGTH_SHORT).show()
                    switchSync.isChecked = false
                    return@setOnCheckedChangeListener
                }
                ClipSyncService.start(this)
                Toast.makeText(this, "同步已开启", Toast.LENGTH_SHORT).show()
            } else {
                ClipSyncService.stop(this)
                Toast.makeText(this, "同步已关闭", Toast.LENGTH_SHORT).show()
            }
            updateStatus()
        }

        btnMiuiGuide.setOnClickListener { openMiuiSettings() }
        btnShizuku.setOnClickListener { connectShizuku() }
        btnLogs.setOnClickListener { startActivity(Intent(this, LogActivity::class.java)) }
    }

    private fun saveConfig() {
        prefs.mqttServer = etServer.text.toString().trim()
        prefs.mqttTopic = etTopic.text.toString().trim()
        prefs.mqttUsername = etUsername.text.toString().trim()
        prefs.mqttPassword = etPassword.text.toString().trim()
        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show()

        if (prefs.serviceEnabled) {
            ClipSyncService.stop(this)
            ClipSyncService.start(this)
        }
    }

    private fun connectShizuku() {
        if (shizukuHelper == null) {
            shizukuHelper = ShizukuHelper(this)
        }
        val helper = shizukuHelper!!

        when {
            !helper.isShizukuAvailable() -> {
                Toast.makeText(this, "Shizuku 未运行，请先安装并启动 Shizuku", Toast.LENGTH_LONG).show()
            }
            !helper.hasPermission() -> {
                helper.requestPermission(this)
            }
            else -> {
                Toast.makeText(this, "Shizuku 已连接", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openMiuiSettings() {
        try {
            val intent = Intent().apply {
                setClassName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
                putExtra("package_name", packageName)
                putExtra("package_label", "ClipSync")
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(this, "请手动在设置中关闭电池优化", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkBatteryOptimization() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }
}
