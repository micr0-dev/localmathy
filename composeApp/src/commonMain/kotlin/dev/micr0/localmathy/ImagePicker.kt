package dev.micr0.localmathy

import androidx.compose.runtime.Composable

/**
 * Launches the platform camera / photo picker and delivers the chosen image
 * as encoded bytes. Android is backed by ActivityResult launchers; iOS is a
 * no-op stub (the photo feature is Android-only for now).
 */
interface ImagePicker {
    /** True when this platform can actually capture/pick an image. */
    val isSupported: Boolean

    /** Set by the consumer to receive the captured/picked bytes (null = cancelled). */
    var onImage: ((ByteArray?) -> Unit)?

    /** Launch the camera to take a new photo. */
    fun takePhoto()

    /** Launch the system photo picker (gallery). */
    fun pickImage()
}

/**
 * Shows [imageBytes] with a draggable crop box and returns the cropped region
 * as encoded JPEG bytes via [onCropped]. Android decodes/crops the bitmap;
 * iOS is a stub (never reached — no capture there).
 */
@Composable
expect fun ImageCropView(
    imageBytes: ByteArray,
    onCropped: (ByteArray) -> Unit,
    onCancel: () -> Unit,
)
