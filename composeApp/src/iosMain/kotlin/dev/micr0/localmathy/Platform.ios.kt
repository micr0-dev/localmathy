package dev.micr0.localmathy

import androidx.compose.runtime.Composable
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970

actual val appVersionName: String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""

@OptIn(ExperimentalForeignApi::class)
actual fun formatTimestamp(epochMillis: Long): String {
    val formatter = NSDateFormatter().apply {
        dateFormat = "MMM d, yyyy HH:mm"
    }
    return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0)) ?: ""
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS has no system back gesture to intercept; navigation is in-app only.
}

@Composable
actual fun ImageCropView(
    imageBytes: ByteArray,
    onCropped: (ByteArray) -> Unit,
    onCancel: () -> Unit,
) {
    // Never reached on iOS: the photo feature is Android-only (ImagePicker is null there).
}

@Composable
actual fun AppLogo(modifier: Modifier) {
    // No bundled asset on iOS (stub target).
}