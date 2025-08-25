package com.punit.weightdriver.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import android.os.Build
import com.punit.weightdriver.core.UsbReaderService

class UsbAttachReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            // Start the foreground service to handle the device.
            val svc = Intent(context, UsbReaderService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc)
            } else {
                context.startService(svc)
            }
        }
        // (Optional) you can stop the service on DETACHED if desired:
        // if (action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
        //     context.stopService(Intent(context, UsbReaderService::class.java))
        // }
    }
}
