package com.crystaln.vpn.desktop.net

import java.io.File
import java.io.FileOutputStream

/**
 * Runs shell commands that need root. TUN-mode setup (routes, `ip tuntap`)
 * and wg-quick always need privileges on Linux; the desktop app itself must
 * stay unprivileged, so privileged work is a generated script executed once
 * via `sudo -n` (passwordless) or `pkexec` (Polkit password dialog).
 */
object Privileges {

    sealed class Result {
        object Ok : Result()
        data class Fail(val output: String) : Result()
    }

    private val lock = Any()

    /**
     * Writes [commands] into a temp script and runs it with elevated rights.
     * Each command must be a single shell line; the script fails fast.
     */
    fun runScript(name: String, commands: List<String>): Result {
        val script = File(System.getProperty("java.io.tmpdir"), "crystaln-$name-${System.nanoTime()}.sh")
        try {
            script.writeText(commands.joinToString("\n", postfix = "\n") { cmd -> "$cmd || exit 1" })
            script.setReadable(true, false)
            script.setExecutable(true, true)
            synchronized(lock) {
                val runner = if (sudoNoPassword()) "sudo -n" else "pkexec"
                val proc = ProcessBuilder(
                    runner.split(" ")[0],
                    *runner.split(" ").drop(1).toTypedArray(),
                    "sh",
                    script.absolutePath,
                ).apply { redirectErrorStream(true) }.start()
                val out = proc.inputStream.readBytes().toString(Charsets.UTF_8)
                val code = proc.waitFor()
                return if (code == 0) Result.Ok else Result.Fail(out.trim())
            }
        } catch (t: Throwable) {
            return Result.Fail(t.message ?: t.javaClass.simpleName)
        } finally {
            script.delete()
        }
    }

    private fun sudoNoPassword(): Boolean = try {
        ProcessBuilder("sudo", "-n", "true")
            .redirectErrorStream(true)
            .start()
            .waitFor() == 0
    } catch (_: Throwable) {
        false
    }

    /** Logs [text] to the app's log dir (never throws). */
    fun appendLog(name: String, text: String) {
        try {
            val dir = com.crystaln.vpn.desktop.AppDirs.logDir()
            dir.mkdirs()
            FileOutputStream(File(dir, name), true).use { it.write(text.toByteArray()) }
        } catch (_: Throwable) {
        }
    }
}
