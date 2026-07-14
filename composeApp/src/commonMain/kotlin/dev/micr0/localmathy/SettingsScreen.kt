package dev.micr0.localmathy

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    settings: AppSettings,
    historyCount: Int,
    modelState: ModelState,
    visionModelState: ModelState,
    onClearHistory: () -> Unit,
    onDeleteModel: () -> Unit,
    onDeleteVisionModel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClearHistory by remember { mutableStateOf(false) }
    var confirmDeleteModel by remember { mutableStateOf(false) }
    var confirmDeleteVisionModel by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
    ) {
        SectionCard(title = "Compute backend") {
            Text(
                "Auto tries the GPU and falls back to the CPU. Applies to your next question.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                EngineBackend.entries.forEach { option ->
                    FilterChip(
                        selected = settings.backend == option,
                        onClick = { settings.backend = option },
                        label = { Text(option.name.uppercase()) },
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = "Notifications") {
            ToggleRow(
                title = "Notify when done",
                subtitle = "Post a notification when an answer is ready while the app is in the background.",
                checked = settings.notifyWhenDone,
                onCheckedChange = { settings.notifyWhenDone = it },
            )
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = "History") {
            ToggleRow(
                title = "Save history",
                subtitle = "Store your questions and answers in a local, on-device database.",
                checked = settings.historyEnabled,
                onCheckedChange = { settings.historyEnabled = it },
            )

            if (settings.historyEnabled) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    "What to save",
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(4.dp))
                HistoryDetail.entries.forEach { detail ->
                    DetailOption(
                        detail = detail,
                        selected = settings.historyDetail == detail,
                        onSelect = { settings.historyDetail = detail },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { confirmClearHistory = true },
                enabled = historyCount > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(if (historyCount > 0) "Clear history ($historyCount)" else "History is empty")
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = "Model") {
            val installed = modelState is ModelState.Available
            Text(
                if (installed) {
                    "${ModelInfo.Solver.name} is installed" +
                        ((modelState as? ModelState.Available)?.let { " (${formatBytes(it.sizeBytes)})" } ?: "")
                } else {
                    "No model installed."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { confirmDeleteModel = true },
                enabled = installed,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Delete installed model")
            }

            val visionInstalled = visionModelState is ModelState.Available
            if (visionInstalled) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    "${ModelInfo.Vision.name} (photo solving) is installed" +
                        ((visionModelState as? ModelState.Available)?.let { " (${formatBytes(it.sizeBytes)})" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { confirmDeleteVisionModel = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Delete vision model")
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        SectionCard(title = "About") {
            Text(
                "LocalMathy v$appVersionName",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Offline, on-device math solving. Free software (AGPL-3.0).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "View the source code",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    uriHandler.openUri("https://github.com/micr0-dev/localmathy")
                },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Found a bug? Report an issue",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    uriHandler.openUri("https://github.com/micr0-dev/localmathy/issues")
                },
            )
        }
    }

    if (confirmClearHistory) {
        ConfirmDialog(
            title = "Clear history?",
            body = "This permanently deletes all $historyCount saved question(s) from this device.",
            confirmLabel = "Clear",
            onConfirm = {
                confirmClearHistory = false
                onClearHistory()
            },
            onDismiss = { confirmClearHistory = false },
        )
    }

    if (confirmDeleteModel) {
        ConfirmDialog(
            title = "Delete model?",
            body = "This removes the ${formatBytes(ModelInfo.Solver.approxSizeBytes)} model file. " +
                "You'll need to download or import it again to solve more questions.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDeleteModel = false
                onDeleteModel()
            },
            onDismiss = { confirmDeleteModel = false },
        )
    }

    if (confirmDeleteVisionModel) {
        ConfirmDialog(
            title = "Delete vision model?",
            body = "This removes the ${formatBytes(ModelInfo.Vision.approxSizeBytes)} ${ModelInfo.Vision.name} " +
                "file used for photo solving. Typed questions are unaffected; you can reinstall it later.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDeleteVisionModel = false
                onDeleteVisionModel()
            },
            onDismiss = { confirmDeleteVisionModel = false },
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DetailOption(
    detail: HistoryDetail,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 4.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(4.dp))
        Column {
            Text(detail.label, style = MaterialTheme.typography.bodyMedium)
            Text(
                detail.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
