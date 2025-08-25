package com.punit.weightdriver.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.punit.weightdriver.R
import kotlinx.coroutines.*

class UsbReaderService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var serial: UsbSerialManager
    private val buf = ByteArray(1024)
    private var running = false

    private val usbManager by lazy { getSystemService(Context.USB_SERVICE) as UsbManager }

    // prefs keys (service doesn't use clipboard/db directly anymore)
    private val PREFS_NAME = "weight_prefs"
    private val KEY_SERVICE_RUNNING = "service_running"

    private val detachReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                stopSelf()
            }
        }
    }

    private val permReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_USB_PERMISSION) {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (device != null && granted) {
                    startLoop()
                } else {
                    stopSelf()
                }
            }
        }
    }

    // Listen for profile updates coming from DeviceEditDialog (or anywhere else)
    private val profileUpdatedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_PROFILE_UPDATED) return
            // Extras are optional; you can expand this later to match current device and reconfigure
            val vid = intent.getIntExtra("vid", -1)
            val pid = intent.getIntExtra("pid", -1)
            val serialNo = intent.getStringExtra("serial")
            // For now, just update the notification so you know it arrived.
            updateNotif("Profile updated (vid=$vid pid=$pid serial=$serialNo)")
            // TODO: if currently connected and this profile matches, re-open port with new params.
        }
    }

    override fun onCreate() {
        super.onCreate()
        serial = UsbSerialManager(this)

        // mark service running in prefs (so UI can reflect)
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SERVICE_RUNNING, true).apply()

        registerReceiver(detachReceiver, IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(
                permReceiver,
                IntentFilter(ACTION_USB_PERMISSION),
                Context.RECEIVER_NOT_EXPORTED
            )
            registerReceiver(
                profileUpdatedReceiver,
                IntentFilter(ACTION_PROFILE_UPDATED),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(permReceiver, IntentFilter(ACTION_USB_PERMISSION))
            @Suppress("DEPRECATION")
            registerReceiver(profileUpdatedReceiver, IntentFilter(ACTION_PROFILE_UPDATED))
        }

        createChannel()
        startForeground(NOTIF_ID, buildNotif("Waiting for device…"))

        requestPermissionIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Support in-app/adb testing: if EXTRA_WEIGHT passed to service, broadcast it
        intent?.getStringExtra(EXTRA_WEIGHT)?.let { w ->
            val b = Intent(ACTION_WEIGHT)
                .putExtra(EXTRA_WEIGHT, w)
                .setPackage(packageName)
            sendBroadcast(b)
            updateNotif("Last: $w")
        }
        return START_STICKY
    }

    private fun requestPermissionIfNeeded() {
        val device = serial.device() ?: return
        if (!usbManager.hasPermission(device)) {
            val pi = PendingIntent.getBroadcast(
                this,
                0,
                Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            usbManager.requestPermission(device, pi)
        } else {
            startLoop()
        }
    }

    private fun startLoop() {
        if (running) return
        running = true

        serviceScope.launch {
            try {
                val opened = serial.open(
                    baudRate = 9600,
                    dataBits = 8,
                    stopBits = UsbSerialPort.STOPBITS_1,
                    parity = UsbSerialPort.PARITY_NONE
                )
                if (!opened) {
                    updateNotif("Open failed (no permission/device)")
                    delay(1500)
                    stopSelf()
                    return@launch
                }
                updateNotif("Reading…")

                val sb = StringBuilder()
                while (isActive && running) {
                    val n = serial.read(buf, 1000)
                    if (n > 0) {
                        val s = String(buf, 0, n, Charsets.UTF_8)
                        sb.append(s)
                        val parts = sb.toString().split(Regex("[\r\n]+"))
                        val lastFrag =
                            if (sb.endsWith("\n") || sb.endsWith("\r")) "" else parts.lastOrNull() ?: ""
                        val complete = if (lastFrag.isEmpty()) parts else parts.dropLast(1)

                        for (line in complete) {
                            val weight = line.trim()
                            if (weight.isNotEmpty()) {
                                val b = Intent(ACTION_WEIGHT)
                                    .putExtra(EXTRA_WEIGHT, weight)
                                    .setPackage(packageName)
                                sendBroadcast(b)
                                updateNotif("Last: $weight")
                            }
                        }

                        sb.setLength(0)
                        if (lastFrag.isNotEmpty()) sb.append(lastFrag)
                    } else {
                        delay(100)
                    }
                }
            } catch (e: Exception) {
                updateNotif("Error: ${e.message}")
                stopSelf()
            } finally {
                try { serial.close() } catch (_: Exception) {}
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Weight Driver",
                NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotif(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("WeightDriver running")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    private fun updateNotif(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotif(text))
    }

    override fun onDestroy() {
        running = false
        serviceScope.cancel()
        try { unregisterReceiver(detachReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(permReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(profileUpdatedReceiver) } catch (_: Exception) {}

        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SERVICE_RUNNING, false).apply()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "weight_driver"
        const val NOTIF_ID = 1001

        const val ACTION_USB_PERMISSION = "com.punit.weightdriver.USB_PERMISSION"
        const val ACTION_WEIGHT = "com.punit.weightdriver.WEIGHT"
        const val EXTRA_WEIGHT = "extra.weight"

        // ✅ Add this so DeviceEditDialog can reference it
        const val ACTION_PROFILE_UPDATED = "com.punit.weightdriver.PROFILE_UPDATED"
    }
}
