package com.crystaln.vpn.ui

import android.content.Context
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import com.crystaln.vpn.R
import com.crystaln.vpn.data.Profile
import com.crystaln.vpn.data.Protocol
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Protocol-aware profile editor.
 *
 * Fields are grouped: identity fields are always visible for the chosen
 * protocol; transport fields show only for transports that use them; security
 * fields show for TLS/REALITY. WireGuard uses a raw wg-quick config editor.
 */
class ProfileEditorDialog(
    private val context: Context,
    private val existing: Profile?,
    private val onSave: (Profile) -> Unit,
) {

    private class Field(
        val key: String,
        val label: TextView,
        val edit: EditText,
        val group: Group,
    ) {
        enum class Group { IDENTITY, TRANSPORT, SECURITY }
    }

    private val transports = listOf("tcp", "ws", "grpc", "httpupgrade", "xhttp", "kcp", "quic", "http")
    private val protocols = Protocol.entries.toList()

    private val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(12), dp(24), dp(0))
        layoutParams = ViewGroup.LayoutParams(dp(340), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private val fields = mutableListOf<Field>()
    private var protocolSpinner: Spinner? = null
    private var networkSpinner: Spinner? = null
    private var tlsBox: CheckBox? = null
    private var realityBox: CheckBox? = null
    private var insecureBox: CheckBox? = null
    private var wgConfigEdit: EditText? = null

    fun show() {
        val editing = existing != null
        if (editing) {
            renderFor(existing!!.protocol)
        } else {
            root.addView(label("Protocol"))
            val spinner = Spinner(context).apply {
                adapter = ArrayAdapter(
                    context,
                    android.R.layout.simple_spinner_dropdown_item,
                    protocols.map { it.displayName },
                )
                onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                        renderFor(protocols[pos])
                    }

                    override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
                }
            }
            protocolSpinner = spinner
            root.addView(spinner)
            renderFor(protocols[0])
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(if (editing) R.string.edit_profile else R.string.add_profile)
            .setView(root)
            .setPositiveButton(R.string.save) { _, _ -> save() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Rebuilds everything below the protocol selector for a protocol change. */
    private fun renderFor(protocol: Protocol) {
        fields.clear()
        tlsBox = null
        realityBox = null
        insecureBox = null
        wgConfigEdit = null
        // Remove old views; keep the protocol selector (2 rows) when present.
        val keep = if (protocolSpinner != null) 2 else 0
        while (root.childCount > keep) root.removeViewAt(root.childCount - 1)

        if (protocol == Protocol.WIREGUARD) {
            root.addView(label("wg-quick config"))
            val edit = editText("wgConfig").apply {
                minLines = 8
                setSingleLine(false)
                inputType = InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_CLASS_TEXT
                gravity = android.view.Gravity.TOP
            }
            wgConfigEdit = edit
            edit.hint = "[Interface]\nPrivateKey = ...\nAddress = 10.0.0.2/32\nDNS = 1.1.1.1\n\n[Peer]\nPublicKey = ...\nEndpoint = host:port\nAllowedIPs = 0.0.0.0/0, ::/0"
            root.addView(edit)
            existing?.let { edit.setText(it.wgConfig) }
            return
        }

        // Identity
        addField("name", context.getString(R.string.name), Field.Group.IDENTITY)
        if (protocol != Protocol.SHADOWSOCKS) {
            addField("address", context.getString(R.string.address), Field.Group.IDENTITY, numeric = false)
            addField("port", context.getString(R.string.port), Field.Group.IDENTITY, numeric = true)
        }
        when (protocol) {
            Protocol.VMESS -> {
                addField("uuid", "UUID", Field.Group.IDENTITY)
                addField("alterId", "alterId", Field.Group.IDENTITY, numeric = true)
            }

            Protocol.VLESS -> {
                addField("uuid", "UUID", Field.Group.IDENTITY)
                addField("flow", "flow (xtls-rprx-vision / empty)", Field.Group.IDENTITY)
            }

            Protocol.TROJAN -> addField("password", "Password", Field.Group.IDENTITY)

            Protocol.SHADOWSOCKS -> {
                addField("method", "Method (aes-256-gcm, chacha20-ietf-poly1305, ...)", Field.Group.IDENTITY)
                addField("password", "Password", Field.Group.IDENTITY)
                addField("address", context.getString(R.string.address), Field.Group.IDENTITY)
                addField("port", context.getString(R.string.port), Field.Group.IDENTITY, numeric = true)
            }

            Protocol.SOCKS, Protocol.HTTP -> {
                addField("username", "Username (optional)", Field.Group.IDENTITY)
                addField("password", "Password (optional)", Field.Group.IDENTITY)
            }

            Protocol.WIREGUARD -> Unit
        }

        // Transport
        root.addView(label("Transport"))
        val spinner = Spinner(context).apply {
            adapter = ArrayAdapter(
                context,
                android.R.layout.simple_spinner_dropdown_item,
                transports,
            )
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p0: android.widget.AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                    refreshTransportVisibility(transports[pos])
                }

                override fun onNothingSelected(p0: android.widget.AdapterView<*>?) {}
            }
        }
        networkSpinner = spinner
        root.addView(spinner)
        addField("host", "Host header", Field.Group.TRANSPORT)
        addField("path", "Path", Field.Group.TRANSPORT)
        addField("serviceName", "gRPC service name", Field.Group.TRANSPORT)
        addField("headerType", "Header type (kcp/quic)", Field.Group.TRANSPORT)
        addField("kcpSeed", "KCP seed", Field.Group.TRANSPORT)

        // Security
        root.addView(label("Security"))
        tlsBox = checkBox("TLS")
        realityBox = checkBox("REALITY")
        addField("sni", "SNI / serverName", Field.Group.SECURITY)
        addField("fingerprint", "uTLS fingerprint", Field.Group.SECURITY)
        addField("alpn", "ALPN", Field.Group.SECURITY)
        addField("publicKey", "REALITY public key", Field.Group.SECURITY)
        addField("shortId", "REALITY shortId", Field.Group.SECURITY)
        addField("spiderX", "REALITY spiderX", Field.Group.SECURITY)
        val cb = CheckBox(context).apply { text = "Allow insecure TLS" }
        insecureBox = cb
        root.addView(cb)
        val insecureField = Field("allowInsecure", TextView(context), EditText(context), Field.Group.SECURITY)
        fields.add(insecureField)

        // Prefill
        existing?.let { p ->
            fields.firstOrNull { it.key == "name" }?.edit?.setText(p.name)
            if (p.fields["address"] == null && p.address.isNotEmpty()) {
                // address/port live on the profile, not in fields
            }
            fields.firstOrNull { it.key == "address" }?.edit?.setText(p.address)
            fields.firstOrNull { it.key == "port" }?.edit?.setText(if (p.port > 0) p.port.toString() else "")
            p.fields.forEach { (k, v) ->
                if (k != "network" && k != "tls" && k != "reality" && k != "allowInsecure") {
                    fields.firstOrNull { it.key == k }?.edit?.setText(v)
                }
            }
            tlsBox?.isChecked = p.fields["tls"] == "true"
            realityBox?.isChecked = p.fields["reality"] == "true"
            insecureBox?.isChecked = p.fields["allowInsecure"] == "true"
            val net = transports.firstOrNull { it == p.fields["network"] } ?: "tcp"
            spinner.setSelection(transports.indexOf(net))
        }

        refreshTransportVisibility(transports[spinner.selectedItemPosition])
        refreshSecurityVisibility()
        tlsBox?.setOnCheckedChangeListener { _, _ -> refreshSecurityVisibility() }
        realityBox?.setOnCheckedChangeListener { _, _ -> refreshSecurityVisibility() }
    }

    private fun addField(key: String, labelText: String, group: Field.Group, numeric: Boolean = false) {
        val l = label(labelText)
        val e = editText(key)
        if (numeric) e.inputType = InputType.TYPE_CLASS_NUMBER
        root.addView(l)
        root.addView(e)
        fields.add(Field(key, l, e, group))
    }

    private fun refreshTransportVisibility(network: String) {
        val visible = when (network) {
            "ws", "httpupgrade", "xhttp", "http" -> setOf("host", "path")
            "grpc" -> setOf("serviceName")
            "kcp" -> setOf("headerType", "kcpSeed")
            "quic" -> setOf("headerType")
            else -> emptySet()
        }
        fields.filter { it.group == Field.Group.TRANSPORT }.forEach { f ->
            val show = f.key in visible
            f.label.visibility = if (show) View.VISIBLE else View.GONE
            f.edit.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    private fun refreshSecurityVisibility() {
        val tls = tlsBox?.isChecked == true
        val reality = realityBox?.isChecked == true
        val visible = when {
            reality -> setOf("sni", "fingerprint", "publicKey", "shortId", "spiderX")
            tls -> setOf("sni", "fingerprint", "alpn", "allowInsecure")
            else -> emptySet()
        }
        fields.filter { it.group == Field.Group.SECURITY }.forEach { f ->
            if (f.key == "allowInsecure") {
                insecureBox?.visibility = if ("allowInsecure" in visible) View.VISIBLE else View.GONE
            } else {
                val show = f.key in visible
                f.label.visibility = if (show) View.VISIBLE else View.GONE
                f.edit.visibility = if (show) View.VISIBLE else View.GONE
            }
        }
    }

    private fun save() {
        val protocol = protocolSpinner?.let { protocols[it.selectedItemPosition] }
            ?: existing?.protocol ?: Protocol.VLESS
        val name = text("name").ifEmpty {
            existing?.name ?: "${protocol.displayName} ${text("address")}"
        }
        val profile = existing?.copy() ?: Profile(name = name, protocol = protocol)
        profile.name = name
        if (protocol == Protocol.WIREGUARD) {
            profile.wgConfig = wgConfigEdit?.text?.toString()?.trim() ?: ""
        } else {
            profile.address = text("address")
            profile.port = text("port").toIntOrNull() ?: 0
            profile.fields = mutableMapOf()
            listOf(
                "uuid", "alterId", "flow", "password", "method", "username",
                "host", "path", "serviceName", "headerType", "kcpSeed",
                "sni", "fingerprint", "alpn", "publicKey", "shortId", "spiderX",
            ).forEach { k -> text(k).takeIf { it.isNotEmpty() }?.let { profile.fields[k] = it } }
            networkSpinner?.let { profile.fields["network"] = transports[it.selectedItemPosition] }
            if (realityBox?.isChecked == true) profile.fields["reality"] = "true"
            if (tlsBox?.isChecked == true) profile.fields["tls"] = "true"
            if (insecureBox?.isChecked == true) profile.fields["allowInsecure"] = "true"
        }
        onSave(profile)
    }

    private fun text(key: String): String =
        fields.firstOrNull { it.key == key }?.edit?.text?.toString()?.trim() ?: ""

    private fun label(text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 12f
        setPadding(0, dp(12), 0, dp(2))
    }

    private fun editText(key: String): EditText = EditText(context)

    private fun checkBox(text: String): CheckBox = CheckBox(context).apply {
        this.text = text
        root.addView(this)
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
}
