package dev.micr0.localmathy

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Renders markdown with LaTeX math ($...$, $$...$$, \(...\), \[...\]).
 * The Android implementation uses a WebView with bundled marked + KaTeX,
 * fully offline. The view scrolls its own content.
 */
@Composable
expect fun MarkdownLatexView(markdown: String, modifier: Modifier = Modifier)
