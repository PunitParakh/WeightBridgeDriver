package com.punit.weightdriver.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object ClipboardHelper {
    private val history = ArrayDeque<String>()

    fun copy(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("weight", text))
        history.addFirst(text)
        if (history.size > 25) history.removeLast()
    }

    fun last(): String? = history.firstOrNull()
}
