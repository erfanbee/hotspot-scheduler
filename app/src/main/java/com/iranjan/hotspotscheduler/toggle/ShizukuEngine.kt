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
                    .version(2)
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

    /**
     * Start the hotspot. Uses cached SSID/passphrase when available so the user's
     * configured network name is preserved; the config from `cmd wifi` is session-only.
     */
    suspend fun setHotspot(
        on: Boolean,
        ssid: String?,
        passphrase: String?,
        openNetwork: Boolean = false
    ): Boolean {
        if (on) {
            val cmd = if (openNetwork) {
                HotspotCommands.startSoftapOpenCmd(ssid ?: HotspotCommands.DEFAULT_SSID)
            } else {
                val c = HotspotCommands.startSoftapCmd(ssid ?: HotspotCommands.DEFAULT_SSID, passphrase ?: "")
                if (c == null) {
                    AttemptLog.add("shizuku hotspot start rejected: invalid passphrase")
                    return false
                }
                c
            }
            val result = exec(cmd)
            val outcome = HotspotCommands.parseStartOutcome(result.output)
            AttemptLog.add("shizuku start-softap exit=${result.exitCode} outcome=$outcome out='${result.output.take(200)}'")
            return when (outcome) {
                HotspotCommands.StartOutcome.STARTED -> true
                HotspotCommands.StartOutcome.FAILED -> false
                HotspotCommands.StartOutcome.UNKNOWN -> {
                    // `cmd wifi start-softap` always exits 0; when callback output is
                    // missing, verify the actual state before believing it.
                    hotspotState()?.on == true
                }
            }
        } else {
            val result = exec(HotspotCommands.stopSoftapCmd())
            AttemptLog.add("shizuku stop-softap exit=${result.exitCode} out='${result.output.take(200)}'")
            if (!result.success) return false
            val state = hotspotState()
            return state?.on == false || state == null
        }
    }

    /** Hotspot state via a grep-filtered dumpsys probe (fits the binder transaction limit). */
    suspend fun hotspotState(): HotspotCommands.StateProbe? {
        val result = exec(HotspotCommands.stateProbeCmd())
        return HotspotCommands.parseStateProbe(result.output)
    }
}
