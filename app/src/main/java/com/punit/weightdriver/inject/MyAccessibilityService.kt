package com.punit.weightdriver.inject

import android.annotation.SuppressLint
import android.accessibilityservice.AccessibilityService
import android.content.*
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.ClipData
import android.content.ClipboardManager
import com.punit.weightdriver.core.UsbReaderService

class MyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MyAccessibilityService"
    }

    private val br = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == UsbReaderService.ACTION_WEIGHT) {
                val w = intent.getStringExtra(UsbReaderService.EXTRA_WEIGHT) ?: return
                Log.d(TAG, "Received weight broadcast: $w")
                try {
                    val ok = injectWeightSmart(w)
                    Log.d(TAG, "injectWeightSmart success=$ok value=$w")
                } catch (t: Throwable) {
                    Log.e(TAG, "inject exception", t)
                }
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

    /**
     * Smart injection:
     * - finds focused editable node
     * - detects if current text is a placeholder (hint/contentDesc or short digit-less text when not focused)
     * - if selection info available, insert at cursor preserving surrounding text
     * - otherwise append to the end
     * - fallback to clipboard+paste if ACTION_SET_TEXT fails
     */
    private fun injectWeightSmart(weight: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val target = findFocusEditable(root) ?: return false

        return try {
            // Current visible text (may be hint or actual)
            val current = target.text?.toString() ?: ""
            // hintText available on API 26+
            val hint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) (target.hintText?.toString() ?: "") else ""
            val contentDesc = target.contentDescription?.toString() ?: ""

            // Heuristic: treat current as placeholder if it equals hint/contentDescription,
            // or if field is not focused and text is short & contains no digits (common placeholder pattern).
            val isPlaceholderByHint = current.isNotEmpty() && (current == hint || current == contentDesc)
            val isPlaceholderHeuristic = (!target.isFocused) && current.isNotEmpty() &&
                    current.length <= 20 && !current.any { it.isDigit() }

            val treatAsEmpty = current.isBlank() || isPlaceholderByHint || isPlaceholderHeuristic

            // Try to read selection indices (may throw on some nodes, guard it)
            var selStart = -1
            var selEnd = -1
            try {
                selStart = target.textSelectionStart
                selEnd = target.textSelectionEnd
            } catch (_: Exception) { /* selection not available */ }

            // If selection not available, append at end or replace placeholder
            if (selStart < 0 || selEnd < 0) {
                val newText = if (treatAsEmpty) weight else (current + weight)
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
                }
                if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                    // try to place cursor after inserted weight
                    val pos = if (treatAsEmpty) weight.length else (current + weight).length
                    setSelectionSafe(target, pos, pos)
                    return true
                }
                // fallback: clipboard + set selection to end + paste
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("weight", weight))
                val len = if (treatAsEmpty) 0 else current.length
                setSelectionSafe(target, len, len)
                return target.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            }

            // We have selection info: insert weight at selStart...selEnd
            val pre = if (treatAsEmpty) "" else current.substring(0, selStart.coerceIn(0, current.length))
            val post = if (treatAsEmpty) "" else current.substring(selEnd.coerceIn(0, current.length))
            val newText = pre + weight + post
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
            }
            if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                // set cursor after inserted text
                val newCursor = (pre + weight).length
                setSelectionSafe(target, newCursor, newCursor)
                return true
            }

            // fallback: clipboard + selection at selStart + paste
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("weight", weight))
            setSelectionSafe(target, selStart, selStart)
            return target.performAction(AccessibilityNodeInfo.ACTION_PASTE)

        } catch (e: Exception) {
            Log.e(TAG, "injectWeightSmart failed", e)
            return false
        }
    }

    private fun setSelectionSafe(node: AccessibilityNodeInfo, start: Int, end: Int) {
        try {
            val selArgs = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, start)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, end)
            }
            @Suppress("DEPRECATION")
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selArgs)
        } catch (t: Throwable) {
            // ignore - best-effort
        }
    }

    private fun findFocusEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isFocused) return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            val f = findFocusEditable(c)
            if (f != null) return f
        }
        return null
    }
}
