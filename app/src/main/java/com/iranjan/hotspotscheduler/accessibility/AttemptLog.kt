package com.iranjan.hotspotscheduler.accessibility

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AttemptLog {
    private const val MAX = 150
    private val entries = ArrayDeque<String>()
    private val format = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun add(message: String) {
        synchronized(entries) {
            entries.addLast(format.format(Date()) + "  " + message)
            while (entries.size > MAX) entries.removeFirst()
        }
    }

    fun snapshot(): List<String> = synchronized(entries) { entries.toList() }

    fun clear() = synchronized(entries) { entries.clear() }
}
