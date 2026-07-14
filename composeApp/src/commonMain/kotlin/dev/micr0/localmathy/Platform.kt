package dev.micr0.localmathy

import androidx.compose.runtime.Composable

/** The app's user-facing version name (e.g. "1.0"), from the build config. */
expect val appVersionName: String

/** "Jun 3, 2026 14:05"-style local timestamp for history entries. */
expect fun formatTimestamp(epochMillis: Long): String

/** The rounded LocalMathy logo. No-op on platforms without the asset. */
@Composable
expect fun AppLogo(modifier: Modifier = Modifier)

/**
 * Intercepts the platform back gesture while [enabled]. Android maps this to
 * androidx BackHandler; iOS has no system back gesture, so it's a no-op there.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
