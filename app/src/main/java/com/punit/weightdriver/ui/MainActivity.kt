package com.punit.weightdriver.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.switchmaterial.SwitchMaterial
import com.punit.weightdriver.R
import com.punit.weightdriver.core.UsbDeviceMonitor
import com.punit.weightdriver.core.UsbReaderService
import com.punit.weightdriver.data.DeviceProfile
import com.punit.weightdriver.data.DeviceRepository
import com.punit.weightdriver.data.WeightRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), DevicesAdapter.OnDeviceClick {

    private lateinit var txtStatus: TextView
    private lateinit var txtDevice: TextView
    private lateinit var rvHistory: RecyclerView
    private lateinit var rvDevices: RecyclerView
    private lateinit var tvNoDevices: TextView
    private lateinit var switchAutoInsert: SwitchMaterial
    private lateinit var switchClipboardOnly: SwitchMaterial
    private lateinit var tabs: TabLayout

    private val historyAdapter = HistoryAdapter()
    private val devicesAdapter = DevicesAdapter(this)

    private lateinit var weightRepo: WeightRepository
    private lateinit var deviceRepo: DeviceRepository
    private lateinit var monitor: UsbDeviceMonitor

    private val PREFS_NAME = "weight_prefs"
    private val KEY_AUTO_INSERT = "auto_insert_enabled"
    private val KEY_CLIPBOARD = "clipboard_enabled"
    private val KEY_SERVICE_RUNNING = "service_running"

    private lateinit var prefs: SharedPreferences

    // local cache of last profiles
    private var lastProfiles: List<DeviceProfile> = emptyList()

    // which tab is currently active (0 = devices, 1 = recent). default = recent(1)
    private var currentTabIndex: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtStatus = findViewById(R.id.txtStatus)
        txtDevice = findViewById(R.id.txtDevice)
        tabs = findViewById(R.id.tabLayout)

        rvHistory = findViewById(R.id.rvHistory)
        rvDevices = findViewById(R.id.rvDevices)
        tvNoDevices = findViewById(R.id.tvNoDevices)

        switchAutoInsert = findViewById(R.id.switchAutoInsert)
        switchClipboardOnly = findViewById(R.id.switchClipboardOnly)

        // History list
        rvHistory.layoutManager = LinearLayoutManager(this).apply { reverseLayout = false }
        rvHistory.adapter = historyAdapter

        // Devices list
        rvDevices.layoutManager = LinearLayoutManager(this)
        rvDevices.adapter = devicesAdapter

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        weightRepo = WeightRepository.getInstance(applicationContext)
        deviceRepo = DeviceRepository.getInstance(applicationContext)
        monitor = UsbDeviceMonitor.getInstance(applicationContext)

        // Observe recent history (auto-scroll to top on new item)
        lifecycleScope.launch {
            weightRepo.recent(200).collectLatest { list ->
                historyAdapter.submitList(list) {
                    if (list.isNotEmpty()) rvHistory.scrollToPosition(0)
                }
            }
        }

        // Observe devices (DB) and update UI visibility as appropriate
        lifecycleScope.launch {
            deviceRepo.all().collectLatest { profiles ->
                lastProfiles = profiles
                val connected = monitor.connected.value
                devicesAdapter.submit(profiles, connected)
                // update visibility according to the currently selected tab
                updateContentVisibility()
            }
        }

        // Watch connected set changes
        lifecycleScope.launch {
            monitor.connected.collectLatest { set ->
                devicesAdapter.refreshConnected(set)
                updateContentVisibility()
            }
        }

        // init switches from prefs
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(serviceIntent)
            else startService(serviceIntent)
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

        // Tab selection listener
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTabIndex = tab.position
                updateContentVisibility()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {
                currentTabIndex = tab.position
                updateContentVisibility()
            }
        })

        // select Recent tab by default
        if (tabs.tabCount >= 2) {
            tabs.getTabAt(1)?.select()
            currentTabIndex = 1
        } else {
            currentTabIndex = 0
        }
        updateContentVisibility()
    }

    /**
     * Centralized visibility logic:
     * - Devices tab: show rvDevices OR tvNoDevices centered when profiles empty.
     * - Recent tab: show rvHistory only.
     */
    private fun updateContentVisibility() {
        if (currentTabIndex == 0) {
            // Devices tab selected
            if (lastProfiles.isEmpty()) {
                rvDevices.visibility = View.GONE
                tvNoDevices.visibility = View.VISIBLE
            } else {
                rvDevices.visibility = View.VISIBLE
                tvNoDevices.visibility = View.GONE
            }
            rvHistory.visibility = View.GONE
        } else {
            // Recent tab selected
            rvDevices.visibility = View.GONE
            tvNoDevices.visibility = View.GONE
            rvHistory.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatusText()
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
        val enabled = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    // Device clicked -> show editor
    override fun onDeviceClick(profile: DeviceProfile) {
        DeviceEditDialog.newInstance(profile.id).show(supportFragmentManager, "edit_device")
    }
}
