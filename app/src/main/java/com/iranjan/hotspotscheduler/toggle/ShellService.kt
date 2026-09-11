package com.iranjan.hotspotscheduler.toggle

import java.util.concurrent.TimeUnit

class ShellService : IShellService.Stub() {

    override fun runCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val finished = process.waitFor(15, TimeUnit.SECONDS)
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            if (!finished) {
                process.destroyForcibly()
                "EXIT:124\noutput:timeout"
            } else {
                "EXIT:${process.exitValue()}\n$stdout$stderr"
            }
        } catch (t: Throwable) {
            "EXIT:-1\n${t.message ?: "error"}"
        }
    }

    override fun exit() {
        System.exit(0)
    }
}
