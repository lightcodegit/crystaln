package com.crystaln.vpn.net

/**
 * TCP-connect "ping": measures how long a host:port takes to accept a
 * connection. Not ICMP, but a good reachability/latency proxy for any
 * address, and it works without special permissions.
 */
object TcpPing {
    const val DEFAULT_PORT = 443
    const val TIMEOUT_MS = 5000

    /** Round-trip connect time in ms, or -1 when unreachable. */
    fun measure(host: String, port: Int, timeoutMs: Int = TIMEOUT_MS): Long = try {
        val started = System.nanoTime()
        java.net.Socket().use { s ->
            s.connect(java.net.InetSocketAddress(host, port), timeoutMs)
        }
        (System.nanoTime() - started) / 1_000_000
    } catch (_: Throwable) {
        -1L
    }

    /**
     * Parses user input like "example.com", "example.com:443" or a bare IP;
     * strips an optional scheme. Returns null when the host part is empty.
     */
    fun parse(input: String): Pair<String, Int>? {
        var s = input.trim().removePrefix("https://").removePrefix("http://")
            .substringBefore('/')
        if (s.isEmpty()) return null
        // Don't split IPv6 literals without brackets; handle "host:port".
        val port = if (s.count { it == ':' } == 1) {
            val p = s.substringAfter(':').toIntOrNull()
            s = s.substringBefore(':')
            p
        } else {
            null
        }
        return s to (port ?: DEFAULT_PORT)
    }
}
