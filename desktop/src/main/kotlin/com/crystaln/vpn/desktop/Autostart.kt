package com.crystaln.vpn.desktop

/**
 * Автозагрузка приложения при входе в систему — Windows:
 * значение `CrystalN` в реестре `HKCU\Software\Microsoft\Windows\CurrentVersion\Run`
 * (не требует прав администратора, путь к установленному exe с кавычками).
 *
 * Точкой правды является состояние реестра: [isEnabled] проверяет его,
 * поэтому настройка само-восстанавливается после переустановки приложения
 * (см. [sync]).
 */
object Autostart {

    private const val WIN_VALUE = "CrystalN"
    private const val RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"

    fun isSupported(): Boolean = Platform.isWindows

    fun isEnabled(): Boolean = try {
        if (Platform.isWindows) winQuery() != null else false
    } catch (_: Throwable) {
        false
    }

    /** Возвращает true при успехе; вызывающий код покажет уведомление. */
    fun setEnabled(enable: Boolean): Boolean {
        if (!Platform.isWindows) return false
        return try {
            val exe = exePath() ?: return false
            if (enable) winAdd(exe) else winRemove()
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Само-восстановление при старте: если в настройках автозапуск включён,
     * а запись ОС отсутствует (переустановка, смена пути) — тихо восстанавливаем.
     */
    fun sync(enabled: Boolean) {
        if (!isSupported()) return
        if (enabled && !isEnabled()) setEnabled(true)
    }

    /**
     * Полный путь к текущему исполняемому файлу приложения. В установленном
     * jpackage-приложении это и есть запускаемый exe/бинарник; для dev-запуска
     * (gradle run) команда указывает на java — тогда автозагрузка невозможна.
     */
    private fun exePath(): String? {
        val cmd = ProcessHandle.current().info().command().orElse(null)?.trim()
            ?.takeIf { it.isNotEmpty() } ?: return null
        val name = cmd.substringAfterLast('/', "").substringAfterLast('\\', "").lowercase()
        if (name == "java" || name == "java.exe" || name == "javaw.exe") return null
        return cmd
    }

    // ── Windows (HKCU Run) ───────────────────────────────────────────────

    private fun winQuery(): String? {
        val p = ProcessBuilder("reg", "query", RUN_KEY, "/v", WIN_VALUE)
            .apply { redirectErrorStream(true) }.start()
        val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
        p.waitFor()
        if (p.exitValue() != 0) return null
        return out.lineSequence().firstOrNull { it.contains(WIN_VALUE, ignoreCase = true) }
    }

    private fun winAdd(exe: String): Boolean {
        val p = ProcessBuilder(
            "reg", "add", RUN_KEY, "/v", WIN_VALUE, "/t", "REG_SZ", "/d", "\"$exe\"", "/f",
        ).apply { redirectErrorStream(true) }.start()
        val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
        p.waitFor()
        if (p.exitValue() != 0) {
            com.crystaln.vpn.desktop.net.Privileges.appendLog(
                "autostart.log",
                "reg add exit ${p.exitValue()}: ${out.take(200)}\n",
            )
        }
        return p.exitValue() == 0
    }

    private fun winRemove(): Boolean {
        val p = ProcessBuilder("reg", "delete", RUN_KEY, "/v", WIN_VALUE, "/f")
            .apply { redirectErrorStream(true) }.start()
        p.inputStream.readBytes()
        p.waitFor()
        return p.exitValue() == 0
    }
}
