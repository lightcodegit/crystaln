package com.crystaln.vpn.desktop

import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.crystaln.vpn.desktop.net.TunnelManager
import com.crystaln.vpn.desktop.ui.CrystalnApp
import java.io.InputStream

/** Loads a bundled image resource into a painter; null when missing. */
fun resourcePainter(name: String): Painter? = try {
    val stream: InputStream? = TunnelManager::class.java.classLoader.getResourceAsStream(name)
    stream?.use { BitmapPainter(loadImageBitmap(it)) }
} catch (_: Throwable) {
    null
}

private val windowIcon: Painter? by lazy { resourcePainter("crystaln/icon.png") }

fun main() {
    // Children (xray, tun2socks, openvpn watchers) must not outlive the app:
    // a normal exit and a crash both run this hook and tear everything down.
    Runtime.getRuntime().addShutdownHook(Thread { TunnelManager.shutdownAll() })

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "CrystalN",
            icon = windowIcon,
            state = rememberWindowState(size = DpSize(1080.dp, 840.dp)),
        ) {
            CrystalnApp()
        }
    }
}
