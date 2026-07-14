package dev.micr0.localmathy

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Stores the model under the app's *internal* files dir. Internal storage is
 * plain ext4 (same partition termux uses), which the GPU backend needs to
 * mmap the 1.9 GB weights quickly - the external app dir goes through the
 * FUSE-emulated storage layer and stalls GPU weight loading.
 *
 * Filled either by downloading from Hugging Face (resumable) or importing a
 * local file via SAF.
 */
class AndroidModelManager(
    private val context: Context,
    override val spec: ModelSpec,
) : ModelManager {

    /** Set by the current activity; SAF pickers can only launch from one. */
    var filePickerLauncher: (() -> Unit)? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val modelDir = File(context.filesDir, "models")
    val modelFile = File(modelDir, spec.fileName)
    private val partFile = File(modelDir, spec.fileName + ".part")

    /** Where builds before the internal-storage move kept the model. */
    private val legacyModelFile =
        File(File(context.getExternalFilesDir(null) ?: context.filesDir, "models"), spec.fileName)

    private val _state = MutableStateFlow<ModelState>(ModelState.Checking)
    override val state: StateFlow<ModelState> = _state

    private var transferJob: Job? = null

    init {
        // Only the original solver model ever lived in the legacy external dir.
        if (spec.id == ModelInfo.Solver.id && !modelFile.exists() && legacyModelFile.exists()) {
            migrateFromLegacyLocation()
        } else {
            refresh()
        }
    }

    fun refresh() {
        _state.value = if (modelFile.exists() && modelFile.length() > 0) {
            ModelState.Available(modelFile.absolutePath, modelFile.length())
        } else {
            ModelState.Missing
        }
    }

    override fun startDownload() {
        if (transferJob?.isActive == true) return
        transferJob = scope.launch(Dispatchers.IO) {
            try {
                download()
                refresh()
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                _state.value = ModelState.Error("Download failed: ${e.message}")
            }
        }
    }

    override fun cancelTransfer() {
        transferJob?.cancel()
        transferJob = null
        refresh()
    }

    override fun requestImport() {
        filePickerLauncher?.invoke()
    }

    override fun deleteModel() {
        transferJob?.cancel()
        transferJob = null
        modelFile.delete()
        partFile.delete()
        refresh()
    }

    /** Moves a model downloaded by an older build from external to internal storage. */
    private fun migrateFromLegacyLocation() {
        transferJob = scope.launch(Dispatchers.IO) {
            try {
                val total = legacyModelFile.length()
                if (modelDir.usableSpace < total + SPACE_MARGIN_BYTES) {
                    error(
                        "Not enough internal storage to move the model " +
                            "(${formatBytes(total)} needed). Free up space and restart the app.",
                    )
                }
                _state.value = ModelState.Importing(0, total)
                legacyModelFile.inputStream().use { input ->
                    copyToPartFile(input, total) { done ->
                        _state.value = ModelState.Importing(done, total)
                    }
                }
                promotePartFile()
                legacyModelFile.delete()
                refresh()
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                partFile.delete()
                _state.value = ModelState.Error("Moving model to internal storage failed: ${e.message}")
            }
        }
    }

    /** Called by the activity with the SAF pick result. */
    fun importFrom(uri: Uri) {
        if (transferJob?.isActive == true) return
        transferJob = scope.launch(Dispatchers.IO) {
            try {
                val total = querySize(uri)
                _state.value = ModelState.Importing(0, total)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    copyToPartFile(input, total) { done ->
                        _state.value = ModelState.Importing(done, total)
                    }
                } ?: error("Could not open the selected file")
                promotePartFile()
                refresh()
            } catch (e: Exception) {
                coroutineContext.ensureActive()
                partFile.delete()
                _state.value = ModelState.Error("Import failed: ${e.message}")
            }
        }
    }

    private suspend fun download() {
        modelDir.mkdirs()
        val alreadyHave = if (partFile.exists()) partFile.length() else 0L

        val connection = URL(spec.downloadUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 30_000
        if (alreadyHave > 0) {
            connection.setRequestProperty("Range", "bytes=$alreadyHave-")
        }
        connection.connect()

        val code = connection.responseCode
        val resuming = code == HttpURLConnection.HTTP_PARTIAL
        if (code != HttpURLConnection.HTTP_OK && !resuming) {
            error("HTTP $code from Hugging Face")
        }

        val startAt = if (resuming) alreadyHave else 0L
        val remaining = connection.contentLengthLong.takeIf { it > 0 }
        val total = remaining?.plus(startAt)
        _state.value = ModelState.Downloading(startAt, total)

        connection.inputStream.use { input ->
            copyStream(
                input = input,
                output = java.io.FileOutputStream(partFile, resuming),
                alreadyDone = startAt,
            ) { done ->
                _state.value = ModelState.Downloading(done, total)
            }
        }
        promotePartFile()
    }

    private suspend fun copyToPartFile(
        input: InputStream,
        total: Long?,
        onProgress: (Long) -> Unit,
    ) {
        modelDir.mkdirs()
        copyStream(input, java.io.FileOutputStream(partFile, false), 0L, onProgress)
    }

    private suspend fun copyStream(
        input: InputStream,
        output: OutputStream,
        alreadyDone: Long,
        onProgress: (Long) -> Unit,
    ) {
        output.use { out ->
            val buffer = ByteArray(256 * 1024)
            var done = alreadyDone
            var lastReport = 0L
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                done += read
                // Throttle state updates to roughly every 8 MB.
                if (done - lastReport > 8 * 1024 * 1024) {
                    lastReport = done
                    onProgress(done)
                }
            }
            out.flush()
        }
    }

    private fun promotePartFile() {
        if (!partFile.renameTo(modelFile)) {
            partFile.copyTo(modelFile, overwrite = true)
            partFile.delete()
        }
    }

    private companion object {
        const val SPACE_MARGIN_BYTES = 500L * 1024 * 1024
    }

    private fun querySize(uri: Uri): Long? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
}
