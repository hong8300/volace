package com.hong.volace.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val text = remember(document, context) { formatLegalDocument(document.read(context)) }
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
                SelectionContainer(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                    )
                }
            }
        }
    }
}

/** The documents' small Markdown subset; tables become labelled rows to fit narrow screens. */
internal fun formatLegalDocument(source: String): AnnotatedString = buildAnnotatedString {
    var inCode = false
    var tableHeaders: List<String>? = null
    for (line in source.lines()) {
        val trimmed = line.trim()
        when {
            trimmed.startsWith("```") -> inCode = !inCode
            inCode -> append("$line\n") // Keep the third-party license text verbatim.
            trimmed.startsWith("|") -> {
                val cells = trimmed.removePrefix("|").removeSuffix("|").split('|').map { it.trim() }
                if (tableHeaders == null) {
                    tableHeaders = cells
                } else if (!cells.all { it.matches(Regex(":?-+:?")) }) {
                    cells.forEachIndexed { index, cell ->
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                            append("${legalInlineText(tableHeaders.getOrElse(index) { "" })}: ")
                        }
                        append("${legalInlineText(cell)}\n")
                    }
                    append('\n')
                }
            }
            else -> {
                tableHeaders = null
                when {
                    trimmed == "---" -> append("\n────────\n\n")
                    trimmed.startsWith("#") -> {
                        val level = trimmed.takeWhile { it == '#' }.length
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = (24 - level * 2).sp)) {
                            append(legalInlineText(trimmed.drop(level).trim()))
                        }
                        append('\n')
                    }
                    trimmed.startsWith("- ") -> append("• ${legalInlineText(trimmed.drop(2))}\n")
                    else -> append("${legalInlineText(line)}\n")
                }
            }
        }
    }
}

// Keep link destinations visible and selectable without launching a browser.
private fun legalInlineText(text: String): String = text
    .replace(Regex("\\[([^]]+)]\\(([^)]+)\\)"), "$1 ($2)")
    .replace("**", "")
    .replace("`", "")
