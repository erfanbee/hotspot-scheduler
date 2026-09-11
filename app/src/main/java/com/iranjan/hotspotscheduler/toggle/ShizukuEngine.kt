package com.iranjan.hotspotscheduler.toggle

import android.content.pm.PackageManager
import com.iranjan.hotspotscheduler.accessibility.AttemptLog
import dev.rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ShellResult(val exitCode: Int, val output: String) {
    val success: Boolean get() = exitCode == 0
}

@Singleton
class ShizukuEngine @Inject constructor() {

    fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        isRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    fun isReady(): Boolean = hasPermission()

    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val output = process.inputStream.bufferedReader().readText()
            val code = process.waitFor()
            ShellResult(code, output)
        } catch (t: Throwable) {
            AttemptLog.add("shizuku exec failed: ${t.message}")
            ShellResult(-1, t.message ?: "error")
        }
    }

    suspend fun mobileData(on: Boolean): Boolean {
        val result = exec("svc data " + if (on) "enable" else "disable")
        return result.success
    }

    suspend fun mobileDataState(): Boolean? {
        val result = exec("settings get global mobile_data")
        return when (result.output.trim()) {
            "1" -> true
            "0" -> false
            else -> null
        }
    }

    suspend fun hotspotCommandSupported(): Boolean {
        val help = exec("cmd wifi help")
        return help.output.contains("start-softap")
    }

    suspend fun setHotspot(on: Boolean, password: String?): Boolean {
        return if (on) {
            val pass = password ?: ""
            val candidates = if (pass.length >= 8) {
                listOf(
                    "cmd wifi start-softap ap0 wpa2 $pass",
                    "cmd wifi start-softap ap0 wpa2-psk $pass"
                )
            } else {
                listOf(
                    "cmd wifi start-softap ap0 open",
                    "cmd wifi start-softap ap0 none"
                )
            }
            for (cmd in candidates) {
                val result = exec(cmd)
                if (result.success) return true
            }
            false
        } else {
            val result = exec("cmd wifi stop-softap ap0")
            if (result.success) {
                true
            } else {
                exec("cmd wifi stop-softap").success
            }
        }
    }
}
