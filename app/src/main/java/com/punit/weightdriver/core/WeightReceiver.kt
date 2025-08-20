package com.punit.weightdriver.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.punit.weightdriver.data.WeightRepository

class WeightReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WeightReceiver"
        private const val PREFS_NAME = "weight_prefs"
        private const val KEY_CLIPBOARD = "clipboard_enabled"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UsbReaderService.ACTION_WEIGHT) return

        val weight = intent.getStringExtra(UsbReaderService.EXTRA_WEIGHT)?.trim() ?: return
        Log.d(TAG, "onReceive: weight=$weight")

        // Keep the receiver alive for async work
        val pendingResult = goAsync()

        // Start background work (DB insert + optional clipboard copy)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // insert into DB via repository singleton (suspend)
                try {
                    WeightRepository.getInstance(context.applicationContext).insert(weight)
                    Log.d(TAG, "DB insert complete: $weight")
                } catch (dbEx: Exception) {
                    Log.e(TAG, "DB insert failed", dbEx)
                }

                // clipboard copy must run on Main dispatcher
                try {
                    val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val copyEnabled = prefs.getBoolean(KEY_CLIPBOARD, true)
                    if (copyEnabled) {
                        withContext(Dispatchers.Main) {
                            try {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("weight", weight)
                                cm.setPrimaryClip(clip)
                                Log.d(TAG, "Clipboard set: $weight")
                            } catch (cEx: Exception) {
                                Log.e(TAG, "Clipboard set failed", cEx)
                            }
                        }
                    } else {
                        Log.d(TAG, "Clipboard disabled by prefs")
                    }
                } catch (cOuter: Exception) {
                    Log.e(TAG, "Clipboard branch failed", cOuter)
                }
            } finally {
                // Always finish the broadcast after async work completes
                pendingResult.finish()
            }
        }
    }
}
