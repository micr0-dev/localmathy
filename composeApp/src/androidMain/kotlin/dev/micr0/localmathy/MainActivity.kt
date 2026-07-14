package dev.micr0.localmathy

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : ComponentActivity() {

    private val app get() = application as LocalMathyApp

    /** Set right before launching the model picker so the result routes to the right model. */
    private var pendingImport: ((Uri) -> Unit)? = null

    /** The file Uri the camera app is writing the current capture to. */
    private var cameraOutputUri: Uri? = null

    private val importModelLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { pendingImport?.invoke(it) }
        }

    private val takePhotoLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val uri = cameraOutputUri
            app.imagePicker.deliver(if (success && uri != null) readBytes(uri) else null)
        }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            app.imagePicker.deliver(uri?.let { readBytes(it) })
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // .litertlm has no registered MIME type, so accept anything. Each manager's
        // picker records itself as the pending target so the result lands in the right model.
        for (manager in listOf(app.modelManager, app.visionModelManager)) {
            manager.filePickerLauncher = {
                pendingImport = manager::importFrom
                importModelLauncher.launch(arrayOf("*/*"))
            }
        }

        app.imagePicker.takePhotoLauncher = { launchCamera() }
        app.imagePicker.pickImageLauncher = {
            pickImageLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
        requestNotificationPermissionIfNeeded()

        setContent {
            App(
                modelManager = app.modelManager,
                visionModelManager = app.visionModelManager,
                engine = app.engine,
                transcriber = app.transcriber,
                imagePicker = app.imagePicker,
                solveState = app.solveState,
                settings = app.settings,
                historyStore = app.historyStore,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        AppForeground.isForeground = true
        Notifications.cancelResult(this)
    }

    override fun onStop() {
        AppForeground.isForeground = false
        super.onStop()
    }

    override fun onDestroy() {
        app.modelManager.filePickerLauncher = null
        app.visionModelManager.filePickerLauncher = null
        app.imagePicker.takePhotoLauncher = null
        app.imagePicker.pickImageLauncher = null
        super.onDestroy()
    }

    private fun launchCamera() {
        val dir = File(cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        cameraOutputUri = uri
        takePhotoLauncher.launch(uri)
    }

    private fun readBytes(uri: Uri): ByteArray? =
        try {
            contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: Exception) {
            null
        }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
