package dev.micr0.localmathy

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun SolveScreen(
    engine: LlmEngine,
    state: SolveState,
    settings: AppSettings,
    imagePicker: ImagePicker?,
    visionModelInstalled: Boolean,
    onSetUpVisionModel: () -> Unit,
) {
    KeepScreenOnWhile(state.isRunning)

    // The backend is chosen in Settings; keep the engine in sync before a run.
    LaunchedEffect(settings.backend) { engine.backend = settings.backend }

    when {
        state.imagePhase == ImagePhase.Cropping && state.imageToCrop != null ->
            ImageCropView(
                imageBytes = state.imageToCrop!!,
                onCropped = state::onCropped,
                onCancel = state::cancelImage,
            )

        state.imagePhase == ImagePhase.Transcribing -> TranscribingView(state)

        state.phase == SolvePhase.Idle ->
            QuestionInput(state, imagePicker, visionModelInstalled, onSetUpVisionModel)

        else -> RunView(state)
    }
}

@Composable
private fun QuestionInput(
    state: SolveState,
    imagePicker: ImagePicker?,
    visionModelInstalled: Boolean,
    onSetUpVisionModel: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Ask a math question", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "One question, one answer — VibeThinker is single-turn, so follow-ups " +
                "aren't possible. Make the question self-contained.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.question,
            onValueChange = { state.question = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            placeholder = {
                Text(
                    "e.g. A farmer has chickens and cows. Together they have 35 heads " +
                        "and 94 legs. How many of each animal?",
                )
            },
        )

        if (state.transcriptionError.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                state.transcriptionError,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = state::submit,
            enabled = state.question.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Solve")
        }

        if (imagePicker?.isSupported == true) {
            Spacer(Modifier.height(20.dp))
            Text(
                "Or scan a problem",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        if (!visionModelInstalled) {
                            onSetUpVisionModel()
                        } else {
                            imagePicker.onImage = state::onImageCaptured
                            imagePicker.takePhoto()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Take photo")
                }
                OutlinedButton(
                    onClick = {
                        if (!visionModelInstalled) {
                            onSetUpVisionModel()
                        } else {
                            imagePicker.onImage = state::onImageCaptured
                            imagePicker.pickImage()
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Choose image")
                }
            }
            if (!visionModelInstalled) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Photo solving needs an extra vision model (${ModelInfo.Vision.name}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { uriHandler.openUri("https://ko-fi.com/micr0byte/") }
                .padding(vertical = 4.dp),
        ) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "If you like LocalMathy, consider donating!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun TranscribingView(state: SolveState) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                state.transcriptionDetail.ifEmpty { "Reading the problem…" },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(16.dp))
        if (state.question.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().weight(1f, fill = false)) {
                Text(
                    state.question,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState()),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
        OutlinedButton(onClick = state::cancelImage, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Close, contentDescription = null)
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Cancel")
        }
    }
}

@Composable
private fun RunView(state: SolveState) {
    val hasAnswer = state.answer.isNotEmpty()
    var thinkingExpanded by remember { mutableStateOf(true) }

    // Collapse the reasoning pane once the final answer starts streaming in.
    LaunchedEffect(state.thinkingDone) {
        if (state.thinkingDone) thinkingExpanded = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            state.question,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        if (state.phase == SolvePhase.LoadingModel) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "${state.loadingDetail.ifEmpty { "Loading model…" }} ${state.elapsedSeconds}s",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        if (state.thinking.isNotEmpty() || state.phase == SolvePhase.Generating) {
            ThinkingCard(
                state = state,
                expanded = thinkingExpanded,
                // Give the reasoning all remaining space until the answer arrives.
                fillsScreen = !hasAnswer,
                onToggle = { thinkingExpanded = !thinkingExpanded },
                modifier = if (!hasAnswer && thinkingExpanded) Modifier.weight(1f) else Modifier,
            )
            Spacer(Modifier.height(12.dp))
        }

        if (hasAnswer) {
            MarkdownLatexView(
                markdown = state.answer,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Spacer(Modifier.height(12.dp))
        } else if (!state.isRunning && state.phase != SolvePhase.Error) {
            Text(
                "The model produced no final answer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.height(12.dp))
        }

        if (state.phase == SolvePhase.Error) {
            Text(
                state.errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        if (state.isRunning) {
            OutlinedButton(onClick = state::stop, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Stop, contentDescription = null)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Stop")
            }
        } else {
            Button(onClick = state::reset, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("New question")
            }
        }
    }
}

@Composable
private fun ThinkingCard(
    state: SolveState,
    expanded: Boolean,
    fillsScreen: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().animateContentSize()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
            ) {
                if (!state.thinkingDone && state.isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    when {
                        !state.thinkingDone && state.isRunning -> "Thinking… ${state.elapsedSeconds}s"
                        else -> "Thought for ${state.elapsedSeconds}s"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Hide reasoning" else "Show reasoning",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            if (expanded && state.thinking.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                val scroll = rememberScrollState()
                // Keep the newest reasoning visible while it streams.
                LaunchedEffect(state.thinking.length) {
                    scroll.scrollTo(scroll.maxValue)
                }
                Box(
                    modifier = if (fillsScreen) {
                        Modifier.fillMaxWidth().weight(1f, fill = false)
                    } else {
                        Modifier.fillMaxWidth().heightIn(max = 160.dp)
                    },
                ) {
                    Text(
                        state.thinking,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.verticalScroll(scroll),
                    )
                }
            }
        }
    }
}
