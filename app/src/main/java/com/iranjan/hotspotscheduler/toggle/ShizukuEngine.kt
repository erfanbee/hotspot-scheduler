package com.iranjan.hotspotscheduler.toggle

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.iranjan.hotspotscheduler.accessibility.AttemptLog
import dagger.hilt.android.qualifiers.ApplicationContext
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ShellResult(val exitCode: Int, val output: String) {
    val success: Boolean get() = exitCode == 0
}

@Singleton
class ShizukuEngine @Inject constructor(@ApplicationContext private val context: Context) {

    @Volatile
    private var service: IShellService? = null

    @Volatile
    private var latch = CountDownLatch(1)

    @Volatile
    private var softApSupported: Boolean? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = binder?.let { IShellService.Stub.asInterface(it) }
            AttemptLog.add("shizuku shell service connected=${service != null}")
            latch.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

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

    private fun awaitService(): IShellService? {
        service?.let { return it }
        synchronized(this) {
            service?.let { return it }
            latch = CountDownLatch(1)
            return try {
                val args = Shizuku.UserServiceArgs(ComponentName(context, ShellService::class.java))
                    .processNameSuffix("shell")
                    .version(1)
                    .debuggable(false)
                Shizuku.bindUserService(args, connection)
                latch.await(10, TimeUnit.SECONDS)
                service
            } catch (t: Throwable) {
                AttemptLog.add("shizuku bind failed: ${t.message}")
                null
            }
        }
    }

    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        val shell = awaitService() ?: return@withContext ShellResult(-1, "service not bound")
        try {
            val raw = shell.runCommand(command)
            val code = raw.lineSequence().firstOrNull { it.startsWith("EXIT:") }
                ?.removePrefix("EXIT:")?.toIntOrNull() ?: -1
            val output = raw.lineSequence().drop(1).joinToString("\n")
            ShellResult(code, output)
        } catch (t: Throwable) {
            AttemptLog.add("shizuku exec failed: ${t.message}")
            ShellResult(-1, t.message ?: "error")
        }
    }

    suspend fun mobileData(on: Boolean): Boolean {
        return exec("svc data " + if (on) "enable" else "disable").success
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
        softApSupported?.let { return it }
        val supported = exec("cmd wifi help").output.contains("start-softap")
        softApSupported = supported
        AttemptLog.add("shizuku start-softap supported=$supported")
        return supported
    }

    suspend fun setHotspot(on: Boolean, password: String?): Boolean {
        return if (on) {
            val pass = password ?: ""
            val quoted = shellQuote(pass)
            val candidates = if (pass.length >= 8) {
                listOf(
                    "cmd wifi start-softap ap0 wpa2 $quoted",
                    "cmd wifi start-softap ap0 wpa2-psk $quoted"
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
            if (exec("cmd wifi stop-softap ap0").success) {
                true
            } else {
                exec("cmd wifi stop-softap").success
            }
        }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
