package dev.micr0.localmathy

import kotlinx.coroutines.flow.StateFlow

sealed interface ModelState {
    data object Checking : ModelState

    /** No model on disk yet; the user must download or import it. */
    data object Missing : ModelState

    data class Downloading(val bytesDone: Long, val bytesTotal: Long?) : ModelState

    data class Importing(val bytesDone: Long, val bytesTotal: Long?) : ModelState

    data class Available(val path: String, val sizeBytes: Long) : ModelState

    data class Error(val message: String) : ModelState
}

interface ModelManager {
    /** Which model this manager is responsible for. */
    val spec: ModelSpec

    val state: StateFlow<ModelState>

    /** Downloads the model straight from Hugging Face (resumable). */
    fun startDownload()

    fun cancelTransfer()

    /** Opens a platform file picker to import an already-downloaded .litertlm file. */
    fun requestImport()

    /** Deletes the installed model file from the device. */
    fun deleteModel()
}

/**
 * Describes one on-device model file: where it lives on disk, where to fetch
 * it, and roughly how big it is. [id] is a stable key used for the SAF import
 * routing so two managers can tell their pick results apart.
 */
data class ModelSpec(
    val id: String,
    val name: String,
    val fileName: String,
    val downloadUrl: String,
    val approxSizeBytes: Long,
)

object ModelInfo {
    /** VibeThinker-3B: the text-only reasoning model that actually solves problems. */
    val Solver = ModelSpec(
        id = "solver",
        name = "VibeThinker-3B",
        fileName = "VibeThinker-3B.litertlm",
        downloadUrl = "https://huggingface.co/litert-community/VibeThinker-3B/resolve/main/model.litertlm",
        approxSizeBytes = 1_900_000_000L,
    )

    /** Gemma 4 E2B: the optional multimodal model used only to read a photo into text. */
    val Vision = ModelSpec(
        id = "vision",
        name = "Gemma 4 E2B",
        fileName = "gemma-4-E2B-it.litertlm",
        downloadUrl =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        approxSizeBytes = 2_580_000_000L,
    )
}
