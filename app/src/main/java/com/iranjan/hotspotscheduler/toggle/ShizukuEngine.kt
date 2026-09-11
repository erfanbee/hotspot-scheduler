package com.iranjan.hotspotscheduler.toggle

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.iranjan.hotspotscheduler.accessibility.AttemptLog
import dagger.hilt.android.qualifiers.ApplicationContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuUserServiceArgs
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

    private var shellService: IShellService? = null
    private val bindLatch = CountDownLatch(1)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            shellService = binder?.let { IShellService.Stub.asInterface(it) }
            AttemptLog.add("shizuku shell service connected=${shellService != null}")
            bindLatch.countDown()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shellService = null
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

    private fun bindService() {
        val args = ShizukuUserServiceArgs(ComponentName(context, ShellService::class.java))
            .processNameSuffix("shell")
            .version(1)
            .debuggable(false)
        Shizuku.bindUserService(args, connection)
    }

    private fun awaitService(): IShellService? {
        if (shellService != null) return shellService
        return try {
            bindService()
            bindLatch.await(10, TimeUnit.SECONDS)
            shellService
        } catch (t: Throwable) {
            AttemptLog.add("shizuku bind failed: ${t.message}")
            null
        }
    }

    suspend fun exec(command: String): ShellResult = withContext(Dispatchers.IO) {
        val service = awaitService() ?: return@withContext ShellResult(-1, "service not bound")
        try {
            val raw = service.runCommand(command)
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
            if (exec("cmd wifi stop-softap ap0").success) {
                true
            } else {
                exec("cmd wifi stop-softap").success
            }
        }
    }
}
