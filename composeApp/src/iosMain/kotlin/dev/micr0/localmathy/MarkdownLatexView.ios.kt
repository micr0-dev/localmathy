package dev.micr0.localmathy

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Placeholder: shows the raw markdown. A WKWebView-based renderer using the
 * same bundled marked + KaTeX assets will replace this when iOS is wired up.
 */
@Composable
actual fun MarkdownLatexView(markdown: String, modifier: Modifier) {
    Text(markdown, modifier = modifier.verticalScroll(rememberScrollState()))
}
