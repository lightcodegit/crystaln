package com.crystaln.vpn.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.crystaln.vpn.desktop.net.TunnelManager
import com.crystaln.vpn.desktop.ui.CrystalnApp

fun main() {
    // Children (xray, tun2socks, openvpn watchers) must not outlive the app:
    // a normal exit and a crash both run this hook and tear everything down.
    Runtime.getRuntime().addShutdownHook(Thread { TunnelManager.shutdownAll() })

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "CrystalN",
            state = rememberWindowState(size = DpSize(1080.dp, 840.dp)),
        ) {
            CrystalnApp()
        }
    }
}
