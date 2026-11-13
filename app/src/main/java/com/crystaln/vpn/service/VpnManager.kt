package com.crystaln.vpn.service

import android.content.Context
import androidx.core.content.ContextCompat
import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.wg.WireGuardTunnel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Orchestrates the single active tunnel, whichever engine it uses.
 * Xray-family profiles run inside [CrystalVpnService]; WireGuard runs through
 * the wireguard-go backend which owns its own VpnService.
 */
object VpnManager {

    enum class State { OFF, STARTING, CONNECTED, STOPPING }

    data class UiState(
        val state: State = State.OFF,
        val profile: Profile? = null,
        val error: String? = null,
        val rx: Long = 0,
        val tx: Long = 0,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    @Volatile
    private var wgTunnel: WireGuardTunnel? = null

    fun current(): UiState = _ui.value

    fun isRunning(): Boolean = _ui.value.state == State.STARTING ||
        _ui.value.state == State.CONNECTED

    fun connect(context: Context, profile: Profile) {
        if (isRunning()) stop(context)
        _ui.value = UiState(State.STARTING, profile)
        if (profile.isWireGuard) {
            startWireGuard(context.applicationContext, profile)
        } else {
            ContextCompat.startForegroundService(
                context,
                CrystalVpnService.startIntent(context, profile),
            )
        }
    }

    fun stop(context: Context) {
        val st = _ui.value
        if (st.state == State.OFF || st.state == State.STOPPING) return
        _ui.value = st.copy(state = State.STOPPING)
        if (st.profile?.isWireGuard == true || wgTunnel != null) {
            stopWireGuard()
        } else {
            context.startService(CrystalVpnService.stopIntent(context))
        }
    }

    private fun startWireGuard(context: Context, profile: Profile) {
        try {
            val tunnel = WireGuardTunnel(context, profile)
            tunnel.up()
            wgTunnel = tunnel
            _ui.value = UiState(State.CONNECTED, profile)
        } catch (t: Throwable) {
            wgTunnel = null
            _ui.value = UiState(State.OFF, profile, error = t.message ?: t.javaClass.simpleName)
        }
    }

    private fun stopWireGuard() {
        val t = wgTunnel
        wgTunnel = null
        try {
            t?.down()
        } catch (_: Throwable) {
        }
        _ui.value = UiState(State.OFF, _ui.value.profile)
    }

    /** Called by services/state owners to publish transitions. */
    fun notifyState(state: State, profile: Profile?) {
        _ui.value = _ui.value.copy(state = state, profile = profile ?: _ui.value.profile)
    }

    fun notifyError(message: String) {
        _ui.value = _ui.value.copy(state = State.OFF, error = message)
    }

    /** Polls the active engine for byte counters. Safe to call from any thread. */
    fun pollTraffic(): Pair<Long, Long> {
        wgTunnel?.let { t ->
            return t.traffic() ?: (0L to 0L)
        }
        XrayEngineHolder.traffic()?.let { return it }
        return 0L to 0L
    }
}

/** Indirection so VpnManager does not hard-depend on Xray classes. */
object XrayEngineHolder {
    fun traffic(): Pair<Long, Long>? = try {
        com.crystaln.vpn.xray.XrayCore.traffic()
    } catch (_: Throwable) {
        null
    }
}
