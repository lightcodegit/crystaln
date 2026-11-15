package com.crystaln.vpn.desktop.net

import com.crystaln.vpn.desktop.AppDirs
import java.io.File
import java.net.URL
import java.util.zip.ZipFile

/**
 * External binaries needed by the desktop app: the official Xray-core build,
 * tun2socks (TUN mode on top of the local socks proxy) and the geosite
 * database for ad blocking. Everything is downloaded once into
 * ~/.local/share/crystaln/bin; connect() refuses to run without them.
 */
object Binaries {

    const val XRAY = "xray"
    const val TUN2SOCKS = "tun2socks"

    data class Status(
        val xrayReady: Boolean,
        val tun2socksReady: Boolean,
        val geositeReady: Boolean,
    ) {
        val proxyModeReady: Boolean get() = xrayReady
        val tunModeReady: Boolean get() = xrayReady && tun2socksReady
    }

    fun status(): Status {
        val bin = AppDirs.binDir()
        return Status(
            xrayReady = File(bin, XRAY).let { it.exists() && it.canExecute() },
            tun2socksReady = File(bin, TUN2SOCKS).let { it.exists() && it.canExecute() },
            geositeReady = File(bin, "geosite.dat").exists(),
        )
    }

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
            // xjasonlyu/tun2socks ships a gzipped binary.
            val gz = downloadTo(
                "https://github.com/xjasonlyu/tun2socks/releases/latest/download/tun2socks-linux-${
                    if (arch == "64") "amd64" else "arm64"
                }.gz",
                File(bin, "tun2socks.gz"),
            )
            progress("Распаковка tun2socks…")
            val out = File(bin, TUN2SOCKS)
            val tmp = File(bin, "$TUN2SOCKS.tmp")
            ProcessBuilder("gunzip", "-c", gz.absolutePath)
                .redirectOutput(tmp)
                .start()
                .waitFor()
            if (!tmp.renameTo(out)) out.writeBytes(tmp.readBytes())
            out.setExecutable(true)
            gz.delete()
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
            conn.inputStream.use { input ->
                dst.outputStream().use { input.copyTo(it) }
            }
        } catch (t: Throwable) {
            dst.delete()
            throw RuntimeException("Не удалось скачать ${url.substringBeforeLast('/')}: ${t.message}", t)
        } finally {
            conn.disconnect()
        }
        if (dst.length() == 0L) {
            dst.delete()
            throw RuntimeException("Пустой файл загрузки")
        }
        return dst
    }
}
