package com.crystaln.vpn.wg

import android.content.Context
import com.crystaln.vpn.data.AwgParams
import com.crystaln.vpn.data.Profile
import java.io.BufferedReader
import java.io.StringReader
import org.amnezia.awg.backend.GoBackend
import org.amnezia.awg.backend.Tunnel
import org.amnezia.awg.config.Config
import org.amnezia.awg.crypto.Key

/**
 * Real AmneziaWG tunnel on the official AmneziaWG Android core
 * (amneziawg-go userspace, package org.amnezia.awg).
 *
 * Junk/obfuscation params (Jc, Jmin, Jmax, S1, S2, H1..H4) live in
 * [Profile.fields] and are injected into the [Interface] section per the
 * AmneziaWG config format; keys already present in the user's config win.
 */
class AwgTunnel(context: Context, private val profile: Profile) : Tunnel {

    private val backend = GoBackend(context.applicationContext)
    private val config: Config = Config.parse(
        BufferedReader(StringReader(WgConfigNormalizer.normalize(profile.wgConfig, AwgParams.fromFields(profile.fields)))),
    )

    /** Public key of the first [Peer], used to look up its handshake stats. */
    private val peerKey: Key? = config.peers.firstOrNull()?.publicKey

    @Volatile
    private var up = false

    override fun getName(): String = "crystaln_awg_${profile.id.take(8)}"

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
}
