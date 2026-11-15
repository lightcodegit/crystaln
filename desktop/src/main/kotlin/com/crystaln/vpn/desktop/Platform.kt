package com.crystaln.vpn.desktop

/**
 * Платформа, под которой запущено приложение.
 *
 * Большая часть privileged-кода (TUN, root-хелпер, `ip`/`resolvectl`) написана
 * под Linux. На Windows/macOS приложение собирается и работает в прокси-режиме
 * (локальный SOCKS+HTTP через xray), а TUN-режим явно сообщает, что недоступен,
 * вместо того чтобы падать с непонятной ошибкой.
 */
object Platform {

    val isWindows: Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("win")

    val isMac: Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("mac")

    val isLinux: Boolean = !isWindows && !isMac

    /** Суффикс исполняемых файлов: `.exe` на Windows. */
    val exeSuffix: String = if (isWindows) ".exe" else ""

    /** Поддерживается ли TUN-режим (полный VPN) на этой платформе. */
    val supportsTun: Boolean get() = isLinux

    /** Поддерживаются ли WireGuard/AmneziaWG/OpenVPN через ядра-демоны. */
    val supportsDaemons: Boolean get() = isLinux

    /** Полное имя бинарника с учётом платформы. */
    fun binName(base: String): String = base + exeSuffix

    /** Человекочитаемое название для сообщений об ошибках. */
    val displayName: String
        get() = when {
            isWindows -> "Windows"
            isMac -> "macOS"
            else -> "Linux"
        }
}
