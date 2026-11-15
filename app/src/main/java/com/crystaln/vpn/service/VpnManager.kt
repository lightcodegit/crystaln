package com.crystaln.vpn.service

import android.content.Context
import android.content.Intent
import android.app.PendingIntent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.ContextCompat
import com.crystaln.vpn.R
import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.data.Protocol
import com.crystaln.vpn.openvpn.OpenVpnService
import com.crystaln.vpn.openvpn.OpenVpnStats
import com.crystaln.vpn.wg.AwgTunnel
import com.crystaln.vpn.wg.WgErrorText
import com.crystaln.vpn.wg.WireGuardTunnel
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Orchestrates the single active tunnel, whichever engine it uses.
 * Xray-family profiles run inside [CrystalVpnService]; WireGuard runs through
 * the wireguard-go backend which owns its own VpnService.
 *
 * All state transitions go through [transition] guarded by a generation
 * counter: a slow async shutdown of an old tunnel must never clobber the
 * state of a newer connect() (classic race between `down()` and `up()`).
 */
object VpnManager {

    /** Max wait for the first WireGuard handshake before declaring failure. */
    private const val HANDSHAKE_TIMEOUT_MS = 20_000L

    /**
     * A healthy WireGuard session rekeys at least every ~2 minutes; a longer
     * silence means the network path or the server died and the tunnel must
     * be restarted.
     */
    private const val HANDSHAKE_STALE_MS = 180_000L

    /** Rate limit for automatic reconnects. */
    private const val RECONNECT_MIN_INTERVAL_MS = 60_000L

    /** Grace period after a network switch before restarting the tunnel. */
    private const val NETWORK_CHANGE_DELAY_MS = 4_000L

    /** Rate limit for network-change reconnects (Wi-Fi ↔ mobile bouncing). */
    private const val NETWORK_CHANGE_MIN_INTERVAL_MS = 30_000L

    enum class State { OFF, STARTING, CONNECTED, STOPPING }

    data class UiState(
        val state: State = State.OFF,
        val profile: Profile? = null,
        val error: String? = null,
        val rx: Long = 0,
        val tx: Long = 0,
        /** True while the tunnel is up but the server has not answered yet. */
        val checking: Boolean = false,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    // GoBackend.setState blocks: service start + handshake must run off the
    // main thread or the VpnService onCreate future deadlocks.
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Single 1 Hz poller for traffic counters and handshake staleness. */
    private val pollScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pollLock = Any()
    private var pollJob: Job? = null

    /** Application context of the last connect(); used by auto-reconnect. */
    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var lastReconnectAt = 0L

    /** Serializes connect/stop decisions and the generation check. */
    private val lifecycleLock = Any()

    /** Incremented on every connect(); async completions compare against it. */
    private val generation = AtomicLong(0)

    @Volatile
    private var wgTunnel: WireGuardTunnel? = null

    @Volatile
    private var awgTunnel: AwgTunnel? = null

    /** Registered only once per process; guarded by the CONNECTED state. */
    @Volatile
    private var netCallback: ConnectivityManager.NetworkCallback? = null

    fun current(): UiState = _ui.value

    fun isRunning(): Boolean = _ui.value.state == State.STARTING ||
        _ui.value.state == State.CONNECTED

    fun connect(context: Context, profile: Profile) {
        appContext = context.applicationContext
        val myGeneration = synchronized(lifecycleLock) {
            stopLocked(context)
            // Fresh state also clears any stale error from previous attempts
            // and resets traffic counters for the new session.
            _ui.value = UiState(State.STARTING, profile)
            generation.incrementAndGet()
        }
        // A superseded async shutdown may never publish OFF, so drop the old
        // status notification here as well (stop() is idempotent).
        VpnStatusNotification.stop()
        ensurePoller()
        ensureNetworkWatch()
        when {
            profile.isWireGuardFamily -> if (profile.protocol == Protocol.WIREGUARD) {
                startWireGuard(context.applicationContext, profile, myGeneration)
            } else {
                startAwg(context.applicationContext, profile, myGeneration)
            }

            profile.isOpenVpn -> try {
                ContextCompat.startForegroundService(
                    context,
                    OpenVpnService.startIntent(context, profile),
                )
            } catch (t: Throwable) {
                failGeneration(myGeneration, profile, WgErrorText.of(t))
            }

            else -> try {
                ContextCompat.startForegroundService(
                    context,
                    CrystalVpnService.startIntent(context, profile),
                )
            } catch (t: Throwable) {
                failGeneration(myGeneration, profile, WgErrorText.of(t))
            }
        }
    }

    fun stop(context: Context) {
        synchronized(lifecycleLock) {
            stopLocked(context)
        }
    }

    /** Caller must hold [lifecycleLock]. */
    private fun stopLocked(context: Context) {
        val st = _ui.value
        if (st.state == State.OFF || st.state == State.STOPPING) return
        _ui.value = st.copy(state = State.STOPPING)
        when {
            st.profile?.isOpenVpn == true -> {
                try {
                    context.startService(OpenVpnService.stopIntent(context))
                } catch (_: Throwable) {
                    // The service may already be gone; treat it as stopped.
                    _ui.value = UiState(State.OFF, st.profile)
                }
            }

            st.profile?.isWireGuardFamily == true || wgTunnel != null || awgTunnel != null -> {
                stopWireGuardLocked()
            }

            else -> {
                try {
                    context.startService(CrystalVpnService.stopIntent(context))
                } catch (_: Throwable) {
                    // The service may already be gone; treat it as stopped.
                    _ui.value = UiState(State.OFF, st.profile)
                }
            }
        }
    }

    private fun startWireGuard(context: Context, profile: Profile, myGeneration: Long) {
        engineScope.launch {
            var tunnel: WireGuardTunnel? = null
            try {
                val t = WireGuardTunnel(context, profile)
                tunnel = t
                t.up()
                notifyChecking(true)
                when (awaitHandshake({ t.handshakeHappened() }, myGeneration)) {
                    null -> {
                        // A newer connect()/stop() superseded this attempt.
                        try {
                            t.down()
                        } catch (_: Throwable) {
                        }
                        return@launch
                    }

                    false -> {
                        // No handshake in time: the server is off, unreachable
                        // or the keys don't match — a mere up() proves nothing.
                        try {
                            t.down()
                        } catch (_: Throwable) {
                        }
                        wgTunnel = null
                        failGeneration(myGeneration, profile, context.getString(R.string.server_unreachable))
                    }

                    true -> {
                        val published = publishIfCurrent(myGeneration) {
                            wgTunnel = t
                            UiState(State.CONNECTED, profile)
                        }
                        if (published == null) {
                            try {
                                t.down()
                            } catch (_: Throwable) {
                            }
                            return@launch
                        }
                        // No app-owned service for WireGuard: post the status
                        // notification ("VPN подключён" + speed) from the app side,
                        // with a "Отключить" action backed by a broadcast receiver.
                        VpnStatusNotification.startApp(context, profile.name, wgStopIntent(context))
                    }
                }
            } catch (t: Throwable) {
                tunnel = null
                wgTunnel = null
                failGeneration(myGeneration, profile, WgErrorText.of(t))
            }
        }
    }

    private fun startAwg(context: Context, profile: Profile, myGeneration: Long) {
        engineScope.launch {
            var tunnel: AwgTunnel? = null
            try {
                val t = AwgTunnel(context, profile)
                tunnel = t
                t.up()
                notifyChecking(true)
                when (awaitHandshake({ t.handshakeHappened() }, myGeneration)) {
                    null -> {
                        try {
                            t.down()
                        } catch (_: Throwable) {
                        }
                        return@launch
                    }

                    false -> {
                        try {
                            t.down()
                        } catch (_: Throwable) {
                        }
                        awgTunnel = null
                        failGeneration(myGeneration, profile, context.getString(R.string.server_unreachable))
                    }

                    true -> {
                        val published = publishIfCurrent(myGeneration) {
                            awgTunnel = t
                            UiState(State.CONNECTED, profile)
                        }
                        if (published == null) {
                            try {
                                t.down()
                            } catch (_: Throwable) {
                            }
                            return@launch
                        }
                        VpnStatusNotification.startApp(context, profile.name, wgStopIntent(context))
                    }
                }
            } catch (t: Throwable) {
                tunnel = null
                awgTunnel = null
                failGeneration(myGeneration, profile, WgErrorText.of(t))
            }
        }
    }

    /** "Отключить" action target for the app-level status notification. */
    private fun wgStopIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        2001,
        Intent(context, VpnDisconnectReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * Waits for the first peer handshake — the only real proof that the
     * server is alive and the keys match. Returns false on timeout, null
     * when the attempt was superseded by a newer connect()/stop().
     */
    private suspend fun awaitHandshake(check: () -> Boolean, myGeneration: Long): Boolean? {
        val deadline = System.currentTimeMillis() + HANDSHAKE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (synchronized(lifecycleLock) { generation.get() != myGeneration }) return null
            if (check()) return true
            delay(500)
        }
        return false
    }

    /**
     * Applies [compute] only if this generation is still the newest one.
     * Returns the published state, or null when the attempt was superseded.
     */
    private fun publishIfCurrent(myGeneration: Long, compute: () -> UiState): UiState? =
        synchronized(lifecycleLock) {
            if (generation.get() != myGeneration) return null
            _ui.value = compute()
            _ui.value
        }

    /** Publishes an OFF+error result unless a newer attempt superseded it. */
    private fun failGeneration(myGeneration: Long, profile: Profile, message: String) {
        synchronized(lifecycleLock) {
            if (generation.get() != myGeneration) return
            _ui.value = UiState(State.OFF, profile, error = message)
        }
        VpnStatusNotification.stop()
    }

    /** Caller must hold [lifecycleLock]. */
    private fun stopWireGuardLocked() {
        val t = wgTunnel
        val a = awgTunnel
        wgTunnel = null
        awgTunnel = null
        val myGeneration = generation.incrementAndGet()
        engineScope.launch {
            try {
                t?.down()
            } catch (_: Throwable) {
            }
            try {
                a?.down()
            } catch (_: Throwable) {
            }
            synchronized(lifecycleLock) {
                if (generation.get() == myGeneration) {
                    _ui.value = UiState(State.OFF, _ui.value.profile)
                    VpnStatusNotification.stop()
                }
            }
        }
    }

    /** Marks the "server is being verified" phase while a tunnel comes up. */
    fun notifyChecking(checking: Boolean) {
        _ui.value = _ui.value.copy(checking = checking)
    }

    /** Called by services/state owners to publish transitions. */
    fun notifyState(state: State, profile: Profile?) {
        synchronized(lifecycleLock) {
            _ui.value = _ui.value.copy(
                state = state,
                profile = profile ?: _ui.value.profile,
                // A successful connect clears stale error text.
                error = if (state == State.CONNECTED) null else _ui.value.error,
                // The check phase only exists within STARTING.
                checking = if (state == State.STARTING) _ui.value.checking else false,
            )
        }
        // The status row ("VPN подключён" + speed) must not outlive the tunnel.
        if (state == State.OFF) VpnStatusNotification.stop()
    }

    /** Publishes polled byte counters for the UI. */
    fun notifyTraffic(rx: Long, tx: Long) {
        _ui.value = _ui.value.copy(rx = rx, tx = tx)
    }

    fun notifyError(message: String) {
        synchronized(lifecycleLock) {
            _ui.value = _ui.value.copy(state = State.OFF, error = message, checking = false)
        }
        VpnStatusNotification.stop()
    }

    /** Polls the active engine for byte counters. Safe to call from any thread. */
    fun pollTraffic(): Pair<Long, Long> {
        awgTunnel?.let { t ->
            return t.traffic() ?: (0L to 0L)
        }
        wgTunnel?.let { t ->
            return t.traffic() ?: (0L to 0L)
        }
        OpenVpnStats.traffic()?.let { return it }
        XrayEngineHolder.traffic()?.let { return it }
        return 0L to 0L
    }

    /**
     * Single 1 Hz loop feeding the UI (and the status notification, which
     * reads the published counters) and watching the WireGuard session:
     * a silent peer for minutes means the network path died — restart the
     * tunnel instead of showing "Подключено" forever.
     */
    private fun ensurePoller() {
        synchronized(pollLock) {
            if (pollJob?.isActive == true) return
            pollJob = pollScope.launch {
                while (isActive) {
                    delay(1000)
                    val st = _ui.value
                    if (st.state != State.CONNECTED) {
                        // Nothing to sample while connecting/stopping; the
                        // poller quits once idle so connect() restarts it.
                        if (st.state == State.OFF) break
                        continue
                    }
                    val (rx, tx) = pollTraffic()
                    _ui.value = _ui.value.copy(rx = rx, tx = tx)
                    reconnectIfWireGuardStale(st)
                }
            }
        }
    }

    /**
     * Registers a connectivity watcher (once per process) so the Xray-family
     * tunnel can be restarted after the device switches networks — their
     * sockets silently die on Wi-Fi ↔ mobile transitions, while WireGuard
     * detects the same condition via its own stale-handshake watchdog.
     */
    private fun ensureNetworkWatch() {
        if (netCallback != null) return
        val ctx = appContext ?: return
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
            .build()
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scheduleNetworkChangeReconnect()
            }
        }
        try {
            cm?.registerNetworkCallback(request, cb)
            netCallback = cb
        } catch (_: Throwable) {
            // Connectivity watching is best-effort; the WireGuard watchdog
            // and the user's manual reconnect remain available.
        }
    }

    /**
     * Debounced full restart of the live Xray-family session after a network
     * switch. Generation check: a stop() or newer connect() that happened
     * while we waited must win.
     */
    private fun scheduleNetworkChangeReconnect() {
        val st = _ui.value
        if (st.state != State.CONNECTED) return
        val p = st.profile ?: return
        if (p.isWireGuardFamily) return
        val now = System.currentTimeMillis()
        if (now - lastReconnectAt < NETWORK_CHANGE_MIN_INTERVAL_MS) return
        lastReconnectAt = now
        val myGeneration = generation.get()
        engineScope.launch {
            delay(NETWORK_CHANGE_DELAY_MS)
            val ctx = appContext ?: return@launch
            val cur = _ui.value
            if (cur.state != State.CONNECTED || generation.get() != myGeneration) return@launch
            connect(ctx, cur.profile ?: p)
        }
    }

    /**
     * Restarts the tunnel when the WireGuard handshake has been silent for
     * [HANDSHAKE_STALE_MS] — typically after the device changed networks
     * (Wi-Fi ↔ mobile) or the server went away.
     */    private fun reconnectIfWireGuardStale(st: UiState) {        val p = st.profile ?: return
        if (!p.isWireGuardFamily) return
        val handshake = awgTunnel?.lastHandshakeAt()
            ?: wgTunnel?.lastHandshakeAt()
            ?: return
        if (handshake <= 0L) return
        val now = System.currentTimeMillis()
        if (now - handshake < HANDSHAKE_STALE_MS) return
        if (now - lastReconnectAt < RECONNECT_MIN_INTERVAL_MS) return
        lastReconnectAt = now
        val ctx = appContext ?: return
        // Full fresh attempt: stop + up + handshake gate.
        connect(ctx, p)
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
