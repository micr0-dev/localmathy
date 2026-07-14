package dev.micr0.localmathy

import androidx.compose.runtime.Composable

/** Keeps the screen awake while [active] and this composable is on screen. */
@Composable
expect fun KeepScreenOnWhile(active: Boolean)
