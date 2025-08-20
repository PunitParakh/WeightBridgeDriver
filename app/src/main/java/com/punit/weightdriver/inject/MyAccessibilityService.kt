package com.punit.weightdriver.inject

import android.annotation.SuppressLint
import android.accessibilityservice.AccessibilityService
import android.content.*
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.punit.weightdriver.core.UsbReaderService

class MyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MyAccessibilityService"
        private const val PREFS_NAME = "weight_prefs"
        private const val KEY_AUTO_INSERT = "auto_insert_enabled"
    }

    private val br = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == UsbReaderService.ACTION_WEIGHT) {
                val w = intent.getStringExtra(UsbReaderService.EXTRA_WEIGHT) ?: return
                Log.d(TAG, "Received weight broadcast (accessibility): $w")

                // Check user preference: auto-insert enabled?
                val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val autoInsert = prefs.getBoolean(KEY_AUTO_INSERT, true)
                if (!autoInsert) {
                    Log.d(TAG, "Auto-insert disabled by user; skipping injection.")
                    return
                }

                val success = injectTextAppend(w)
                Log.d(TAG, "injectTextAppend success=$success value=$w")
            }
        }
    }

    @SuppressLint("UnprotectedBroadcastReceiver")
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")

        val filter = IntentFilter().apply { addAction(UsbReaderService.ACTION_WEIGHT) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerReceiver(br, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(br, filter)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        try { unregisterReceiver(br) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun injectTextAppend(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = findFocusEditable(root) ?: return false

        try {
            val current = target.text?.toString() ?: ""
            val newText = if (current.isEmpty()) text else if (current.endsWith("\n")) current + text else "$current\n$text"

            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
            }
            if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                return true
            }

            // fallback: paste via clipboard (clipboard is handled by WeightReceiver)
            val len = current.length
            val selArgs = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, len)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, len)
            }
            @Suppress("DEPRECATION")
            target.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)

            return target.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        } catch (e: Exception) {
            Log.e(TAG, "injectTextAppend error", e)
            return false
        }
    }

    private fun findFocusEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isFocused) return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            val found = findFocusEditable(c)
            if (found != null) return found
        }
        return null
    }
}
