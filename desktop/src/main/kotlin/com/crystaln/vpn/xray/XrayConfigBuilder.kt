package com.crystaln.vpn.xray

import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.data.Protocol
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds a full Xray-core config with a TUN inbound for [Profile]s of the
 * Xray family (VMess, VLESS, Trojan, Shadowsocks, SOCKS, HTTP).
 *
 * The TUN inbound is fed the VPN interface fd by the service; sniffing is on so
 * domain routing works even when clients connect by IP.
 */
object XrayConfigBuilder {

    const val TUN_MTU = 8500

    /** Options that tune the config without changing the server. */
    data class Options(
        /** Intercept port 53 in the TUN and resolve DNS through the tunnel. */
        val dnsViaTunnel: Boolean = false,
        /** Upstream DNS server used when [dnsViaTunnel] is on. */
        val dnsServer: String = "1.1.1.1",
        /** Drop ad/tracker domains (geosite:category-ads-all → block). */
        val blockAds: Boolean = false,
    )

    fun build(profile: Profile): String = build(profile, Options())

    fun build(profile: Profile, options: Options): String {
        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))
        root.put("inbounds", JSONArray().put(tunInbound()))
        root.put("outbounds", JSONArray().apply {
            put(buildOutbound(profile))
            put(directOutbound())
            put(blockOutbound())
            if (options.dnsViaTunnel) put(dnsOutbound(options.dnsServer))
        })
        root.put("routing", routing(options))
        return root.toString()
    }

    /**
     * Desktop proxy-mode config: local SOCKS/HTTP inbounds instead of the TUN.
     * The desktop xray-core binary has no `tun` inbound (that inbound is an
     * AndroidLibXrayLite addition), so TUN mode is built on top of this same
     * local proxy by tun2socks (see XrayRunner).
     */
    fun buildProxy(
        profile: Profile,
        options: Options,
        socksPort: Int,
        httpPort: Int,
    ): String {
        val root = JSONObject()
        root.put("log", JSONObject().put("loglevel", "warning"))
        root.put("inbounds", JSONArray().apply {
            put(
                JSONObject().apply {
                    put("tag", "socks-in")
                    put("listen", "127.0.0.1")
                    put("port", socksPort)
                    put("protocol", "socks")
                    put("settings", JSONObject().put("udp", true))
                },
            )
            if (httpPort > 0) {
                put(
                    JSONObject().apply {
                        put("tag", "http-in")
                        put("listen", "127.0.0.1")
                        put("port", httpPort)
                        put("protocol", "http")
                        put("settings", JSONObject())
                    },
                )
            }
        })
        root.put("outbounds", JSONArray().apply {
            put(buildOutbound(profile))
            put(directOutbound())
            put(blockOutbound())
            if (options.dnsViaTunnel) put(dnsOutbound(options.dnsServer))
        })
        root.put("routing", routing(options))
        return root.toString()
    }

    private fun tunInbound(): JSONObject = JSONObject().apply {
        put("tag", "tun")
        put("protocol", "tun")
        put(
            "settings",
            JSONObject().put("name", "xray0").put("MTU", TUN_MTU).put("userLevel", 8),
        )
        put(
            "sniffing",
            JSONObject().put("enabled", true)
                .put("destOverride", JSONArray().put("http").put("tls").put("quic")),
        )
    }

    private fun routing(options: Options): JSONObject = JSONObject().apply {
        put("domainStrategy", "IPIfNonMatch")
        put(
            "rules",
            JSONArray().apply {
                // Local/loopback/link-local traffic stays off the tunnel.
                put(
                    JSONObject().put(
                        "ip",
                        JSONArray()
                            .put("10.0.0.0/8").put("172.16.0.0/12").put("192.168.0.0/16")
                            .put("127.0.0.0/8").put("169.254.0.0/16").put("100.64.0.0/10")
                            .put("fe80::/10").put("fc00::/7"),
                    ).put("outboundTag", "direct"),
                )
                // Ads/tracker domains die before they leave the device.
                if (options.blockAds) {
                    put(
                        JSONObject()
                            .put("domain", JSONArray().put("geosite:category-ads-all"))
                            .put("outboundTag", "block"),
                    )
                }
                // Plain DNS (port 53) goes through the tunnel's dns-out instead
                // of leaking to the local network / ISP resolver. Must precede
                // the catch-all proxy rule.
                if (options.dnsViaTunnel) {
                    put(
                        JSONObject()
                            .put("port", "53")
                            .put("network", "udp,tcp")
                            .put("outboundTag", "dns-out"),
                    )
                }
            },
        )
    }

    /** DNS forwarded to [server] through the proxy outbound. */
    private fun dnsOutbound(server: String): JSONObject = JSONObject().apply {
        put("tag", "dns-out")
        put("protocol", "dns")
        put(
            "settings",
            JSONObject().apply {
                if (server.isNotEmpty()) put("address", server)
                put("port", 53)
            },
        )
    }

    private fun directOutbound(): JSONObject = JSONObject().apply {
        put("tag", "direct")
        put("protocol", "freedom")
        put("settings", JSONObject())
    }

    private fun blockOutbound(): JSONObject = JSONObject().apply {
        put("tag", "block")
        put("protocol", "blackhole")
        put("settings", JSONObject())
    }

    /**
     * Local SOCKS5 port of the Hysteria2 core (child process). For HYSTERIA2
     * profiles the xray outbound is a SOCKS proxy pointing at the hysteria
     * binary listening on this loopback port — hysteria speaks QUIC to the
     * real server.
     */
    const val HYSTERIA_SOCKS_PORT = 17901

    fun buildOutbound(profile: Profile): JSONObject = when {
        profile.isHysteria -> JSONObject().apply {
            put("tag", "proxy")
            put("protocol", "socks")
            put(
                "settings",
                JSONObject().put(
                    "servers",
                    JSONArray().put(
                        JSONObject()
                            .put("address", "127.0.0.1")
                            .put("port", HYSTERIA_SOCKS_PORT),
                    ),
                ),
            )
        }

        else -> JSONObject().apply {
            put("tag", "proxy")
            put("protocol", xrayProtocol(profile.protocol))
            put("settings", settingsFor(profile))
            val stream = streamSettings(profile)
            if (stream != null) put("streamSettings", stream)
        }
    }

    private fun xrayProtocol(p: Protocol): String = when (p) {
        Protocol.VMESS -> "vmess"
        Protocol.VLESS -> "vless"
        Protocol.TROJAN -> "trojan"
        Protocol.SHADOWSOCKS -> "shadowsocks"
        Protocol.SOCKS -> "socks"
        Protocol.HTTP, Protocol.HTTPS -> "http"
        Protocol.WIREGUARD, Protocol.AMNEZIAWG ->
            throw IllegalArgumentException("WireGuard-family is not routed through Xray")
        Protocol.OPENVPN ->
            throw IllegalArgumentException("OpenVPN is not routed through Xray")
        Protocol.HYSTERIA2 ->
            throw IllegalArgumentException("Hysteria2 is routed through its own SOCKS outbound")
        Protocol.TUIC ->
            throw IllegalArgumentException("TUIC requires the sing-box engine (planned)")
    }

    private fun settingsFor(p: Profile): JSONObject = when (p.protocol) {
        Protocol.VMESS -> JSONObject().put(
            "vnext",
            JSONArray().put(
                server(p).put(
                    "users",
                    JSONArray().put(
                        JSONObject()
                            .put("id", p.fields["uuid"] ?: "")
                            .put("alterId", (p.fields["alterId"] ?: "0").toIntOrNull() ?: 0)
                            .put("security", p.fields["security"] ?: "auto")
                            .put("level", 8),
                    ),
                ),
            ),
        )

        Protocol.VLESS -> JSONObject().put(
            "vnext",
            JSONArray().put(
                server(p).put(
                    "users",
                    JSONArray().put(
                        JSONObject().apply {
                            put("id", p.fields["uuid"] ?: "")
                            put("encryption", "none")
                            put("level", 8)
                            p.fields["flow"]?.takeIf { it.isNotEmpty() }?.let { put("flow", it) }
                        },
                    ),
                ),
            ),
        )

        Protocol.TROJAN -> JSONObject().put(
            "servers",
            JSONArray().put(
                server(p).apply {
                    put("password", p.fields["password"] ?: "")
                },
            ),
        )

        Protocol.SHADOWSOCKS -> JSONObject().put(
            "servers",
            JSONArray().put(
                server(p).apply {
                    put("method", p.fields["method"] ?: "aes-256-gcm")
                    put("password", p.fields["password"] ?: "")
                    put("uot", true)
                },
            ),
        )

        Protocol.SOCKS, Protocol.HTTP, Protocol.HTTPS -> JSONObject().put(
            "servers",
            JSONArray().put(
                server(p).apply {
                    val user = p.fields["username"]
                    val pass = p.fields["password"]
                    if (!user.isNullOrEmpty()) {
                        put(
                            "users",
                            JSONArray().put(
                                JSONObject().put("user", user).put("pass", pass ?: "").put("level", 8),
                            ),
                        )
                    }
                },
            ),
        )

        Protocol.WIREGUARD, Protocol.AMNEZIAWG ->
            throw IllegalArgumentException("WireGuard-family is not routed through Xray")

        Protocol.OPENVPN ->
            throw IllegalArgumentException("OpenVPN is not routed through Xray")

        Protocol.HYSTERIA2 ->
            throw IllegalArgumentException("Hysteria2 is routed through its own SOCKS outbound")
        Protocol.TUIC ->
            throw IllegalArgumentException("TUIC requires the sing-box engine (planned)")
    }

    private fun server(p: Profile): JSONObject =
        JSONObject().put("address", p.address).put("port", p.port)

    private fun streamSettings(p: Profile): JSONObject? {
        val network = p.fields["network"] ?: "tcp"
        val s = JSONObject().put("network", network)
        when (network) {
            "ws" -> s.put(
                "wsSettings",
                JSONObject().apply {
                    put("path", p.fields["path"] ?: "/")
                    p.fields["host"]?.takeIf { it.isNotEmpty() }?.let {
                        put("headers", JSONObject().put("Host", it))
                    }
                },
            )

            "grpc" -> s.put(
                "grpcSettings",
                JSONObject().apply {
                    put("serviceName", p.fields["serviceName"] ?: "")
                    put("multiMode", (p.fields["multiMode"] ?: "false") == "true")
                },
            )

            "httpupgrade" -> s.put(
                "httpupgradeSettings",
                JSONObject().apply {
                    put("path", p.fields["path"] ?: "/")
                    put("host", p.fields["host"] ?: "")
                },
            )

            "xhttp", "splithttp" -> s.put(
                "xhttpSettings",
                JSONObject().apply {
                    put("path", p.fields["path"] ?: "/")
                    put("host", p.fields["host"] ?: "")
                    p.fields["mode"]?.takeIf { it.isNotEmpty() }?.let { put("mode", it) }
                },
            )

            "kcp" -> s.put(
                "kcpSettings",
                JSONObject().apply {
                    put("mtu", 1350)
                    put("tti", 50)
                    put("uplinkCapacity", 12)
                    put("downlinkCapacity", 100)
                    put("congestion", false)
                    put("readBufferSize", 1)
                    put("writeBufferSize", 1)
                    put(
                        "header",
                        JSONObject().put("type", p.fields["headerType"] ?: "none"),
                    )
                    p.fields["kcpSeed"]?.takeIf { it.isNotEmpty() }?.let { put("seed", it) }
                },
            )

            "quic" -> s.put(
                "quicSettings",
                JSONObject().apply {
                    put("security", p.fields["quicSecurity"] ?: "none")
                    put("key", p.fields["quicKey"] ?: "")
                    put("header", JSONObject().put("type", p.fields["headerType"] ?: "none"))
                },
            )

            "http", "h2" -> s.put(
                "httpSettings",
                JSONObject().apply {
                    put("path", p.fields["path"] ?: "/")
                    val host = p.fields["host"]
                    if (!host.isNullOrEmpty()) {
                        put("host", JSONArray().put(host))
                    }
                },
            )

            else -> {} // tcp
        }

        val useTls = p.fields["tls"] == "true" || p.protocol == Protocol.HTTPS
        val useReality = p.fields["reality"] == "true"
        if (useTls || useReality) {
            s.put("security", if (useReality) "reality" else "tls")
            if (useReality) {
                s.put(
                    "realitySettings",
                    JSONObject().apply {
                        put("show", false)
                        put("fingerprint", p.fields["fingerprint"] ?: "chrome")
                        put("serverName", p.fields["sni"] ?: "")
                        put("publicKey", p.fields["publicKey"] ?: "")
                        put("shortId", p.fields["shortId"] ?: "")
                        put("spiderX", p.fields["spiderX"] ?: "/")
                    },
                )
            } else {
                s.put(
                    "tlsSettings",
                    JSONObject().apply {
                        put("allowInsecure", p.fields["allowInsecure"] == "true")
                        put("serverName", p.fields["sni"] ?: "")
                        p.fields["fingerprint"]?.takeIf { it.isNotEmpty() }?.let { put("fingerprint", it) }
                        p.fields["alpn"]?.takeIf { it.isNotEmpty() }?.let {
                            put("alpn", JSONArray().put(it))
                        }
                    },
                )
            }
        }
        return s
    }
}
