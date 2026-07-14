package dev.micr0.localmathy

import androidx.compose.runtime.Composable

/** No-op for now; iOS will use UIApplication.idleTimerDisabled when wired up. */
@Composable
actual fun KeepScreenOnWhile(active: Boolean) = Unit
