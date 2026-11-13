package com.crystaln.vpn.wg

import android.content.Context
import com.crystaln.vpn.data.Profile
import java.io.BufferedReader
import java.io.StringReader
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config

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
}
