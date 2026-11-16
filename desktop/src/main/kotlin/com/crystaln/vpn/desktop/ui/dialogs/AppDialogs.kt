package com.crystaln.vpn.desktop.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.crystaln.vpn.data.UpdateChecker

/** "New release available" → opens the GitHub releases page. */
@Composable
internal fun UpdateDialog(
    release: UpdateChecker.Release?,
    currentVersion: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (release == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Доступна новая версия") },
        text = { Text("Установлена версия $currentVersion. Свежая — ${release.version}. Откройте страницу релизов, чтобы скачать.") },
        confirmButton = {
            TextButton(onClick = onOpen) { Text("Открыть релизы") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Позже") }
        },
    )
}

@Composable
internal fun ImportDialog(
    onDismiss: () -> Unit,
    onClipboard: () -> Unit,
    onFile: () -> Unit,
    onExport: () -> Unit,
    onUrl: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        title = { Text("Профили") },
        text = {
            Column {
                ImportAction(Icons.Default.FileOpen, "Установить конфиг из файла", onFile)
                ImportAction(Icons.Default.ContentPaste, "Импорт из буфера", onClipboard)
                ImportAction(Icons.Default.Link, "Импорт по ссылке", onUrl)
                ImportAction(Icons.Default.FileDownload, "Экспорт в файл", onExport)
            }
        },
    )
}

@Composable
internal fun UrlImportDialog(
    onDismiss: () -> Unit,
    onOk: (String) -> Unit,
) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Импорт по ссылке") },
        text = {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                singleLine = true,
                placeholder = { Text("Вставьте ссылку на конфиг") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = url.trim().startsWith("http://") || url.trim().startsWith("https://"),
                onClick = { onOk(url.trim()) },
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/**
 * Asks how to export: raw JSON (full fidelity, no protection) or an
 * AES-GCM "vault" encrypted with a user password. null password = raw.
 */
@Composable
internal fun ExportDialog(
    onDismiss: () -> Unit,
    onExport: (String?) -> Unit,
) {
    var usePassword by remember { mutableStateOf(true) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val passwordOk = usePassword &&
        password.length >= 4 &&
        password == confirm
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Экспорт в файл") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = usePassword,
                        onCheckedChange = { usePassword = it },
                    )
                    Text("Защитить паролем")
                }
                if (usePassword) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("Пароль (минимум 4 символа)") },
                        isError = password.isNotEmpty() && password.length < 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = confirm,
                        onValueChange = { confirm = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("Повторите пароль") },
                        isError = confirm.isNotEmpty() && password != confirm,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !usePassword || passwordOk,
                onClick = { onExport(if (usePassword) password else null) },
            ) {
                Text("Экспорт в файл")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/** Password prompt for importing a vault envelope. */
@Composable
internal fun VaultPasswordDialog(
    onDismiss: () -> Unit,
    onUnlock: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Защищённый экспорт") },
        text = {
            Column {
                Text(
                    "Файл зашифрован. Введите пароль, которым он был создан.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("Пароль (минимум 4 символа)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = password.isNotEmpty(),
                onClick = { onUnlock(password) },
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
private fun ImportAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(24.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(label)
        }
    }
}
