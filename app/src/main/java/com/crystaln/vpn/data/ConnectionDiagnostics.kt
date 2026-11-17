package com.crystaln.vpn.data

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Connection diagnostics: answers "why doesn't it connect?" without starting
 * a VPN. Runs three kinds of checks — DNS, TCP reachability and protocol
 * config validation — and never touches the tunnel itself, so it is safe to
 * use while another profile is connected or when nothing is configured yet.
 */
object ConnectionDiagnostics {

    enum class Kind { DNS, TCP, ICMP, CONFIG }

    data class Result(
        val kind: Kind,
        val ok: Boolean,
        /** Check duration in ms; -1 for instant static checks. */
        val ms: Long,
        /** Human-readable technical detail (resolved IPs, error text, ...). */
        val detail: String,
    )

    private const val DNS_TIMEOUT_MS = 8_000
    private const val TCP_TIMEOUT_MS = 6_000
    private const val ICMP_TIMEOUT_MS = 5_000

    /** Runs every check for [profile]; network parts must run off the UI thread. */
    fun run(profile: Profile): List<Result> {
        val results = mutableListOf<Result>()
        results += staticChecks(profile)
        if (profile.address.isNotBlank() && profile.port in 1..65535) {
            results += dnsCheck(profile.address)
            // TCP only makes sense when the name resolved — and only for
            // TCP-based protocols. WireGuard/AWG (and OpenVPN in udp mode)
            // never accept TCP connections on the tunnel port: probing it
            // would always report a false failure, so ping (ICMP) is used.
            val udpOnly = when (profile.protocol) {
                Protocol.WIREGUARD, Protocol.AMNEZIAWG -> true
                Protocol.OPENVPN -> !profile.ovpnConfig.contains("proto tcp", ignoreCase = true)
                else -> false
            }
            if (results.last().ok) {
                results += if (udpOnly) {
                    icmpCheck(profile.address)
                } else {
                    tcpCheck(profile.address, profile.port)
                }
            }
        }
        return results
    }

    /** Protocol-agnostic endpoint checks. Network calls, blocking. */
    fun dnsCheck(host: String): Result = try {
        val started = System.nanoTime()
        val ips = InetAddress.getAllByName(host)
        val ms = (System.nanoTime() - started) / 1_000_000
        Result(
            kind = Kind.DNS,
            ok = ips.isNotEmpty(),
            ms = ms,
            detail = ips.take(3).joinToString(", ") { it.hostAddress ?: "?" },
        )
    } catch (t: Throwable) {
        Result(Kind.DNS, ok = false, ms = -1, detail = t.message ?: t.javaClass.simpleName)
    }

    fun tcpCheck(host: String, port: Int): Result = try {
        val started = System.nanoTime()
        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), TCP_TIMEOUT_MS)
        }
        val ms = (System.nanoTime() - started) / 1_000_000
        Result(Kind.TCP, ok = true, ms = ms, detail = "")
    } catch (t: Throwable) {
        Result(Kind.TCP, ok = false, ms = -1, detail = t.message ?: t.javaClass.simpleName)
    }


    /**
     * UDP-based protocols (WireGuard/AWG, OpenVPN/udp) never accept TCP on
     * the tunnel port, so reachability is checked with an ICMP ping instead.
     * A failed ping is not proof of outage — some hosts drop ICMP — the
     * detail line says so explicitly.
     */
    fun icmpCheck(host: String): Result = try {
        val started = System.nanoTime()
        val reachable = InetAddress.getByName(host).isReachable(ICMP_TIMEOUT_MS)
        val ms = (System.nanoTime() - started) / 1_000_000
        Result(
            kind = Kind.ICMP,
            ok = reachable,
            ms = if (reachable) ms else -1,
            detail = if (reachable) {
                "UDP-протокол: TCP-проба не применяется, доступность подтверждена ping'ом"
            } else {
                "ping (ICMP) не проходит — сервер может запрещать ICMP, VPN по UDP всё равно может работать"
            },
        )
    } catch (t: Throwable) {
        Result(Kind.ICMP, ok = false, ms = -1, detail = t.message ?: t.javaClass.simpleName)
    }

    /**
     * Config validation, purely offline and deterministic — the part covered
     * by unit tests. Catches "imported but incomplete" profiles before the
     * user burns a connection attempt.
     */
    fun staticChecks(profile: Profile): List<Result> {
        val problems = mutableListOf<String>()
        if (profile.address.isBlank()) problems += "address"
        if (profile.port !in 1..65535) problems += "port"

        when (profile.protocol) {
            Protocol.VMESS, Protocol.VLESS -> {
                if (profile.fields["uuid"].isNullOrBlank()) problems += "uuid"
                if (profile.fields["reality"] == "true") {
                    if (profile.fields["publicKey"].isNullOrBlank()) problems += "reality publicKey"
                    if (profile.fields["shortId"].isNullOrBlank()) problems += "reality shortId"
                }
            }

            Protocol.TROJAN -> if (profile.fields["password"].isNullOrBlank()) problems += "password"

            Protocol.SHADOWSOCKS -> {
                if (profile.fields["password"].isNullOrBlank()) problems += "password"
                if (profile.fields["method"].isNullOrBlank()) problems += "method"
            }

            Protocol.SOCKS, Protocol.HTTP, Protocol.HTTPS -> Unit // credentials optional

            Protocol.WIREGUARD, Protocol.AMNEZIAWG -> {
                val cfg = profile.wgConfig
                if (!cfg.contains("PrivateKey", ignoreCase = true)) problems += "PrivateKey"
                if (!cfg.contains("PublicKey", ignoreCase = true)) problems += "peer PublicKey"
                if (!cfg.contains("Endpoint", ignoreCase = true)) problems += "Endpoint"
            }

            Protocol.OPENVPN -> {
                val cfg = profile.ovpnConfig
                if (!cfg.contains("remote ", ignoreCase = true)) problems += "remote"
                if (!cfg.contains("<ca>", ignoreCase = true) &&
                    !cfg.contains("ca ", ignoreCase = true)
                ) {
                    problems += "ca"
                }
                if (cfg.contains("auth-user-pass", ignoreCase = true) &&
                    profile.fields["username"].isNullOrBlank() &&
                    !cfg.contains("auth-user-pass user", ignoreCase = true)
                ) {
                    // Credentials may be entered in the editor; only flag a
                    // hard contradiction (inline credentials absent) softly.
                }
            }
        }

        val ok = problems.isEmpty()
        return listOf(
            Result(
                kind = Kind.CONFIG,
                ok = ok,
                ms = -1,
                detail = if (ok) "" else problems.joinToString(", "),
            ),
        )
    }
}
