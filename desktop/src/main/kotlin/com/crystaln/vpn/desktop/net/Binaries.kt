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

    private const val AWG_GO_RELEASE =
        "https://github.com/lightcodegit/crystaln-binaries/releases/download/awg-go-bin"
    private const val AWG_TOOLS_ZIP =
        "https://github.com/amnezia-vpn/amneziawg-tools/releases/download/" +
            "v3.1.20260812/ubuntu-22.04-amneziawg-tools.zip"

    data class Status(
        val xrayReady: Boolean,
        val tun2socksReady: Boolean,
        val geositeReady: Boolean,
        val awgReady: Boolean,
    ) {
        val proxyModeReady: Boolean get() = xrayReady
        val tunModeReady: Boolean get() = xrayReady && tun2socksReady
    }

    fun status(): Status {
        val bin = AppDirs.binDir()
        return Status(
            xrayReady = executable(File(bin, XRAY)),
            tun2socksReady = executable(File(bin, TUN2SOCKS)),
            geositeReady = File(bin, "geosite.dat").exists(),
            awgReady = executable(File(bin, AWG_GO)) && executable(File(bin, AWG)),
        )
    }

    private fun executable(file: File) = file.exists() && file.canExecute()

    private fun archSuffix(): String {
        val a = System.getProperty("os.arch") ?: "amd64"
        return when {
            a.contains("aarch64") || a.contains("arm64") -> "arm64-v8a"
            a.contains("x86_64") || a.contains("amd64") -> "64"
            else -> throw IllegalStateException("Unsupported CPU: $a")
        }
    }

    /**
     * Downloads and unpacks the missing pieces. Called from a background
     * dispatcher; [progress] receives human-readable steps.
     */
    fun downloadAll(
        needXray: Boolean = true,
        needTun2socks: Boolean = true,
        needGeosite: Boolean = false,
        needAwg: Boolean = true,
        progress: (String) -> Unit = {},
    ) {
        val bin = AppDirs.binDir()
        bin.mkdirs()
        val arch = archSuffix()
        if (needXray && !status().xrayReady) {
            progress("Загрузка Xray-core…")
            val zip = downloadTo(
                "https://github.com/XTLS/Xray-core/releases/latest/download/Xray-linux-$arch.zip",
                File(bin, "xray.zip"),
            )
            progress("Распаковка Xray…")
            ZipFile(zip).use { zf ->
                zf.getEntry("xray")?.let { entry ->
                    zf.getInputStream(entry).use { input ->
                        val out = File(bin, XRAY)
                        val tmp = File(bin, "$XRAY.tmp")
                        tmp.outputStream().use { input.copyTo(it) }
                        if (!tmp.renameTo(out)) out.writeBytes(tmp.readBytes())
                        out.setExecutable(true)
                    }
                }
            }
            zip.delete()
        }
        if (needTun2socks && !status().tun2socksReady) {
            progress("Загрузка tun2socks…")
            // xjasonlyu/tun2socks ships a zip with the binary inside
            // (the old .gz asset is gone since v2.6+ — downloading it 404s).
            val t2sArch = if (arch == "64") "amd64" else "arm64"
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
        if (needAwg && !executable(File(bin, AWG_GO))) {
            progress("Загрузка amneziawg-go…")
            val goArch = if (arch == "64") "amd64" else "arm64"
            val dst = File(bin, AWG_GO)
            val tmp = File(bin, "$AWG_GO.tmp")
            downloadTo("$AWG_GO_RELEASE/amneziawg-go-linux-$goArch", tmp)
            if (!tmp.renameTo(dst)) dst.writeBytes(tmp.readBytes())
            dst.setExecutable(true)
        }
        if (needAwg && !executable(File(bin, AWG))) {
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
