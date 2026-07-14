package dev.micr0.localmathy

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** App-scoped bridge to the activity's camera / photo-picker launchers. */
class AndroidImagePicker : ImagePicker {
    var takePhotoLauncher: (() -> Unit)? = null
    var pickImageLauncher: (() -> Unit)? = null

    override val isSupported: Boolean get() = takePhotoLauncher != null || pickImageLauncher != null
    override var onImage: ((ByteArray?) -> Unit)? = null

    override fun takePhoto() {
        takePhotoLauncher?.invoke()
    }

    override fun pickImage() {
        pickImageLauncher?.invoke()
    }

    /** Called by the activity with the launcher result (null = cancelled/failed). */
    fun deliver(bytes: ByteArray?) {
        val cb = onImage
        onImage = null
        cb?.invoke(bytes)
    }
}

private enum class Handle { TopLeft, TopRight, BottomLeft, BottomRight, Move, None }

private data class Crop(val left: Float, val top: Float, val right: Float, val bottom: Float)

@Composable
actual fun ImageCropView(
    imageBytes: ByteArray,
    onCropped: (ByteArray) -> Unit,
    onCancel: () -> Unit,
) {
    val bitmap = remember(imageBytes) { decodeOriented(imageBytes) }
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    var crop by remember(imageBytes) { mutableStateOf(Crop(0.08f, 0.08f, 0.92f, 0.92f)) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Drag the box to frame one problem", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Crop tightly to a single equation or question for the best reading accuracy.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            val cw = constraints.maxWidth.toFloat()
            val ch = constraints.maxHeight.toFloat()
            val dw: Float
            val dh: Float
            if (cw / ch > ratio) {
                dh = ch; dw = ch * ratio
            } else {
                dw = cw; dh = cw / ratio
            }
            val density = LocalDensity.current
            Box(
                modifier = Modifier.size(
                    with(density) { dw.toDp() },
                    with(density) { dh.toDp() },
                ),
            ) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Captured problem",
                    modifier = Modifier.fillMaxSize(),
                )
                var active by remember { mutableStateOf(Handle.None) }
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(dw, dh) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    active = pickHandle(
                                        crop,
                                        pos.x / size.width,
                                        pos.y / size.height,
                                        48f / size.width,
                                        48f / size.height,
                                    )
                                },
                                onDragEnd = { active = Handle.None },
                                onDragCancel = { active = Handle.None },
                                onDrag = { change, drag ->
                                    change.consume()
                                    crop = applyDrag(crop, active, drag.x / size.width, drag.y / size.height)
                                },
                            )
                        },
                ) {
                    drawCrop(crop)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Cancel")
            }
            Button(
                onClick = { onCropped(cropToJpeg(bitmap, crop)) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Use crop")
            }
        }
    }
}

private fun DrawScope.drawCrop(crop: Crop) {
    val w = size.width
    val h = size.height
    val l = crop.left * w
    val t = crop.top * h
    val r = crop.right * w
    val b = crop.bottom * h
    val dim = Color.Black.copy(alpha = 0.5f)
    // Dim everything outside the crop rectangle.
    drawRect(dim, topLeft = Offset(0f, 0f), size = Size(w, t))
    drawRect(dim, topLeft = Offset(0f, b), size = Size(w, h - b))
    drawRect(dim, topLeft = Offset(0f, t), size = Size(l, b - t))
    drawRect(dim, topLeft = Offset(r, t), size = Size(w - r, b - t))
    // Border + corner handles.
    drawRect(Color.White, topLeft = Offset(l, t), size = Size(r - l, b - t), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
    val hs = 22f
    for (corner in listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b))) {
        drawRect(Color.White, topLeft = Offset(corner.x - hs / 2, corner.y - hs / 2), size = Size(hs, hs))
    }
}

private fun pickHandle(crop: Crop, fx: Float, fy: Float, tx: Float, ty: Float): Handle {
    fun near(cx: Float, cy: Float) = kotlin.math.abs(fx - cx) <= tx && kotlin.math.abs(fy - cy) <= ty
    return when {
        near(crop.left, crop.top) -> Handle.TopLeft
        near(crop.right, crop.top) -> Handle.TopRight
        near(crop.left, crop.bottom) -> Handle.BottomLeft
        near(crop.right, crop.bottom) -> Handle.BottomRight
        fx in crop.left..crop.right && fy in crop.top..crop.bottom -> Handle.Move
        else -> Handle.None
    }
}

private fun applyDrag(crop: Crop, handle: Handle, dx: Float, dy: Float): Crop {
    val minSize = 0.08f
    return when (handle) {
        Handle.TopLeft -> crop.copy(
            left = (crop.left + dx).coerceIn(0f, crop.right - minSize),
            top = (crop.top + dy).coerceIn(0f, crop.bottom - minSize),
        )
        Handle.TopRight -> crop.copy(
            right = (crop.right + dx).coerceIn(crop.left + minSize, 1f),
            top = (crop.top + dy).coerceIn(0f, crop.bottom - minSize),
        )
        Handle.BottomLeft -> crop.copy(
            left = (crop.left + dx).coerceIn(0f, crop.right - minSize),
            bottom = (crop.bottom + dy).coerceIn(crop.top + minSize, 1f),
        )
        Handle.BottomRight -> crop.copy(
            right = (crop.right + dx).coerceIn(crop.left + minSize, 1f),
            bottom = (crop.bottom + dy).coerceIn(crop.top + minSize, 1f),
        )
        Handle.Move -> {
            val ndx = dx.coerceIn(-crop.left, 1f - crop.right)
            val ndy = dy.coerceIn(-crop.top, 1f - crop.bottom)
            crop.copy(
                left = crop.left + ndx, right = crop.right + ndx,
                top = crop.top + ndy, bottom = crop.bottom + ndy,
            )
        }
        Handle.None -> crop
    }
}

/** Decodes bytes to a right-side-up bitmap, downscaled so the max side is <= [maxDim]. */
private fun decodeOriented(bytes: ByteArray, maxDim: Int = 1536): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    val maxSrc = maxOf(bounds.outWidth, bounds.outHeight)
    while (maxSrc / sample > maxDim) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        ?: error("Could not read the image")

    val orientation = try {
        ExifInterface(ByteArrayInputStream(bytes))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
    }
    if (!matrix.isIdentity) {
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    }
    return bmp
}

private fun cropToJpeg(bmp: Bitmap, crop: Crop): ByteArray {
    val left = (crop.left * bmp.width).toInt().coerceIn(0, bmp.width - 1)
    val top = (crop.top * bmp.height).toInt().coerceIn(0, bmp.height - 1)
    val right = (crop.right * bmp.width).toInt().coerceIn(left + 1, bmp.width)
    val bottom = (crop.bottom * bmp.height).toInt().coerceIn(top + 1, bmp.height)
    val cropped = Bitmap.createBitmap(bmp, left, top, right - left, bottom - top)
    return ByteArrayOutputStream().use { out ->
        cropped.compress(Bitmap.CompressFormat.JPEG, 92, out)
        out.toByteArray()
    }
}
