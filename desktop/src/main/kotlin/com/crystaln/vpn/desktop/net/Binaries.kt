package com.crystaln.vpn.desktop.net

import com.crystaln.vpn.desktop.AppDirs
import java.io.File
import java.net.URL
import java.util.zip.ZipFile

/**
 * External binaries needed by the desktop app: the official Xray-core build,
 * tun2socks (TUN mode on top of the local socks proxy), the userspace
 * AmneziaWG daemon with its `awg` configuration utility, and the geosite
 * database for ad blocking. Everything is downloaded once into
 * ~/.local/share/crystaln/bin; connect() refuses to run without them.
 *
 * amneziawg-go is built from upstream by the `awg.yml` GitHub workflow in the
 * public `lightcodegit/crystaln-binaries` repository (this private repository's
 * release assets cannot be downloaded without a GitHub token).
 */
object Binaries {

    const val XRAY = "xray"
    const val TUN2SOCKS = "tun2socks"
    const val AWG_GO = "amneziawg-go"
    const val AWG = "awg"

    /** Официальный клиент Hysteria2 (child-процесс с локальным SOCKS5). */
    const val HYSTERIA = "hysteria"

    /** Windows: amneziawg.exe — менеджер туннель-сервисов AmneziaWG. */
    const val AWG_SERVICE = "amneziawg"

    private const val AWG_GO_RELEASE =
        "https://github.com/lightcodegit/crystaln-binaries/releases/download/awg-go-bin"
    private const val AWG_TOOLS_ZIP =
        "https://github.com/amnezia-vpn/amneziawg-tools/releases/download/" +
            "v3.1.20260812/ubuntu-22.04-amneziawg-tools.zip"

    // Windows: AmneziaWG и OpenVPN распространяются MSI-установщиками, но их
    // можно распаковать БЕЗ установки (`msiexec /a`) и использовать портативно.
    private const val AWG_WIN_RELEASE =
        "https://github.com/amnezia-vpn/amneziawg-windows-client/releases/download/3.1.0"
    private const val OPENVPN_MSI_URL =
        "https://swupdate.openvpn.org/community/releases/OpenVPN-2.7.9-I002-%s.msi"

    data class Status(
        val xrayReady: Boolean,
        val tun2socksReady: Boolean,
        val geositeReady: Boolean,
        val awgReady: Boolean,
        val hysteriaReady: Boolean,
        /** Windows: wintun.dll лежит рядом с tun2socks/openvpn (иначе TUN нем). */
        val wintunReady: Boolean = true,
        /** Windows: портативный openvpn извлечён в bin/openvpn (на Linux системный). */
        val openVpnReady: Boolean = false,
    ) {
        val proxyModeReady: Boolean get() = xrayReady
        val tunModeReady: Boolean get() = xrayReady && tun2socksReady && wintunReady
    }

    private val p = com.crystaln.vpn.desktop.Platform

    fun status(): Status {
        val bin = AppDirs.binDir()
        // На Windows файлы называются xray.exe и т.д. — через Platform.binName.
        return Status(
            xrayReady = executable(File(bin, p.binName(XRAY))),
            tun2socksReady = executable(File(bin, p.binName(TUN2SOCKS))),
            geositeReady = File(bin, "geosite.dat").exists(),
            hysteriaReady = executable(File(bin, p.binName(HYSTERIA))),
            awgReady = if (p.isWindows) {
                executable(File(bin, p.binName(AWG_SERVICE))) &&
                    executable(File(bin, p.binName(AWG)))
            } else {
                executable(File(bin, p.binName(AWG_GO))) &&
                    executable(File(bin, p.binName(AWG)))
            },
            wintunReady = !p.isWindows || File(bin, "wintun.dll").exists(),
            openVpnReady = p.isWindows && openVpnExecutable().exists(),
        )
    }

    /** Портативный openvpn.exe, извлечённый из MSI (только Windows). */
    fun openVpnExecutable(): File =
        File(AppDirs.binDir(), "openvpn/bin/openvpn${p.exeSuffix}")

    /** wintun.dll — общий драйвер TUN для tun2socks/openvpn/amneziawg. */
    fun wintunFile(): File = File(AppDirs.binDir(), "wintun.dll")

    private fun executable(file: File) = file.exists() && file.canExecute()

    private fun archSuffix(): String {
        val a = System.getProperty("os.arch") ?: "amd64"
        return when {
            a.contains("aarch64") || a.contains("arm64") -> "arm64-v8a"
            a.contains("x86_64") || a.contains("amd64") -> "64"
            else -> throw IllegalStateException("Unsupported CPU: $a")
        }
    }

    /** Короткое имя архитектуры для апстримов (amd64/arm64). */
    private fun upstreamArch(): String =
        if (archSuffix() == "arm64-v8a") "arm64" else "amd64"

    /**
     * Downloads and unpacks the missing pieces. Called from a background
     * dispatcher; [progress] receives human-readable steps.
     */
    fun downloadAll(
        needXray: Boolean = true,
        needTun2socks: Boolean = true,
        needGeosite: Boolean = false,
        needAwg: Boolean = true,
        needOpenVpn: Boolean = true,
        needHysteria: Boolean = true,
        progress: (String) -> Unit = {},
    ) {
        val bin = AppDirs.binDir()
        bin.mkdirs()
        val arch = archSuffix()
        val isWin = p.isWindows
        if (needHysteria && !status().hysteriaReady) {
            progress("Загрузка Hysteria2…")
            // Ассеты — сырые бинарники: hysteria-windows-amd64.exe (без zip),
            // hysteria-linux-amd64. Windows-версию нельзя качать сразу в целевой
            // файл: downloadTo пишет через .tmp, поэтому качаем и переименовываем.
            val asset = if (isWin) {
                "hysteria-windows-$arch.exe"
            } else {
                "hysteria-linux-$arch"
            }
            val tmp = downloadTo(
                "https://github.com/HyNetworks/hysteria/releases/download/app/v2.14.0/$asset",
                File(bin, "hysteria.tmp"),
            )
            val out = File(bin, p.binName(HYSTERIA))
            if (!tmp.renameTo(out)) out.writeBytes(tmp.readBytes())
            out.setExecutable(true)
        }
        if (needXray && !status().xrayReady) {
            progress("Загрузка Xray-core…")
            // Windows-сборка называется Xray-windows-64.zip и содержит xray.exe.
            val zipName = if (isWin) "Xray-windows-$arch.zip" else "Xray-linux-$arch.zip"
            val entryName = if (isWin) "xray.exe" else "xray"
            val zip = downloadTo(
                "https://github.com/XTLS/Xray-core/releases/latest/download/$zipName",
                File(bin, "xray.zip"),
            )
            progress("Распаковка Xray…")
            ZipFile(zip).use { zf ->
                zf.getEntry(entryName)?.let { entry ->
                    zf.getInputStream(entry).use { input ->
                        val out = File(bin, p.binName(XRAY))
                        val tmp = File(bin, "${p.binName(XRAY)}.tmp")
                        tmp.outputStream().use { input.copyTo(it) }
                        if (!tmp.renameTo(out)) out.writeBytes(tmp.readBytes())
                        out.setExecutable(true)
                    }
                }
            }
            zip.delete()
        }
        if (needTun2socks && !status().tun2socksReady && p.supportsTun) {
            progress("Загрузка tun2socks…")
            // xjasonlyu/tun2socks ships a zip with the binary inside
            // (the old .gz asset is gone since v2.6+ — downloading it 404s).
            val t2sArch = if (arch == "64") "amd64" else "arm64"
            if (isWin) {
                val entry = "tun2socks-windows-$t2sArch.exe"
                val zip = downloadTo(
                    "https://github.com/xjasonlyu/tun2socks/releases/latest/download/" +
                        "tun2socks-windows-$t2sArch.zip",
                    File(bin, "tun2socks.zip"),
                )
                progress("Распаковка tun2socks…")
                var extracted = false
                ZipFile(zip).use { zf ->
                    zf.getEntry(entry)?.let { entry2 ->
                        zf.getInputStream(entry2).use { input ->
                            val out = File(bin, p.binName(TUN2SOCKS))
                            val tmp = File(bin, "${p.binName(TUN2SOCKS)}.tmp")
                            tmp.outputStream().use { input.copyTo(it) }
                            if (!tmp.renameTo(out)) out.writeBytes(tmp.readBytes())
                            out.setExecutable(true)
                            extracted = true
                        }
                    }
                }
                zip.delete()
                if (!extracted) {
                    throw RuntimeException("В архиве tun2socks нет бинарника $entry")
                }
                // wintun.dll нужен tun2socks в той же папке; он входит в MSI
                // AmneziaWG (ниже/уже распакован). Если dll ещё нет — качаем MSI.
                if (!wintunFile().exists()) {
                    progress("Распаковка wintun.dll (из MSI AmneziaWG)…")
                    extractAmneziaWgMsi(bin)
                }
            } else {
                val zip = downloadTo(
                    "https://github.com/xjasonlyu/tun2socks/releases/latest/download/tun2socks-linux-$t2sArch.zip",
                    File(bin, "tun2socks.zip"),
                )
                progress("Распаковка tun2socks…")
                var extracted = false
                ZipFile(zip).use { zf ->
                    zf.getEntry("tun2socks-linux-$t2sArch")?.let { entry ->
                        zf.getInputStream(entry).use { input ->
                            val out = File(bin, TUN2SOCKS)
                            val tmp = File(bin, "$TUN2SOCKS.tmp")
                            tmp.outputStream().use { input.copyTo(it) }
                            if (!tmp.renameTo(out)) out.writeBytes(tmp.readBytes())
                            out.setExecutable(true)
                            extracted = true
                        }
                    }
                }
                if (!extracted) {
                    throw RuntimeException("В архиве tun2socks нет бинарника tun2socks-linux-$t2sArch")
                }
                zip.delete()
            }
        }
        if (isWin) {
            // Windows: WireGuard/AWG — через amneziawg.exe (tunnel service),
            // wintun.dll — общий для всех движков, OpenVPN — портативный из MSI.
            if ((needAwg && !status().awgReady) || !wintunFile().exists()) {
                progress("Распаковка AmneziaWG (WireGuard/AmneziaWG + wintun)…")
                extractAmneziaWgMsi(bin)
            }
            if (needOpenVpn && !status().openVpnReady) {
                progress("Распаковка OpenVPN…")
                extractOpenVpnMsi(bin)
            }
        }
        if (!isWin && needAwg && !executable(File(bin, AWG_GO)) && p.supportsDaemons) {
            progress("Загрузка amneziawg-go…")
            val goArch = if (arch == "64") "amd64" else "arm64"
            val dst = File(bin, AWG_GO)
            val tmp = File(bin, "$AWG_GO.tmp")
            downloadTo("$AWG_GO_RELEASE/amneziawg-go-linux-$goArch", tmp)
            if (!tmp.renameTo(dst)) dst.writeBytes(tmp.readBytes())
            dst.setExecutable(true)
        }
        if (!isWin && needAwg && !executable(File(bin, AWG))) {
            progress("Загрузка утилиты awg…")
            val zip = downloadTo(AWG_TOOLS_ZIP, File(bin, "amneziawg-tools.zip"))
            var extracted = false
            ZipFile(zip).use { zf ->
                zf.getEntry("ubuntu-22.04-amneziawg-tools/awg")?.let { entry ->
                    zf.getInputStream(entry).use { input ->
                        val dst = File(bin, AWG)
                        val tmp = File(bin, "$AWG.tmp")
                        tmp.outputStream().use { input.copyTo(it) }
                        if (!tmp.renameTo(dst)) dst.writeBytes(tmp.readBytes())
                        dst.setExecutable(true)
                        extracted = true
                    }
                }
            }
            zip.delete()
            if (!extracted) throw RuntimeException("В архиве amneziawg-tools нет утилиты awg")
        }
        if (needGeosite && !status().geositeReady) {
            progress("Загрузка базы рекламных доменов…")
            val dst = File(bin, "geosite.dat")
            val tmp = File(bin, "geosite.dat.tmp")
            downloadTo(
                "https://github.com/v2fly/domain-list-community/releases/latest/download/dlc.dat",
                tmp,
            )
            if (!tmp.renameTo(dst)) dst.writeBytes(tmp.readBytes())
        }
        progress("Готово")
    }

    /**
     * `msiexec /a <msi> /qn TARGETDIR=<dir>` — administrative install point:
     * распаковывает MSI в каталог без установки. Так достаются портативные
     * amneziawg.exe/awg.exe/wintun.dll и openvpn.exe с его DLL.
     */
    private fun extractMsi(msi: File, target: File) {
        target.mkdirs()
        val proc = ProcessBuilder(
            "msiexec", "/a", msi.absolutePath, "/qn", "TARGETDIR=${target.absolutePath}",
        ).apply { redirectErrorStream(true) }.start()
        val out = proc.inputStream.readBytes().toString(Charsets.UTF_8).trim()
        val code = proc.waitFor()
        if (code != 0) {
            throw RuntimeException(
                "Не удалось распаковать ${msi.name} (msiexec /a exit $code): ${out.take(200)}",
            )
        }
    }

    /** Скачивает и распаковывает MSI AmneziaWG → amneziawg.exe, awg.exe, wintun.dll. */
    private fun extractAmneziaWgMsi(bin: File) {
        val arch = upstreamArch()
        val msi = downloadTo(
            "$AWG_WIN_RELEASE/amneziawg-$arch-3.1.0.msi",
            File(bin, "amneziawg.msi"),
        )
        val extract = File(bin, ".msi-awg")
        extract.deleteRecursively()
        try {
            extractMsi(msi, extract)
            val src = File(extract, "AmneziaWG")
            val awgExe = File(src, "${AWG_SERVICE}.exe")
            val awgUtil = File(src, "${AWG}.exe")
            val wintun = File(src, "wintun.dll")
            if (!awgExe.exists() || !awgUtil.exists() || !wintun.exists()) {
                throw RuntimeException("В MSI AmneziaWG нет ожидаемых файлов (amneziawg/awg/wintun)")
            }
            listOf(awgExe to File(bin, "${AWG_SERVICE}.exe"), awgUtil to File(bin, "${AWG}.exe"))
                .forEach { (from, to) ->
                    from.copyTo(to, overwrite = true)
                    to.setExecutable(true)
                }
            wintun.copyTo(wintunFile(), overwrite = true)
        } finally {
            msi.delete()
            extract.deleteRecursively()
        }
    }

    /** Скачивает и распаковывает MSI OpenVPN → bin/openvpn (портативный). */
    private fun extractOpenVpnMsi(bin: File) {
        val arch = upstreamArch()
        val msi = downloadTo(
            OPENVPN_MSI_URL.format(arch),
            File(bin, "openvpn.msi"),
        )
        val extract = File(bin, ".msi-openvpn")
        extract.deleteRecursively()
        try {
            extractMsi(msi, extract)
            val src = File(extract, "OpenVPN")
            val exe = File(src, "bin/openvpn${p.exeSuffix}")
            if (!exe.exists()) {
                throw RuntimeException("В MSI OpenVPN нет bin/openvpn.exe")
            }
            val dst = File(bin, "openvpn")
            dst.deleteRecursively()
            // bin/ (openvpn.exe + OpenSSL DLL) и ssl/modules (legacy.dll) —
            // всё необходимое; easy-rsa и gui не нужны.
            listOf("bin", "ssl").forEach { sub ->
                val from = File(src, sub)
                if (from.exists()) from.copyRecursively(File(dst, sub), overwrite = true)
            }
            exe.setExecutable(true)
        } finally {
            msi.delete()
            extract.deleteRecursively()
        }
    }

    private fun downloadTo(url: String, dst: File): File {
        val conn = (URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                dst.delete()
                throw RuntimeException("Не удалось скачать $url: HTTP $code")
            }
            conn.inputStream.use { input ->
                dst.outputStream().use { input.copyTo(it) }
            }
        } catch (t: Throwable) {
            dst.delete()
            throw RuntimeException("Не удалось скачать $url: ${t.message}", t)
        } finally {
            conn.disconnect()
        }
        if (dst.length() == 0L) {
            dst.delete()
            throw RuntimeException("Пустой файл загрузки: $url")
        }
        return dst
    }
}
