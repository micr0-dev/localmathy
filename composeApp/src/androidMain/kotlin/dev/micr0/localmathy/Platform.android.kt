package dev.micr0.localmathy

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual val appVersionName: String = BuildConfig.VERSION_NAME

actual fun formatTimestamp(epochMillis: Long): String =
    SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(epochMillis))

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
