package com.crystaln.vpn.desktop.net

import java.io.BufferedReader
import java.io.File

import java.net.SocketTimeoutException
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/**
 * systemd-activated privileged helper ("как в AmneziaVPN").
 *
 * On first privileged call the app installs — once, through the one-shot
 * pkexec path in [Privileges] — a systemd socket unit listening on
 * /run/crystaln-helper.sock plus a root service that executes scripts.
 * Afterwards root commands go through that service and never ask for a
 * password. Access is restricted to the uid recorded at install time
 * (SO_PEERCRED check inside the helper).
 *
 * If the service/socket is missing (not installed, systemd absent, stopped),
 * [exec] returns null and Privileges falls back to the per-session helper.
 */
object RootService {

    private const val SOCKET_PATH = "/run/crystaln-helper.sock"
    private const val HELPER_DIR = "/usr/lib/crystaln"
    private const val UID_FILE = "/etc/crystaln-helper.uid"
    private const val VERSION_FILE = "/etc/crystaln-helper.version"

    /**
     * Bump when the installed helper pieces change shape (unit mode, socket
     * path, protocol) so existing installations get repaired on next run.
     * v2: socket mode 0666 — 0660 root:root denied unprivileged peers.
     */
    private const val HELPER_VERSION = "2"
    private const val SENTINEL_RESULT = "__CR_R:"
    private const val READ_TIMEOUT_MS = 120_000
    private const val INSTALL_WAIT_MS = 5_000

    /** True when the helper pieces are installed and the socket is up. */
    fun isInstalled(): Boolean = File(SOCKET_PATH).exists()

    /**
     * Runs [script] through the installed root service, or returns null when
     * the service is unavailable (the caller should fall back / install it).
     * A protocol answer (Ok/Fail) is returned as-is — a failing script must
     * not be re-executed by the fallback path.
     */
    fun exec(script: String): Privileges.Result? {
        if (!isInstalled()) return null
        val channel = try {
            SocketChannel.open(java.net.StandardProtocolFamily.UNIX)
        } catch (_: Throwable) {
            return null
        }
        val watchdog = Thread {
            try {
                Thread.sleep(READ_TIMEOUT_MS.toLong())
                channel.close() // блокирующее чтение получит AsynchronousCloseException
            } catch (_: InterruptedException) {
            }
        }
        watchdog.isDaemon = true
        watchdog.start()
        try {
            channel.connect(UnixDomainSocketAddress.of(SOCKET_PATH))
            val payload = Base64.getEncoder().encodeToString(
                script.toByteArray(StandardCharsets.UTF_8),
            ) + "\n"
            channel.write(ByteBuffer.wrap(payload.toByteArray(StandardCharsets.UTF_8)))
            val reader = BufferedReader(
                java.nio.channels.Channels.newReader(channel, StandardCharsets.UTF_8),
            )
            val line = reader.readLine() ?: return Privileges.Result.Fail(
                "root-сервис закрыл соединение без ответа",
            )
            if (!line.startsWith(SENTINEL_RESULT)) {
                return Privileges.Result.Fail(
                    "неожиданный ответ root-сервиса: ${line.take(200)}",
                )
            }
            val rest = line.removePrefix(SENTINEL_RESULT)
            val sep = rest.indexOf(':')
            if (sep < 0) return Privileges.Result.Fail("повреждённый ответ root-сервиса")
            val code = rest.substring(0, sep).toIntOrNull()
                ?: return Privileges.Result.Fail("повреждённый ответ root-сервиса")
            val out = try {
                String(
                    Base64.getDecoder().decode(rest.substring(sep + 1)),
                    StandardCharsets.UTF_8,
                )
            } catch (_: IllegalArgumentException) {
                return Privileges.Result.Fail("повреждённый ответ root-сервиса")
            }
            return if (code == 0) Privileges.Result.Ok else Privileges.Result.Fail(out)
        } catch (_: java.nio.channels.AsynchronousCloseException) {
            // Do not re-run the script via the fallback: partial execution
            // would be worse than reporting the timeout.
            return Privileges.Result.Fail("root-сервис не ответил за 120 с")
        } catch (_: SocketTimeoutException) {
            // Do not re-run the script via the fallback: partial execution
            // would be worse than reporting the timeout.
            return Privileges.Result.Fail("root-сервис не ответил за 120 с")
        } catch (_: Throwable) {
            // Socket vanished (service stopped/removed) — let the caller fall back.
            return null
        } finally {
            watchdog.interrupt()
            try {
                channel.close()
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * One-time installation of the helper (runs as root through the one-shot
     * path in [Privileges]). Returns true when the socket is up afterwards.
     */
    fun ensureInstalled(): Boolean {
        if (isInstalled() && installedVersionOk()) return true
        if (!python3Available()) return false
        val uid = currentUid() ?: return false
        val dir = try {
            Files.createTempDirectory("crystaln-helper")
        } catch (_: Throwable) {
            return false
        }
        val files = listOf("crystaln-helper.py", "crystaln-helper.socket", "crystaln-helper@.service")
        for (f in files) {
            if (!extractResource("crystaln/$f", dir.resolve(f))) return false
        }
        val py = dir.resolve("crystaln-helper.py")
        val sock = dir.resolve("crystaln-helper.socket")
        val svc = dir.resolve("crystaln-helper@.service")
        val res = Privileges.runViaSessionHelper(
            listOf(
                "mkdir -p $HELPER_DIR /etc/systemd/system",
                "install -m 755 $py $HELPER_DIR/crystaln-helper.py",
                "install -m 644 $sock /etc/systemd/system/crystaln-helper.socket",
                "install -m 644 $svc /etc/systemd/system/crystaln-helper@.service",
                "echo $uid > $UID_FILE",
                "chmod 600 $UID_FILE",
                "echo $HELPER_VERSION > $VERSION_FILE",
                "chmod 644 $VERSION_FILE",
                "systemctl daemon-reload || true",
                "systemctl enable crystaln-helper.socket || true",
                "systemctl restart crystaln-helper.socket || true",
            ).joinToString("\n", postfix = "\n") { "$it || exit 1" },
        )
        dir.toFile().deleteRecursively()
        if (res is Privileges.Result.Fail) return false
        // The socket unit is enabled synchronously; still, give it a moment.
        val deadline = System.currentTimeMillis() + INSTALL_WAIT_MS
        while (System.currentTimeMillis() < deadline) {
            if (isInstalled()) return true
            try {
                Thread.sleep(200)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return false
    }

    /** True when the installed helper matches [HELPER_VERSION] (or unknown). */
    private fun installedVersionOk(): Boolean = try {
        File(VERSION_FILE).readText().trim() == HELPER_VERSION
    } catch (_: Throwable) {
        false
    }

    private fun extractResource(name: String, target: Path): Boolean = try {
        val stream = RootService::class.java.classLoader.getResourceAsStream(name) ?: return false
        stream.use { input ->
            Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        val file = target.toFile()
        file.setReadable(true, false) // the root installer must read these
        file.setWritable(false, false)
        true
    } catch (_: Throwable) {
        false
    }

    private fun python3Available(): Boolean = File("/usr/bin/python3").exists()

    private fun currentUid(): String? = try {
        val proc = ProcessBuilder("id", "-u")
            .redirectErrorStream(true)
            .start()
        val out = proc.inputStream.readBytes().toString(Charsets.UTF_8).trim()
        proc.waitFor()
        if (proc.exitValue() == 0 && out.all { it.isDigit() }) out else null
    } catch (_: Throwable) {
        null
    }
}
