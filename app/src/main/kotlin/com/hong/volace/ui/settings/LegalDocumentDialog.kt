package com.hong.volace.ui.settings

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hong.volace.R

internal enum class LegalDocument(val title: Int, private vararg val files: String) {
    PRIVACY(R.string.settings_privacy, "PRIVACY.md"),
    LICENSES(R.string.settings_licenses, "LICENSE", "THIRD_PARTY_NOTICES.md");

    fun read(context: Context): String = files.joinToString("\n\n---\n\n") { file ->
        context.assets.open("legal/$file").bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}

/** Local, selectable documents. Dismissing the page returns to the settings menu. */
@Composable
internal fun LegalDocumentDialog(document: LegalDocument, onBack: () -> Unit) {
    val context = LocalContext.current
    val title = stringResource(document.title)
    val contentsLabel = stringResource(R.string.settings_document_contents)
    val configuration = LocalConfiguration.current
    val colors = MaterialTheme.colorScheme
    fun Color.css() = "#%06x".format(toArgb() and 0xffffff)
    val html = remember(document, context, title, contentsLabel, colors, configuration.locales) {
        legalHtmlPage(
            formatLegalDocument(document.read(context), contentsLabel), title,
            configuration.locales[0].toLanguageTag(), colors.surface.css(), colors.onSurface.css(),
            colors.onSurfaceVariant.css(), colors.primary.css(), colors.surfaceContainer.css(),
            colors.outlineVariant.css(),
        )
    }
    var scrollPosition by rememberSaveable(document) { mutableIntStateOf(0) }
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(document.title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onBack) { Text(stringResource(R.string.settings_document_back)) }
                }
                HorizontalDivider()
                AndroidView(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    factory = { viewContext ->
                        WebView(viewContext).apply {
                            setBackgroundColor(colors.surface.toArgb())
                            settings.apply {
                                javaScriptEnabled = false
                                blockNetworkLoads = true
                                allowFileAccess = false
                                allowContentAccess = false
                                builtInZoomControls = true
                                displayZoomControls = false
                            }
                            var ready = false
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                    val url = request.url
                                    // Only the document's own table-of-contents anchors can navigate.
                                    return url.scheme != "https" || url.host != "appassets.androidplatform.net" ||
                                        url.path != "/legal/" || url.fragment == null
                                }

                                override fun onPageFinished(view: WebView, url: String) {
                                    view.scrollTo(0, scrollPosition)
                                    ready = true
                                }
                            }
                            setOnScrollChangeListener { _, _, y, _, _ ->
                                if (ready) scrollPosition = y
                            }
                        }
                    },
                    onRelease = { it.destroy() },
                    update = { view ->
                        view.settings.textZoom = (configuration.fontScale * 100).toInt()
                        if (view.tag != html) {
                            view.tag = html
                            view.loadDataWithBaseURL("https://appassets.androidplatform.net/legal/", html, "text/html", "UTF-8", null)
                        }
                    },
                )
            }
        }
    }
}
