package com.crystaln.vpn.wg

import android.content.Context
import com.crystaln.vpn.data.Profile
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.BufferedReader
import java.io.StringReader

/**
 * WireGuard tunnel over the official userspace (wireguard-go) backend.
 *
 * The backend starts [GoBackend.VpnService] itself, which must be declared in
 * the manifest; the raw wg-quick style config comes from the profile.
 */
class WireGuardTunnel(context: Context, private val profile: Profile) : Tunnel {

    private val backend = GoBackend(context.applicationContext)
    private val config: Config = Config.parse(BufferedReader(StringReader(profile.wgConfig)))

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

    /** Cumulative byte counters since tunnel start; null when not up. */
    fun traffic(): Pair<Long, Long>? {
        if (!isUp()) return null
        val t = WgStats.totals(backend.getStatistics(this))
        return t[0] to t[1]
    }
}
