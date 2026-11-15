package com.crystaln.vpn.desktop.net

import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * A supervised child process (xray / openvpn): destroys it on [stop], exposes
 * the last output lines for the UI log and survives UI restarts within the
 * JVM lifetime.
 */
class ChildProcess(
    private val name: String,
    command: List<String>,
    private val onLine: (String) -> Unit,
) {
    @Volatile
    private var proc: Process? = null

    private val readerThread = AtomicReference<Thread?>()

    val alive: Boolean get() = proc?.isAlive ?: false

    init {
        try {
            val p = ProcessBuilder(command).apply { redirectErrorStream(true) }.start()
            proc = p
            val t = Thread {
                try {
                    p.inputStream.bufferedReader().forEachLine { line ->
                        Privileges.appendLog("$name.log", line + "\n")
                        onLine(line)
                    }
                } catch (_: Exception) {
                }
            }.apply {
                isDaemon = true
                name = "crystaln-$name-log"
                start()
            }
            readerThread.set(t)
        } catch (t: Throwable) {
            onLine("не удалось запустить: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    /** Returns non-zero exit code if the process already died. */
    fun stop() {
        val p = proc ?: return
        if (p.isAlive) {
            p.destroy()
            try {
                if (!p.waitFor(3_000, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    p.destroyForcibly()
                    p.waitFor(2_000, java.util.concurrent.TimeUnit.MILLISECONDS)
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    fun exitCode(): Int? = proc?.let { if (it.isAlive) null else it.exitValue() }

    companion object {
        /** True when the command is present on PATH. */
        fun commandExists(cmd: String): Boolean = try {
            ProcessBuilder("which", cmd).start().waitFor() == 0 &&
                ProcessBuilder("which", cmd).start().inputStream.readBytes()
                    .toString(Charsets.UTF_8).isNotBlank()
        } catch (_: Throwable) {
            false
        }

        fun bin(dir: File, name: String): String {
            val f = File(dir, name)
            return if (f.exists()) f.absolutePath else name
        }
    }
}
