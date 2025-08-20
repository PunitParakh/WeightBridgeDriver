package com.punit.weightdriver.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.punit.weightdriver.R
import com.punit.weightdriver.core.UsbReaderService
import com.punit.weightdriver.data.WeightRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var txtStatus: TextView
    private lateinit var txtDevice: TextView
    private lateinit var rvHistory: RecyclerView
    private lateinit var switchAutoInsert: SwitchMaterial
    private lateinit var switchClipboardOnly: SwitchMaterial
    private val adapter = HistoryAdapter()
    private lateinit var repo: WeightRepository

    private val PREFS_NAME = "weight_prefs"
    private val KEY_AUTO_INSERT = "auto_insert_enabled"
    private val KEY_CLIPBOARD = "clipboard_enabled"
    private val KEY_SERVICE_RUNNING = "service_running"

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtStatus = findViewById(R.id.txtStatus)
        txtDevice = findViewById(R.id.txtDevice)
        rvHistory = findViewById(R.id.rvHistory)
        switchAutoInsert = findViewById<SwitchMaterial>(R.id.switchAutoInsert)
        switchClipboardOnly = findViewById<SwitchMaterial>(R.id.switchClipboardOnly)

        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = adapter

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // repository singleton
        repo = WeightRepository.getInstance(applicationContext)

        // Observe recent history
        lifecycleScope.launch {
            repo.recent(200).collectLatest { list ->
                adapter.submitList(list) {
                    if (list.isNotEmpty()) {
                        rvHistory.smoothScrollToPosition(0) // auto scroll to latest
                    }
                }
            }
        }

        // initialize switches from prefs
        switchAutoInsert.isChecked = prefs.getBoolean(KEY_AUTO_INSERT, true)
        switchClipboardOnly.isChecked = prefs.getBoolean(KEY_CLIPBOARD, true)

        switchAutoInsert.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_AUTO_INSERT, isChecked).apply()
        }
        switchClipboardOnly.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_CLIPBOARD, isChecked).apply()
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            requestNotifIfNeeded()
            val serviceIntent = Intent(this, UsbReaderService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            prefs.edit().putBoolean(KEY_SERVICE_RUNNING, true).apply()
            updateStatusText()
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, UsbReaderService::class.java))
            prefs.edit().putBoolean(KEY_SERVICE_RUNNING, false).apply()
            updateStatusText()
        }

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }

//        // Test button (in-app) — safe demo without adb
//        findViewById<Button?>(R.id.btnTest)?.setOnClickListener {
//            // Use service to simulate a weight (so DB + clipboard path is exercised)
//            val i = Intent(this, UsbReaderService::class.java).apply {
//                putExtra(UsbReaderService.EXTRA_WEIGHT, "72.480")
//            }
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
//        }
    }

    override fun onResume() {
        super.onResume()
        updateStatusText()
        // show Accessibility status on launch/resume
        val accEnabled = isAccessibilityEnabled()
        val accText = if (accEnabled) "Accessibility: ON" else "Accessibility: OFF (enable for Auto-Insert)"
        txtDevice.text = accText
    }

    private fun updateStatusText() {
        val running = prefs.getBoolean(KEY_SERVICE_RUNNING, false)
        txtStatus.text = if (running) "Status: Service running" else "Status: Idle"
    }

    private fun requestNotifIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = "$packageName/${com.punit.weightdriver.inject.MyAccessibilityService::class.java.name}"
        val enabled = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }
}
