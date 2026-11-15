package com.crystaln.vpn.desktop.net

import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.Base64

/**
 * Runs shell commands that need root. TUN-mode setup (routes, `ip tuntap`)
 * and wg-quick always need privileges on Linux; the desktop app itself must
 * stay unprivileged.
 *
 * A single privileged helper is started per app session (via `sudo -n` when
 * passwordless, otherwise `pkexec` with its one Polkit password dialog). The
 * helper is a `sh -c` loop reading base64-encoded scripts from stdin, running
 * each one and answering with `exit code` + captured output. This way root is
 * requested once per session instead of on every connect/disconnect.
 *
 * Security: the helper is fed through a pipe that only this app process holds,
 * so no other local process can inject commands into it. The temp script it
 * writes is 0700 and removed right after execution.
 */
object Privileges {

    sealed class Result {
        object Ok : Result()
        data class Fail(val output: String) : Result()
    }

    private val lock = Any()

    private const val SENTINEL_EXIT = "__CR_EXIT__"
    private const val SENTINEL_RESULT = "__CR_R:"

    private val helperShell =
        "while IFS= read -r line; do " +
            "[ \"\$line\" = \"$SENTINEL_EXIT\" ] && exit 0; " +
            "f=\$(mktemp); printf %s \"\$line\" | base64 -d > \"\$f\"; chmod 700 \"\$f\"; " +
            "out=\$(sh \"\$f\" 2>&1); c=\$?; rm -f \"\$f\"; " +
            "printf '$SENTINEL_RESULT%s:%s\\n' \"\$c\" \"\$(printf %s \"\$out\" | base64 -w0)\"; " +
            "done"

    private var helper: Process? = null
    private var helperStdin: java.io.OutputStream? = null
    private var helperStdout: BufferedReader? = null

    init {
        // When the app exits, tell the helper to stop (its read loop ends).
        Runtime.getRuntime().addShutdownHook(Thread {
            synchronized(lock) {
                try {
                    helperStdin?.let { it.write((SENTINEL_EXIT + "\n").toByteArray()); it.flush() }
                } catch (_: Throwable) {
                }
                try {
                    helperStdin?.close()
                } catch (_: Throwable) {
                }
                helper = null
            }
        })
    }

    /**
     * Runs [commands] with elevated rights. Each command must be a single
     * shell line; the script fails fast. The password is asked only when the
     * session's helper has not been started yet.
     */
    fun runScript(name: String, commands: List<String>): Result {
        val script = commands.joinToString("\n", postfix = "\n") { cmd -> "$cmd || exit 1" }
        synchronized(lock) {
            // 1) Однажды установленный systemd-сервис: без диалогов вообще.
            //    Версию проверяем ДО вызова: иначе устаревшие юниты (например,
            //    без KillMode=process) живут вечно, т.к. exec() успешен.
            if (!RootService.versionOk()) RootService.ensureInstalled()
            RootService.exec(script)?.let { return it }
            // 2) Сервис не установлен/сломан/старой версии — ставим или чиним
            //    (диалог пароля один раз), затем повторяем через сервис.
            if (RootService.ensureInstalled()) {
                RootService.exec(script)?.let { return it }
            }
            // 3) Фолбэк: сессионный хелпер через sudo -n / pkexec.
            return runViaSessionHelper(script)
        }
    }

    /** Executes [script] through the per-session root helper. */
    internal fun runViaSessionHelper(script: String): Result {
        synchronized(lock) {
            try {
                val proc = ensureHelper()
                    ?: return Result.Fail("root-хелпер недоступен (отказано в доступе?)")
                val stdin = helperStdin ?: return Result.Fail("root-хелпер не открыт")
                val stdout = helperStdout ?: return Result.Fail("root-хелпер не читается")
                stdin.write(
                    (Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_8)) + "\n")
                        .toByteArray(Charsets.UTF_8),
                )
                stdin.flush()
                val line = stdout.readLine()
                if (line == null) {
                    // Helper died (cancelled dialog, crash, logout). Reset so the
                    // next call starts a fresh one.
                    stopHelper()
                    return Result.Fail("root-хелпер завершился; повторите подключение")
                }
                if (!line.startsWith(SENTINEL_RESULT)) {
                    stopHelper()
                    return Result.Fail("неожиданный ответ root-хелпера: ${line.take(200)}")
                }
                val rest = line.removePrefix(SENTINEL_RESULT)
                val sep = rest.indexOf(':')
                if (sep < 0) {
                    stopHelper()
                    return Result.Fail("повреждённый ответ root-хелпера")
                }
                val code = rest.substring(0, sep).toIntOrNull()
                    ?: return Result.Fail("повреждённый ответ root-хелпера")
                val out = try {
                    String(
                        Base64.getDecoder().decode(rest.substring(sep + 1)),
                        Charsets.UTF_8,
                    )
                } catch (_: IllegalArgumentException) {
                    return Result.Fail("повреждённый ответ root-хелпера")
                }
                return if (code == 0) Result.Ok else Result.Fail(out.trim())
            } catch (t: Throwable) {
                stopHelper()
                return Result.Fail(t.message ?: t.javaClass.simpleName)
            }
        }
    }

    /** Starts the root helper if needed; null if it failed to stay alive. */
    private fun ensureHelper(): Process? {
        helper?.let { if (it.isAlive) return it }
        stopHelper()
        val runner = if (sudoNoPassword()) listOf("sudo", "-n") else listOf("pkexec")
        val proc = try {
            ProcessBuilder(runner + listOf("sh", "-c", helperShell))
                .apply { redirectErrorStream(false) }
                .start()
        } catch (_: Throwable) {
            return null
        }
        helper = proc
        helperStdin = proc.outputStream
        helperStdout = BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8))
        // pkexec exits immediately when the user dismisses the dialog; give it
        // a moment and check so we fail fast instead of hanging on write.
        Thread.sleep(150)
        if (!proc.isAlive) {
            stopHelper()
            return null
        }
        return proc
    }

    private fun stopHelper() {
        try {
            helperStdin?.close()
        } catch (_: Throwable) {
        }
        try {
            helper?.destroy()
        } catch (_: Throwable) {
        }
        helper = null
        helperStdin = null
        helperStdout = null
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
