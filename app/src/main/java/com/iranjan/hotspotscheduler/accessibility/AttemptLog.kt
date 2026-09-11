package com.iranjan.hotspotscheduler.accessibility

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AttemptLog {
    private const val MAX_LINES = 400
    private val format = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
    private val cached = ArrayDeque<String>()
    private var logFile: File? = null
    private var initialized = false

    fun init(context: Context) {
        synchronized(this) {
            if (initialized) return
            val file = File(context.filesDir, "diagnostics.log")
            logFile = file
            runCatching {
                if (file.exists()) {
                    file.readLines().takeLast(MAX_LINES).forEach { cached.addLast(it) }
                }
            }
            initialized = true
        }
    }

    fun add(message: String) {
        val line = format.format(Date()) + "  " + message
        synchronized(this) {
            cached.addLast(line)
            while (cached.size > MAX_LINES) cached.removeFirst()
            val file = logFile
            if (file != null) {
                runCatching {
                    file.appendText(line + "\n")
                    if (file.length() > MAX_LINES * 200L) {
                        file.writeText(cached.joinToString("\n") + "\n")
                    }
                }
            }
        }
    }

    fun snapshot(): List<String> = synchronized(this) { cached.toList() }

    fun clear() {
        synchronized(this) {
            cached.clear()
            runCatching { logFile?.writeText("") }
        }
    }
}
