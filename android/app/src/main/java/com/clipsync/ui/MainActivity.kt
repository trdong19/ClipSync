package com.clipsync.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.clipsync.service.ClipSyncService
import com.clipsync.util.PrefsHelper
import com.clipsync.R

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsHelper
    private lateinit var switchSync: SwitchMaterial
    private lateinit var etServer: TextInputEditText
    private lateinit var etTopic: TextInputEditText
    private lateinit var etUsername: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnSave: MaterialButton
    private lateinit var btnMiuiGuide: MaterialButton

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
        etServer = findViewById(R.id.etServer)
        etTopic = findViewById(R.id.etTopic)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)
        btnSave = findViewById(R.id.btnSave)
        btnMiuiGuide = findViewById(R.id.btnMiuiGuide)

        loadConfig()
        setupListeners()
        requestPermissions()
        checkBatteryOptimization()
    }

    private fun loadConfig() {
        etServer.setText(prefs.mqttServer)
        etTopic.setText(prefs.mqttTopic)
        etUsername.setText(prefs.mqttUsername)
        etPassword.setText(prefs.mqttPassword)
        switchSync.isChecked = prefs.serviceEnabled
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
        }

        btnMiuiGuide.setOnClickListener { openMiuiSettings() }
    }

    private fun saveConfig() {
        prefs.mqttServer = etServer.text.toString().trim()
        prefs.mqttTopic = etTopic.text.toString().trim()
        prefs.mqttUsername = etUsername.text.toString().trim()
        prefs.mqttPassword = etPassword.text.toString().trim()
        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show()

        // 如果服务在运行，重启以应用新配置
        if (prefs.serviceEnabled) {
            ClipSyncService.stop(this)
            ClipSyncService.start(this)
        }
    }

    private fun openMiuiSettings() {
        // 尝试打开小米电池优化设置
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
            // 降级到系统电池优化设置
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
