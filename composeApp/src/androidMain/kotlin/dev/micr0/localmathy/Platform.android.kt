package dev.micr0.localmathy

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual val appVersionName: String = BuildConfig.VERSION_NAME

@Composable
actual fun AppLogo(modifier: Modifier) {
    Image(
        painter = painterResource(R.drawable.app_logo),
        contentDescription = "LocalMathy logo",
        modifier = modifier,
    )
}

actual fun formatTimestamp(epochMillis: Long): String =
    SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(epochMillis))

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
