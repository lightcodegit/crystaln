package com.crystaln.vpn.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.crystaln.vpn.desktop.net.TunnelManager
import com.crystaln.vpn.desktop.ui.CrystalnApp
import java.awt.SystemTray
import java.io.InputStream
import kotlin.system.exitProcess

/** Loads a bundled image resource into a painter; null when missing. */
fun resourcePainter(name: String): Painter? = try {
    val stream: InputStream? = TunnelManager::class.java.classLoader.getResourceAsStream(name)
    stream?.use { BitmapPainter(loadImageBitmap(it)) }
} catch (_: Throwable) {
    null
}

private val windowIcon: Painter? by lazy { resourcePainter("crystaln/icon.png") }

/** Tray support: Windows only (Wayland/GNOME sessions are unreliable anyway). */
private val traySupported: Boolean by lazy {
    try {
        com.crystaln.vpn.desktop.Platform.isWindows &&
            windowIcon != null &&
            java.awt.SystemTray.isSupported()
    } catch (_: Throwable) {
        false
    }
}

fun main() {
    // Children (xray, tun2socks, openvpn watchers) must not outlive the app:
    // a normal exit and a crash both run this hook and tear everything down.
    Runtime.getRuntime().addShutdownHook(Thread { TunnelManager.shutdownAll() })

    application {
        // На Windows закрытие окна сворачивает приложение в трей (туннель
        // продолжает работать); без трея — обычный выход.
        var windowVisible by remember { mutableStateOf(true) }

        Window(
            onCloseRequest = {
                if (traySupported) windowVisible = false else exitApplication()
            },
            visible = windowVisible,
            title = "CrystalN",
            icon = windowIcon,
            state = rememberWindowState(size = DpSize(1080.dp, 840.dp)),
        ) {
            CrystalnApp()
        }

        val icon = windowIcon
        if (traySupported && icon != null) {
            Tray(
                icon = icon,
                tooltip = "CrystalN — VPN",
                // Двойной клик по иконке в трее (Windows) открывает окно.
                onAction = { windowVisible = true },
            ) {
                Item("Открыть CrystalN") { windowVisible = true }
                Item("Выход") {
                    // Полный выход: shutdown-хук погасит xray/tun2socks/openvpn.
                    exitProcess(0)
                }
            }
        }
    }
}
