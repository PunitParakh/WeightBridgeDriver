package com.punit.weightdriver.core

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DeviceKey(val vid: Int, val pid: Int, val serial: String?)

class UsbDeviceMonitor private constructor(private val app: Context) {
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _connected: MutableStateFlow<Set<DeviceKey>> = MutableStateFlow(emptySet())
    val connected: StateFlow<Set<DeviceKey>> = _connected

    private val usb by lazy { app.getSystemService(Context.USB_SERVICE) as UsbManager }

    private val permissionAction = "${app.packageName}.USB_PERMISSION"

    private val br = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED -> refresh()

                permissionAction -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    val granted: Boolean = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (device != null && granted) {
                        refresh()
                    }
                }
            }
        }
    }

    init {
        // initial snapshot
        refresh()
        // listen to attach/detach + permission
        val f = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(permissionAction)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            app.registerReceiver(br, f, Context.RECEIVER_NOT_EXPORTED)
        }
        else{
             app.registerReceiver(br, f)
    }}

    private fun refresh() {
        scope.launch {
            val set = usb.deviceList.values.map { d ->
                ensurePermission(d)
                DeviceKey(d.vendorId, d.productId, safeSerial(d))
            }.toSet()
            _connected.value = set
        }
    }

    private fun ensurePermission(device: UsbDevice) {
        if (!usb.hasPermission(device)) {
            val pi = PendingIntent.getBroadcast(
                app,
                0,
                Intent(permissionAction),
                PendingIntent.FLAG_IMMUTABLE
            )
            usb.requestPermission(device, pi)
        }
    }

    private fun safeSerial(d: UsbDevice): String? {
        return try {
            if (usb.hasPermission(d)) d.serialNumber else null
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    fun shutdown() {
        try {
            app.unregisterReceiver(br)
        } catch (_: Exception) {
        }
    }

    companion object {
        @Volatile private var inst: UsbDeviceMonitor? = null
        fun getInstance(ctx: Context): UsbDeviceMonitor {
            return inst ?: synchronized(this) {
                UsbDeviceMonitor(ctx.applicationContext).also { inst = it }
            }
        }
    }
}
