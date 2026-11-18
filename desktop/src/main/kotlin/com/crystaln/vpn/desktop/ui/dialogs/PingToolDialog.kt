package com.crystaln.vpn.desktop.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.crystaln.vpn.desktop.ui.tcpPing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ping any address without touching the tunnel: TCP connect timing to
 * host:port (ICMP raw sockets need admin, a handshake does not). Mirrors the
 * per-profile ping in the profile list, but for an arbitrary target.
 */
@Composable
internal fun PingToolDialog(onDismiss: () -> Unit) {
    var target by remember { mutableStateOf("1.1.1.1") }
    var running by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Long>>(emptyList()) }
    val scope = rememberCoroutineScope()

    fun parse(raw: String): Pair<String, Int>? {
        val t = raw.trim().removePrefix("tcp://")
        if (t.isEmpty() || t.any { it.isWhitespace() }) return null
        val host = t.substringBeforeLast(':').ifEmpty { t }
        val port = t.substringAfterLast(':', "").toIntOrNull() ?: 443
        if (host == t && t.contains(':')) return null
        if (port !in 1..65535) return null
        return host to port
    }

    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text("Ping-инструмент") },
        text = {
            Column {
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    singleLine = true,
                    label = { Text("Адрес (host или host:порт)") },
                    supportingText = { Text("Порт по умолчанию 443; замеряется TCP-рукопожатие.") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                if (results.isEmpty() && !running) {
                    Text(
                        "Нажмите «Пинговать» — 4 замера по 2 с таймаутом.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
                results.forEachIndexed { i, ms ->
                    Text(
                        "Ответ ${i + 1}: " + if (ms >= 0) "$ms мс" else "таймаут",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ms >= 0) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.error,
                    )
                }
                if (results.isNotEmpty() && !running) {
                    val ok = results.filter { it >= 0 }
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Text(
                            if (ok.isEmpty()) "Хост недоступен"
                            else "min ${ok.minOrNull()} мс · avg ${ok.average().toLong()} мс · потерь ${results.size - ok.size}/${results.size}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (ok.isEmpty()) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !running && parse(target) != null,
                onClick = {
                    val (host, port) = parse(target) ?: return@TextButton
                    running = true
                    results = emptyList()
                    scope.launch(Dispatchers.IO) {
                        repeat(4) {
                            val ms = tcpPing(host, port)
                            withContext(Dispatchers.Main) { results = results + ms }
                            kotlinx.coroutines.delay(400)
                        }
                        withContext(Dispatchers.Main) { running = false }
                    }
                },
            ) {
                Text(if (running) "Идёт…" else "Пинговать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
    )
}
