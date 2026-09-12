package com.iranjan.hotspotscheduler.toggle

import java.util.concurrent.TimeUnit

class ShellService : IShellService.Stub() {

    override fun runCommand(command: String): String {
        return try {
            val process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = StringBuilder()
            val reader = Thread {
                try {
                    process.inputStream.bufferedReader().use { br ->
                        val buf = CharArray(8192)
                        while (true) {
                            val n = br.read(buf)
                            if (n < 0) break
                            output.append(buf, 0, n)
                            if (output.length > MAX_OUTPUT_CHARS) break
                        }
                    }
                } catch (t: Throwable) {
                }
            }
            reader.start()
            val finished = process.waitFor(20, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                "EXIT:124\n" + output.toString() + "\n[timeout after 20s]"
            } else {
                try { reader.join(2000) } catch (t: Throwable) {}
                "EXIT:${process.exitValue()}\n" + output.toString()
            }
        } catch (t: Throwable) {
            "EXIT:-1\n${t.message ?: "error"}"
        }
    }

    override fun exit() {
        System.exit(0)
    }

    companion object {
        private const val MAX_OUTPUT_CHARS = 1 shl 20
    }
}
