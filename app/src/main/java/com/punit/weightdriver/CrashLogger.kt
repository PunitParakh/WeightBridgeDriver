package com.punit.weightdriver

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class App : Application() {

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            logToFile("USB event: ${intent.action}")
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Register USB attach/detach so we see the moment it flips to host mode
        registerReceiver(
            usbReceiver,
            IntentFilter().apply {
                addAction("android.hardware.usb.action.USB_DEVICE_ATTACHED")
                addAction("android.hardware.usb.action.USB_DEVICE_DETACHED")
                addAction("android.hardware.usb.action.USB_STATE")
            }
        )

        // Uncaught exception logger
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            logToFile(
                """
                ==== UNCAUGHT EXCEPTION ====
                Thread: ${t.name}
                Time  : ${now()}
                ${sw.toString()}
                """.trimIndent()
            )
            // Let the original handler (and crash reporter if any) run
            prev?.uncaughtException(t, e)
        }

        logToFile("App started at ${now()}")
    }

    private fun now(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    private fun logsDir(): File =
        File(getExternalFilesDir(null), "logs").apply { mkdirs() }

    private fun logToFile(text: String) {
        try {
            val f = File(logsDir(), "crash-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.txt")
            f.appendText("[${now()}] $text\n")
        } catch (_: Throwable) {
            // ignore
        }
    }
}