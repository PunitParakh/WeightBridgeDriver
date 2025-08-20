package com.punit.weightdriver.core

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UsbSerialManager(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var port: UsbSerialPort? = null

    fun findDriver(): UsbSerialDriver? {
        val prober = UsbSerialProber.getDefaultProber()
        val drivers = prober.findAllDrivers(usbManager)
        return drivers.firstOrNull()
    }

    fun device(): UsbDevice? = findDriver()?.device

    suspend fun open(
        baudRate: Int = 9600,
        dataBits: Int = 8,
        stopBits: Int = UsbSerialPort.STOPBITS_1,
        parity: Int = UsbSerialPort.PARITY_NONE
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val driver = findDriver() ?: return@withContext false
            val conn = usbManager.openDevice(driver.device) ?: return@withContext false
            val p = driver.ports.first()
            p.open(conn)
            p.setParameters(baudRate, dataBits, stopBits, parity)
            // Remove unsupported flow control lines
            // try { p.setFlowControl(flow) } catch (_: Exception) {}

            port = p
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun read(dst: ByteArray, timeoutMs: Int = 1000): Int = withContext(Dispatchers.IO) {
        try { port?.read(dst, timeoutMs) ?: -1 } catch (_: Exception) { -1 }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        try { port?.close() } catch (_: Exception) {}
        port = null
    }
}
