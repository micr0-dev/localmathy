package dev.micr0.localmathy

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

private class RenderHolder {
    var pageLoaded = false
    var pendingMarkdown: String? = null
    var dark = false
}

/** Exposed to the page as `window.LocalMathy`; used by tap-to-copy on \boxed answers. */
private class ClipboardBridge(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun copy(text: String) {
        mainHandler.post {
            // Android 13+ shows its own "copied" confirmation; no app toast needed.
            context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("LocalMathy answer", text))
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun MarkdownLatexView(markdown: String, modifier: Modifier) {
    // The WebView is transparent, so text sits on the Compose surface. The
    // WebView's own prefers-color-scheme doesn't reliably follow the app theme,
    // so drive the page's colors from the theme Compose actually resolved.
    val dark = isSystemInDarkTheme()
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val holder = RenderHolder().apply { this.dark = dark }
            WebView(ctx).apply {
                tag = holder
                settings.javaScriptEnabled = true
                addJavascriptInterface(ClipboardBridge(ctx.applicationContext), "LocalMathy")
                setBackgroundColor(Color.TRANSPARENT)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        holder.pageLoaded = true
                        applyTheme(view, holder.dark)
                        holder.pendingMarkdown?.let { push(view, it) }
                        holder.pendingMarkdown = null
                    }
                }
                loadUrl("file:///android_asset/render/render.html")
            }
        },
        update = { webView ->
            val holder = webView.tag as RenderHolder
            holder.dark = dark
            if (holder.pageLoaded) {
                applyTheme(webView, dark)
                push(webView, markdown)
            } else {
                holder.pendingMarkdown = markdown
            }
        },
    )
}

private fun applyTheme(webView: WebView, dark: Boolean) {
    webView.evaluateJavascript("setTheme($dark);", null)
}

private fun push(webView: WebView, markdown: String) {
    // Base64 round-trip avoids every JS string-escaping pitfall.
    val b64 = Base64.encodeToString(markdown.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    webView.evaluateJavascript("setContentB64('$b64');", null)
}
