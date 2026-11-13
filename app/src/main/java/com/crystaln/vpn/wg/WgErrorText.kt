package com.crystaln.vpn.wg

/**
 * Maps exceptions from the AmneziaWG backend to readable user-facing messages.
 */
object WgErrorText {

    fun of(t: Throwable): String = when (t) {
        is org.amnezia.awg.backend.BackendException -> when (t.reason) {
            org.amnezia.awg.backend.BackendException.Reason.VPN_NOT_AUTHORIZED ->
                "Нет разрешения на VPN"
            org.amnezia.awg.backend.BackendException.Reason.DNS_RESOLUTION_FAILURE ->
                "Не удалось определить адрес сервера (DNS): " +
                    (t.format?.firstOrNull()?.toString() ?: "")
            org.amnezia.awg.backend.BackendException.Reason.UNABLE_TO_START_VPN ->
                "Не удалось запустить VpnService"
            org.amnezia.awg.backend.BackendException.Reason.TUN_CREATION_ERROR ->
                "Ошибка создания TUN-интерфейса"
            org.amnezia.awg.backend.BackendException.Reason.TUNNEL_MISSING_CONFIG ->
                "Пустая конфигурация туннеля"
            else -> t.message ?: t.javaClass.simpleName
        }

        is org.amnezia.awg.config.BadConfigException ->
            "Ошибка в конфигурации: ${t.message ?: t.javaClass.simpleName}"

        else -> t.message ?: t.javaClass.simpleName
    }
}
