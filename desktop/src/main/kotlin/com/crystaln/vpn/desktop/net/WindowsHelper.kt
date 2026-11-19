package com.crystaln.vpn.desktop.net

import com.crystaln.vpn.desktop.AppDirs
import java.io.File
import java.util.Base64
import java.util.UUID

/**
 * Privileged command execution on Windows, the counterpart of [Privileges]
 * (root helper / systemd service on Linux).
 *
 * The desktop app itself stays unprivileged; commands that need admin rights
 * (routes, `amneziawg.exe /installtunnelservice`, starting tun2socks and
 * openvpn, which create wintun adapters) go through a persistent elevated
 * helper process. One UAC prompt per app session — the Windows equivalent of
 * the single Polkit/root dialog on Linux.
 *
 * Protocol (file queue, because a UAC'd `Start-Process` cannot be combined
 * with redirected stdio):
 * 1. App writes `priv/cmd/<id>.txt`: first line `#token=<session token>`,
 *    the rest is a PowerShell script.
 * 2. The helper (a hidden elevated powershell running [HELPER_SCRIPT])
 *    verifies the token, executes the script in a nested hidden powershell,
 *    and writes `priv/resp/<id>.txt` with `__CR_R:<exit>:<base64 output>`.
 * 3. The helper stops when `priv/stop` appears (app shutdown).
 *
 * Security: scripts in the queue are executed only when the first line
 * matches the per-session token, so other local processes cannot inject
 * commands into the elevated helper. The priv directory is created with
 * default user ACLs (other users cannot write to %LOCALAPPDATA%).
 */
object WindowsHelper {

    private val privDir: File get() = File(AppDirs.dataDir(), "priv")
    private val cmdDir: File get() = File(privDir, "cmd")
    private val respDir: File get() = File(privDir, "resp")
    private val stopFile: File get() = File(privDir, "stop")
    private val readyFile: File get() = File(privDir, "ready")
    private val helperPidFile: File get() = File(privDir, "helper.pid")

    private val token: String = UUID.randomUUID().toString()

    private val lock = Any()
    private var helperStarted = false
    private var readyObserved = false

    private const val RESP_PREFIX = "__CR_R:"

    init {
        Runtime.getRuntime().addShutdownHook(Thread { shutdown() })
    }

    /** True when the JVM itself already runs elevated (no helper needed). */
    fun isElevated(): Boolean = try {
        ProcessBuilder("net", "session").start().waitFor() == 0
    } catch (_: Throwable) {
        false
    }

    /**
     * Runs [commands] as an elevated PowerShell script (fail fast, like the
     * Linux `cmd || exit 1`). Returns [Privileges.Result.Ok] with the last
     * success or [Privileges.Result.Fail] with captured output.
     */
    fun runScript(name: String, commands: List<String>): Privileges.Result {
        if (commands.isEmpty()) return Privileges.Result.Ok
        // PS cmdlets do not touch $LASTEXITCODE, native commands set it; reset
        // it before each command so failure semantics are fail-fast per line
        // (the Windows equivalent of the Linux `cmd || exit 1`).
        val body = buildString {
            // command-not-found и падения cmdlet'ов не задают $LASTEXITCODE —
            // поэтому fail-fast двойной: EAP=Stop для ошибок уровня PS и
            // явная проверка $LASTEXITCODE для native-команд (route, netsh…).
            appendLine("\$ErrorActionPreference = 'Stop'")
            commands.forEach { cmd ->
                appendLine("\$LASTEXITCODE = 0")
                appendLine(cmd)
                appendLine("if (\$LASTEXITCODE -ne 0) { exit \$LASTEXITCODE }")
            }
        }
        return synchronized(lock) {
            if (isElevated()) {
                runDirect(name, body)
            } else {
                runViaHelper(name, body)
            }
        }
    }

    /** Executes a PowerShell script directly (assumes the JVM is elevated). */
    private fun runDirect(name: String, body: String): Privileges.Result {
        privDir.mkdirs()
        val script = File(privDir, "direct-$name-${System.nanoTime()}.ps1")
        return try {
            script.writeText(body)
            runPowerShellCapture(script)
        } catch (t: Throwable) {
            Privileges.Result.Fail(t.message ?: t.javaClass.simpleName)
        } finally {
            script.delete()
        }
    }

    /** Executes a script through the elevated helper (one UAC per session). */
    private fun runViaHelper(name: String, body: String): Privileges.Result {
        cmdDir.mkdirs()
        respDir.mkdirs()
        if (!ensureHelper()) {
            return Privileges.Result.Fail(
                "Повышение прав отменено или недоступно (UAC). " +
                    "Подтвердите запрос Windows и повторите подключение.",
            )
        }
        val id = "$name-${System.nanoTime()}"
        val cmd = File(cmdDir, "$id.txt")
        val resp = File(respDir, "$id.txt")
        cmd.delete()
        resp.delete()
        cmd.writeText("#token=$token\n$body")
        // Scripts include waiting for adapters/daemons; give them minutes.
        val deadline = System.currentTimeMillis() + 300_000
        while (System.currentTimeMillis() < deadline) {
            if (resp.exists()) {
                cmd.delete()
                return parseResp(resp).also { resp.delete() }
            }
            Thread.sleep(120)
        }
        cmd.delete()
        return Privileges.Result.Fail("Таймаут выполнения привилегированного скрипта")
    }

    private fun parseResp(resp: File): Privileges.Result = try {
        val text = resp.readText().trim()
        if (!text.startsWith(RESP_PREFIX)) {
            Privileges.Result.Fail("Неожиданный ответ хелпера: ${text.take(120)}")
        } else {
            val rest = text.removePrefix(RESP_PREFIX)
            val sep = rest.indexOf(':')
            val code = if (sep < 0) null else rest.substring(0, sep).toIntOrNull()
            if (code == null) {
                Privileges.Result.Fail("Повреждённый ответ хелпера")
            } else {
                val out = String(
                    Base64.getDecoder().decode(rest.substring(sep + 1)),
                    Charsets.UTF_8,
                )
                if (code == 0) Privileges.Result.Ok else Privileges.Result.Fail(out.trim())
            }
        }
    } catch (t: Throwable) {
        Privileges.Result.Fail(t.message ?: "повреждённый ответ хелпера")
    }

    /**
     * Starts the persistent elevated helper (one UAC prompt) and waits for it
     * to come up. Skipped entirely when it already reported ready.
     */
    private fun ensureHelper(): Boolean {
        if (readyObserved && readyFile.exists()) return true
        stopFile.delete()
        readyFile.delete()
        // The session token must NOT appear in a process command line (any
        // local process can read Win32_Process CommandLine via WMI). Pass the
        // path of an ACL-restricted token file instead; the elevated helper
        // reads the token from that file.
        val helper = File(privDir, "helper.ps1")
        privDir.mkdirs()
        helper.writeText(HELPER_SCRIPT)
        val tokenFile = File(privDir, "token")
        // Remove a stale token file from a previous run: its ACL may only
        // grant read access, which would make overwriting it fail.
        tokenFile.delete()
        tokenFile.writeText(token)
        try {
            ProcessBuilder(
                "icacls", tokenFile.absolutePath,
                "/inheritance:r", "/grant:r", "${System.getProperty("user.name")}:(RX)",
            ).start().waitFor()
        } catch (_: Throwable) {
        }
        val args = listOf(
            "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
            "-Command",
            "Start-Process powershell -Verb RunAs -WindowStyle Hidden -ArgumentList " +
                "'-NoProfile','-ExecutionPolicy','Bypass','-WindowStyle','Hidden'," +
                "'-File','$helper','-Dir','$privDir','-TokenFile','${tokenFile.absolutePath}'",
        )
        val proc = ProcessBuilder("powershell", *args.toTypedArray())
            .apply { redirectErrorStream(true) }
            .start()
        val out = proc.inputStream.bufferedReader().use { it.readText() } // drain output
        proc.waitFor()
        // Пользователь отменил UAC: Start-Process -Verb RunAs бросает внутри
        // лаунчера → его powershell выходит с ненулевым кодом. Fail fast.
        if (proc.exitValue() != 0) {
            Privileges.appendLog(
                "windows-helper.log",
                "UAC launcher exit ${proc.exitValue()}: ${out.take(400)}\n",
            )
            return false
        }
        // UAC dialog may take a while; the helper creates the ready marker.
        val deadline = System.currentTimeMillis() + 120_000
        while (System.currentTimeMillis() < deadline) {
            if (readyFile.exists()) {
                readyObserved = true
                helperStarted = true
                return true
            }
            Thread.sleep(250)
        }
        return false
    }

    /** App shutdown: stop the helper and clean the queue. */
    fun shutdown() {
        try {
            stopStatsPoller()
            stopFile.parentFile?.mkdirs()
            stopFile.writeText(token)
            helperPidFile.takeIf { it.exists() }?.readText()?.trim()?.let { pid ->
                Runtime.getRuntime().exec(arrayOf("taskkill", "/PID", pid, "/F"))
            }
            // Best-effort cleanup of leftovers from this session.
            cmdDir.listFiles()?.forEach { if (it.readText().startsWith("#token=$token")) it.delete() }
        } catch (_: Throwable) {
        }
    }

    // ── traffic statistics ───────────────────────────────────────────────

    /**
     * Persistent 1 Hz poller for a network adapter's byte counters (the
     * Windows replacement of /sys/class/net/<dev>/statistics). Returns the
     * file with "rx,tx" lines that [readStatsFile] consumes.
     */
    fun startStatsPoller(device: String): File {
        stopStatsPoller()
        val dir = File(privDir, "stats")
        dir.mkdirs()
        val statsFile = File(dir, "$device.txt")
        val stop = File(dir, "$device.stop")
        stop.delete()
        statsFile.delete()
        val script = """
            |param([string]${'$'}Dev, [string]${'$'}Out, [string]${'$'}Stop)
            |while (${'$'}true) {
            |  if (Test-Path ${'$'}Stop) { break }
            |  try {
            |    ${'$'}s = Get-NetAdapterStatistics -Name ${'$'}Dev -ErrorAction Stop
            |    ("{0},{1}" -f ${'$'}s.ReceivedBytes, ${'$'}s.SentBytes) | Set-Content -Path ${'$'}Out
            |  } catch {
            |    "0,0" | Set-Content -Path ${'$'}Out
            |  }
            |  Start-Sleep -Milliseconds 1000
            |}
        """.trimMargin()
        val ps = File(dir, "poller.ps1")
        ps.writeText(script)
        try {
            ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-WindowStyle", "Hidden", "-File", ps.absolutePath,
                "-Dev", device, "-Out", statsFile.absolutePath, "-Stop", stop.absolutePath,
            ).start()
        } catch (_: Throwable) {
        }
        return statsFile
    }

    fun stopStatsPoller() {
        val dir = File(privDir, "stats")
        dir.listFiles { f -> f.name.endsWith(".stop") }?.forEach { it.writeText("stop") }
    }

    /** Parses the poller file: latest "rx,tx" line, 0 when absent/garbage. */
    fun readStatsFile(file: File?): Pair<Long, Long>? {
        if (file == null || !file.exists()) return null
        return try {
            val lines = file.readText().trim().lines()
            val last = lines.lastOrNull { it.isNotBlank() } ?: return null
            val parts = last.split(',')
            if (parts.size != 2) return null
            (parts[0].trim().toLongOrNull() ?: 0L) to (parts[1].trim().toLongOrNull() ?: 0L)
        } catch (_: Throwable) {
            null
        }
    }

    private fun runPowerShellCapture(script: File): Privileges.Result = try {
        val p = ProcessBuilder(
            "powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
            "-File", script.absolutePath,
        ).apply { redirectErrorStream(true) }.start()
        val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
        val code = p.waitFor()
        if (code == 0) Privileges.Result.Ok else Privileges.Result.Fail(out.trim().ifBlank { "exit $code" })
    } catch (t: Throwable) {
        Privileges.Result.Fail(t.message ?: t.javaClass.simpleName)
    }

    /**
     * The elevated helper. Executes queued scripts in a nested hidden
     * powershell and answers with `__CR_R:<exit>:<base64 output>` files.
     */
    private val HELPER_SCRIPT = """
        |param([string]${'$'}Dir, [string]${'$'}TokenFile)
        |${'$'}ErrorActionPreference = 'Continue'
        |${'$'}Token = [System.IO.File]::ReadAllText(${'$'}TokenFile).Trim()
        |${'$'}cmdDir = Join-Path ${'$'}Dir 'cmd'
        |${'$'}respDir = Join-Path ${'$'}Dir 'resp'
        |${'$'}stopFile = Join-Path ${'$'}Dir 'stop'
        |${'$'}pidFile = Join-Path ${'$'}Dir 'helper.pid'
        |[System.IO.File]::WriteAllText(${'$'}pidFile, ${'$'}PID)
        |New-Item -ItemType Directory -Force ${'$'}respDir | Out-Null
        |[System.IO.File]::WriteAllText((Join-Path ${'$'}Dir 'ready'), 'ok')
        |while (${'$'}true) {
        |  if (Test-Path ${'$'}stopFile) { break }
        |  ${'$'}files = @()
        |  try { ${'$'}files = Get-ChildItem ${'$'}cmdDir -Filter '*.txt' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime } catch {}
        |  foreach (${'$'}f in ${'$'}files) {
        |    ${'$'}id = ${'$'}f.BaseName
        |    ${'$'}resp = Join-Path ${'$'}respDir (${'$'}id + '.txt')
        |    try {
        |      ${'$'}lines = [System.IO.File]::ReadAllLines(${'$'}f.FullName)
        |      if (${'$'}lines.Length -lt 1 -or ${'$'}lines[0] -ne ('#token=' + ${'$'}Token)) {
        |        [System.IO.File]::WriteAllText(${'$'}resp, '__CR_R:97:Zm9yZWlnbiB0b2tlbg==')
        |        Remove-Item ${'$'}f.FullName -Force -ErrorAction SilentlyContinue
        |        continue
        |      }
        |      ${'$'}body = (${'$'}lines | Select-Object -Skip 1) -join "`r`n"
        |      ${'$'}run = Join-Path ${'$'}Dir ('run-' + ${'$'}id + '.ps1')
        |      [System.IO.File]::WriteAllText(${'$'}run, ${'$'}body)
        |      ${'$'}out = & powershell -NoProfile -NonInteractive -ExecutionPolicy Bypass -File ${'$'}run 2>&1
        |      ${'$'}code = ${'$'}LASTEXITCODE
        |      if (${'$'}code -eq ${'$'}null) { ${'$'}code = 0 }
        |      ${'$'}text = (${'$'}out | Out-String)
        |      ${'$'}b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(${'$'}text))
        |      [System.IO.File]::WriteAllText(${'$'}resp, ('__CR_R:{0}:{1}' -f ${'$'}code, ${'$'}b64))
        |      Remove-Item ${'$'}run, ${'$'}f.FullName -Force -ErrorAction SilentlyContinue
        |    } catch {
        |      ${'$'}m = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes(('helper: ' + ${'$'}_.Exception.Message)))
        |      [System.IO.File]::WriteAllText(${'$'}resp, ('__CR_R:98:{0}' -f ${'$'}m))
        |      Remove-Item ${'$'}f.FullName -Force -ErrorAction SilentlyContinue
        |    }
        |  }
        |  Start-Sleep -Milliseconds 150
        |}
        |Remove-Item (Join-Path ${'$'}Dir 'ready') -Force -ErrorAction SilentlyContinue
        |Remove-Item ${'$'}pidFile -Force -ErrorAction SilentlyContinue
    """.trimMargin()
}
