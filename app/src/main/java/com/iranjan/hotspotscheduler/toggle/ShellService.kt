package com.iranjan.hotspotscheduler.toggle

import java.util.concurrent.TimeUnit

class ShellService : IShellService.Stub() {

    override fun runCommand(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(20, TimeUnit.SECONDS)
            val output = process.inputStream.bufferedReader().readText()
            if (!finished) {
                process.destroyForcibly()
                "EXIT:124\noutput:timeout"
            } else {
                "EXIT:${process.exitValue()}\n$output"
            }
        } catch (t: Throwable) {
            "EXIT:-1\n${t.message ?: "error"}"
        }
    }

    override fun exit() {
        System.exit(0)
    }
}
