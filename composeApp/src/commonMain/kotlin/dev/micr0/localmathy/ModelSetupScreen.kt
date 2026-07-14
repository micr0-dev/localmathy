package dev.micr0.localmathy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun ModelSetupScreen(modelManager: ModelManager, state: ModelState, intro: String? = null) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Get the model", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(
            intro ?: (
                "LocalMathy solves math fully offline using ${modelManager.spec.name} (LiteRT). " +
                    "The model file is about ${formatBytes(modelManager.spec.approxSizeBytes)} and only " +
                    "needs to be set up once."
                ),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        when (state) {
            is ModelState.Checking -> CircularProgressIndicator()

            is ModelState.Downloading -> TransferProgress(
                label = "Downloading model…",
                bytesDone = state.bytesDone,
                bytesTotal = state.bytesTotal,
                onCancel = modelManager::cancelTransfer,
            )

            is ModelState.Importing -> TransferProgress(
                label = "Importing model…",
                bytesDone = state.bytesDone,
                bytesTotal = state.bytesTotal,
                onCancel = modelManager::cancelTransfer,
            )

            is ModelState.Missing, is ModelState.Error -> {
                if (state is ModelState.Error) {
                    Text(
                        state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                }
                Button(onClick = modelManager::startDownload, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Download from Hugging Face")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = modelManager::requestImport, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FileOpen, contentDescription = null)
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Import a .litertlm file")
                }
            }

            is ModelState.Available -> Unit // parent switches screens
        }
    }
}

@Composable
private fun TransferProgress(
    label: String,
    bytesDone: Long,
    bytesTotal: Long?,
    onCancel: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        if (bytesTotal != null && bytesTotal > 0) {
            LinearProgressIndicator(
                progress = { (bytesDone.toFloat() / bytesTotal).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${formatBytes(bytesDone)} / ${formatBytes(bytesTotal)}",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text(formatBytes(bytesDone), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onCancel) {
            Icon(Icons.Default.Close, contentDescription = null)
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Cancel")
        }
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "${((bytes / 100_000_000.0).roundToInt() / 10.0)} GB"
    bytes >= 1_000_000 -> "${(bytes / 1_000_000.0).roundToInt()} MB"
    bytes >= 1_000 -> "${(bytes / 1_000.0).roundToInt()} kB"
    else -> "$bytes B"
}
