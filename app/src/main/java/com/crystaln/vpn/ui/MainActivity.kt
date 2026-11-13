package com.crystaln.vpn.ui

import android.Manifest
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.crystaln.vpn.R
import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.data.ProfileStore
import com.crystaln.vpn.data.ShareLinkParser
import com.crystaln.vpn.service.VpnManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var adapter: ProfilesAdapter
    private lateinit var stateText: TextView
    private lateinit var profileText: TextView
    private lateinit var trafficText: TextView
    private lateinit var errorText: TextView
    private lateinit var connectButton: com.google.android.material.button.MaterialButton

    private var pendingProfileId: String? = null
    private var trafficJob: Job? = null

    private val vpnPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val id = pendingProfileId
            pendingProfileId = null
            if (result.resultCode == Activity.RESULT_OK && id != null) {
                ProfileStore.load(this).firstOrNull { it.id == id }?.let { launchConnect(it) }
            } else {
                Toast.makeText(this, R.string.vpn_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        stateText = findViewById(R.id.stateText)
        profileText = findViewById(R.id.profileText)
        trafficText = findViewById(R.id.trafficText)
        errorText = findViewById(R.id.errorText)
        connectButton = findViewById(R.id.connectButton)

        adapter = ProfilesAdapter(
            profiles = ProfileStore.load(this).toList(),
            selectedId = ProfileStore.selectedId,
            onSelect = { p ->
                ProfileStore.select(this, p.id)
                adapter.submit(ProfileStore.load(this).toList(), p.id)
                renderIdle()
            },
            onEdit = { p -> ProfileEditorDialog(this, p) { updated -> saveAndRefresh(updated) }.show() },
            onDelete = { p ->
                ProfileStore.remove(this, p.id)
                adapter.submit(ProfileStore.load(this).toList(), ProfileStore.selectedId)
                renderIdle()
            },
        )
        findViewById<RecyclerView>(R.id.profilesList).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        findViewById<FloatingActionButton>(R.id.addButton).setOnClickListener {
            ProfileEditorDialog(this, null) { p -> saveAndRefresh(p) }.show()
        }
        findViewById<FloatingActionButton>(R.id.importButton).setOnClickListener { importClipboard() }
        connectButton.setOnClickListener { onConnectClicked() }

        requestNotifPermission()

        lifecycleScope.launch {
            VpnManager.ui.collect { render(it) }
        }
        startTrafficPolling()
    }

    private fun onConnectClicked() {
        when (VpnManager.current().state) {
            VpnManager.State.OFF -> {
                val p = ProfileStore.selected(this) ?: run {
                    Toast.makeText(this, R.string.no_profile, Toast.LENGTH_SHORT).show()
                    return
                }
                pendingProfileId = p.id
                val intent = VpnService.prepare(this)
                if (intent != null) {
                    vpnPermission.launch(intent)
                } else {
                    launchConnect(p)
                }
            }

            else -> VpnManager.stop(this)
        }
    }

    private fun launchConnect(profile: Profile) {
        VpnManager.connect(this, profile)
    }

    private fun importClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.getItemAt(0)?.text?.toString() ?: return
        val parsed = ShareLinkParser.parseAll(text)
        if (parsed.isEmpty()) {
            Snackbar.make(findViewById(R.id.profilesList), "Nothing to import", Snackbar.LENGTH_SHORT).show()
            return
        }
        parsed.forEach { ProfileStore.upsert(this, it) }
        adapter.submit(ProfileStore.load(this).toList(), ProfileStore.selectedId)
        Snackbar.make(
            findViewById(R.id.profilesList),
            getString(R.string.import_result, parsed.size, 0),
            Snackbar.LENGTH_SHORT,
        ).show()
    }

    private fun saveAndRefresh(p: Profile) {
        ProfileStore.upsert(this, p)
        adapter.submit(ProfileStore.load(this).toList(), ProfileStore.selectedId)
        renderIdle()
    }

    private fun renderIdle() {
        render(VpnManager.current())
    }

    private fun render(s: VpnManager.UiState) {
        stateText.text = when (s.state) {
            VpnManager.State.OFF -> getString(R.string.state_off)
            VpnManager.State.STARTING -> getString(R.string.state_starting)
            VpnManager.State.CONNECTED -> getString(R.string.state_connected)
            VpnManager.State.STOPPING -> getString(R.string.state_stopping)
        }
        profileText.text = s.profile?.summary() ?: ProfileStore.selected(this)?.summary() ?: getString(R.string.no_profile)
        errorText.visibility = if (s.error == null) TextView.GONE else TextView.VISIBLE
        errorText.text = s.error?.let { getString(R.string.state_error, it) }

        when (s.state) {
            VpnManager.State.OFF -> {
                connectButton.text = getString(R.string.connect)
                trafficText.visibility = TextView.GONE
            }

            VpnManager.State.CONNECTED -> {
                connectButton.text = getString(R.string.disconnect)
                trafficText.visibility = TextView.VISIBLE
                trafficText.text = formatTraffic(s.rx, s.tx)
            }

            else -> connectButton.text = getString(R.string.disconnect)
        }
    }

    private fun formatTraffic(rx: Long, tx: Long): String =
        getString(R.string.traffic_format, humanBytes(rx), humanBytes(tx))

    private fun humanBytes(v: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var x = v.toDouble()
        var u = 0
        while (x >= 1024 && u < units.size - 1) {
            x /= 1024
            u++
        }
        return "%.1f %s".format(x, units[u])
    }

    private fun startTrafficPolling() {
        trafficJob?.cancel()
        trafficJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                if (VpnManager.current().state == VpnManager.State.CONNECTED) {
                    val (rx, tx) = VpnManager.pollTraffic()
                    runOnUiThread {
                        trafficText.visibility = TextView.VISIBLE
                        trafficText.text = formatTraffic(rx, tx)
                    }
                }
                delay(1000)
            }
        }
    }

    private fun requestNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
