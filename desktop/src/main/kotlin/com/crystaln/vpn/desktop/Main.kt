package com.crystaln.vpn.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
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

/**
 * Status icons for the tray: the base icon with a colored dot in the
 * bottom-right corner — gray when off, orange while starting/stopping,
 * green when the VPN is up. Computed once from the bundled icon.
 */
private val statusIcons: Map<TunnelManager.State, Painter?> by lazy {
    try {
        val base = baseBitmap() ?: return@lazy emptyMap()
        mapOf(
            TunnelManager.State.OFF to dotPainter(base, Color(0xFF98A2AE)),
            TunnelManager.State.STARTING to dotPainter(base, Color(0xFFFFB020)),
            TunnelManager.State.STOPPING to dotPainter(base, Color(0xFFFFB020)),
            TunnelManager.State.CONNECTED to dotPainter(base, Color(0xFF35D07F)),
        )
    } catch (_: Throwable) {
        emptyMap()
    }
}

/** The bundled icon as a raw bitmap (for compositing status dots). */
private fun baseBitmap(): ImageBitmap? = try {
    val stream = TunnelManager::class.java.classLoader
        .getResourceAsStream("crystaln/icon.png")
    stream?.use { loadImageBitmap(it) }
} catch (_: Throwable) {
    null
}

/** Base icon with a colored status dot (white ring for contrast). */
private fun dotPainter(base: ImageBitmap, dot: Color): Painter {
    val w = base.width
    val h = base.height
    val bmp = ImageBitmap(w, h)
    val canvas = Canvas(bmp)
    canvas.drawImage(base, topLeftOffset = Offset.Zero, paint = Paint())
    val radius = minOf(w, h) * 0.19f
    val cx = w - radius * 1.45f
    val cy = h - radius * 1.45f
    val paint = Paint()
    paint.color = Color(0xFFFFFFFF)
    canvas.drawCircle(Offset(cx, cy), radius * 1.18f, paint)
    paint.color = dot
    canvas.drawCircle(Offset(cx, cy), radius, paint)
    return BitmapPainter(bmp)
}

/** Tray icon matching the current tunnel state (falls back to base icon). */
private fun iconFor(state: TunnelManager.State): Painter? {
    val icons = statusIcons
    return icons[state] ?: windowIcon
}

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

/** Profile to connect to from the tray: the selected one, or the first saved. */
private fun trayProfile(): com.crystaln.vpn.data.Profile? {
    val cfg = AppDirs.configDir()
    return com.crystaln.vpn.data.ProfileStore.selected(cfg)
        ?: com.crystaln.vpn.data.ProfileStore.load(cfg).firstOrNull()
}

fun main() {
    // Children (xray, tun2socks, openvpn watchers) must not outlive the app:
    // a normal exit and a crash both run this hook and tear everything down.
    Runtime.getRuntime().addShutdownHook(Thread { TunnelManager.shutdownAll() })

    application {
        // На Windows закрытие окна сворачивает приложение в трей (туннель
        // продолжает работать); без трея — обычный выход.
        var windowVisible by remember { mutableStateOf(true) }

        // Состояние туннеля для меню трея (Подключить/Отключить, подсказка).
        val conn by TunnelManager.ui.collectAsState()

        // Windows-балун уведомления при смене состояния туннеля: подключено /
        // отключено / ошибка подключения. Показываются, только пока жив трей.
        val trayState = rememberTrayState()
        var lastTrayState by remember { mutableStateOf<TunnelManager.State?>(null) }
        LaunchedEffect(conn) {
            val prev = lastTrayState
            if (traySupported && prev != null && prev != conn.state) {
                when (conn.state) {
                    TunnelManager.State.CONNECTED ->
                        trayState.sendNotification(
                            Notification(
                                "CrystalN — VPN",
                                "Подключено: ${conn.profile?.name ?: "профиль"}",
                                Notification.Type.Info,
                            ),
                        )
                    TunnelManager.State.OFF ->
                        if (prev == TunnelManager.State.CONNECTED ||
                            prev == TunnelManager.State.STARTING
                        ) {
                            val err = conn.error
                            if (err != null) {
                                trayState.sendNotification(
                                    Notification(
                                        "CrystalN — VPN",
                                        "Ошибка подключения: $err",
                                        Notification.Type.Error,
                                    ),
                                )
                            } else {
                                trayState.sendNotification(
                                    Notification(
                                        "CrystalN — VPN",
                                        "Отключено",
                                        Notification.Type.Info,
                                    ),
                                )
                            }
                        }
                    else -> {}
                }
            }
            lastTrayState = conn.state
        }

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
            val tooltip = when (val c = conn.state) {
                TunnelManager.State.CONNECTED ->
                    "CrystalN — подключено (${conn.profile?.name ?: "профиль"})"
                TunnelManager.State.STARTING -> "CrystalN — подключение…"
                TunnelManager.State.STOPPING -> "CrystalN — отключение…"
                else -> "CrystalN — отключено"
            }
            Tray(
                icon = iconFor(conn.state) ?: icon,
                state = trayState,
                tooltip = tooltip,
                // Двойной клик по иконке в трее (Windows) открывает окно.
                onAction = { windowVisible = true },
            ) {
                Item(tooltip, enabled = false) { /* только статус */ }
                Separator()
                if (conn.state == TunnelManager.State.OFF ||
                    conn.state == TunnelManager.State.STOPPING
                ) {
                    Item("Подключить") {
                        val p = trayProfile()
                        if (p != null) TunnelManager.connect(p)
                    }
                }
                if (conn.state == TunnelManager.State.STARTING ||
                    conn.state == TunnelManager.State.CONNECTED
                ) {
                    Item("Отключить") { TunnelManager.stop() }
                }
                Separator()
                Item("Открыть CrystalN") { windowVisible = true }
                Item("Выход") {
                    // Полный выход: shutdown-хук погасит xray/tun2socks/openvpn.
                    exitProcess(0)
                }
            }
        }
    }
}
