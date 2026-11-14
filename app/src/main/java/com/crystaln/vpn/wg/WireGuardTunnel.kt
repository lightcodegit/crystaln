package com.crystaln.vpn.wg

import android.content.Context
import com.crystaln.vpn.data.Profile
import java.io.BufferedReader
import java.io.StringReader
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config
import org.amnezia.awg.crypto.Key

/**
 * Plain WireGuard tunnel. Runs on the AmneziaWG userspace core
 * (amneziawg-go is a WireGuard superset: standard wg-quick configs work
 * unchanged; junk/obfuscation params simply stay at defaults).
 */
class WireGuardTunnel(context: Context, private val profile: Profile) : Tunnel {

    private val backend = GoBackend(context.applicationContext)
    private val config: Config = Config.parse(
        BufferedReader(StringReader(WgConfigNormalizer.normalize(profile.wgConfig))),
    )

    /** Public key of the first [Peer], used to look up its handshake stats. */
    private val peerKey: Key? = config.peers.firstOrNull()?.publicKey

    @Volatile
    private var up = false

    override fun getName(): String = "crystaln_${profile.id.take(8)}"

    override fun onStateChange(newState: Tunnel.State) {
        up = newState == Tunnel.State.UP
    }

    fun up() {
        backend.setState(this, Tunnel.State.UP, config)
        up = true
    }

    fun down() {
        backend.setState(this, Tunnel.State.DOWN, null)
        up = false
    }

    fun isUp(): Boolean = up

    fun traffic(): Pair<Long, Long>? {
        if (!isUp()) return null
        val stats = backend.getStatistics(this) ?: return null
        return stats.totalRx() to stats.totalTx()
    }

    /**
     * True once the peer has completed at least one Noise handshake — the
     * only real proof that the server is alive and the keys match (a tunnel
     * being "UP" merely means the config was applied).
     */
    fun handshakeHappened(): Boolean {
        val key = peerKey ?: return true // nothing to verify against
        val stats = backend.getStatistics(this) ?: return false
        return (stats.peer(key)?.latestHandshakeEpochMillis ?: 0L) > 0L
    }

    /** Epoch millis of the peer's last handshake, or 0 if there was none yet. */
    fun lastHandshakeAt(): Long {
        val key = peerKey ?: return 0L
        val stats = backend.getStatistics(this) ?: return 0L
        return stats.peer(key)?.latestHandshakeEpochMillis ?: 0L
    }
}
