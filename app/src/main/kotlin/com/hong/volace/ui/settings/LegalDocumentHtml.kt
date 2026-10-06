package com.hong.volace.ui.settings

/** Renders the bundled documents' Markdown subset without accepting HTML or external resources. */
internal fun formatLegalDocument(source: String, contentsLabel: String = "目次"): String {
    val lines = source.lines()
    val body = StringBuilder()
    val contents = mutableListOf<Pair<String, String>>()
    var index = 0
    var headingCount = 0
    fun heading(level: Int, title: String) {
        val id = "section-${headingCount++}"
        if (level <= 2) contents += id to title
        body.append("<h$level id=\"$id\">${legalInlineHtml(title)}</h$level>")
    }
    fun isBoundary(line: String): Boolean {
        val text = line.trim()
        return text.isEmpty() || text.startsWith("```") || text.startsWith('|') ||
            text.startsWith("- ") || text == "---" || text.matches(Regex("=+")) ||
            text.matches(Regex("#{1,6} .*")) || line.matches(Regex("[0-9]+\\. .+")) ||
            text == "日本語 (正文)" || text == "English (reference translation)"
    }
    while (index < lines.size) {
        val line = lines[index]
        val text = line.trim()
        when {
            text.isEmpty() || text.matches(Regex("=+")) -> index++
            text.startsWith("```") -> {
                index++
                val code = StringBuilder()
                while (index < lines.size && !lines[index].trim().startsWith("```")) {
                    code.append(lines[index++]).append('\n')
                }
                if (index < lines.size) index++
                body.append("<pre><code>${escapeLegalHtml(code.toString())}</code></pre>")
            }
            text.startsWith('|') -> {
                val rows = mutableListOf<List<String>>()
                while (index < lines.size && lines[index].trim().startsWith('|')) {
                    val cells = lines[index++].trim().removePrefix("|").removeSuffix("|")
                        .split('|').map { it.trim() }
                    if (!cells.all { it.matches(Regex(":?-+:?")) }) rows += cells
                }
                val headers = rows.first()
                body.append("<table><thead><tr>")
                headers.forEach { body.append("<th scope=\"col\">${legalInlineHtml(it)}</th>") }
                body.append("</tr></thead><tbody>")
                rows.drop(1).forEach { cells ->
                    body.append("<tr>")
                    cells.forEachIndexed { cellIndex, cell ->
                        val label = escapeLegalHtml(headers.getOrElse(cellIndex) { "" })
                        body.append("<td data-label=\"$label\">${legalInlineHtml(cell)}</td>")
                    }
                    body.append("</tr>")
                }
                body.append("</tbody></table>")
            }
            text.matches(Regex("#{1,6} .*")) -> {
                val level = text.takeWhile { it == '#' }.length
                heading(level, text.drop(level).trim())
                index++
            }
            text.startsWith("Volace License（") -> { heading(1, text); index++ }
            text == "日本語 (正文)" || text == "English (reference translation)" -> {
                heading(2, text)
                index++
            }
            line.matches(Regex("[0-9]+\\. .+")) -> { heading(3, text); index++ }
            text == "---" -> { body.append("<hr>"); index++ }
            text.startsWith("- ") || text.matches(Regex("\\([a-z]\\) .*")) -> {
                body.append("<ul>")
                while (index < lines.size) {
                    val item = lines[index].trim()
                    if (!item.startsWith("- ") && !item.matches(Regex("\\([a-z]\\) .*"))) break
                    val paragraph = StringBuilder(if (item.startsWith("- ")) item.drop(2) else item)
                    index++
                    while (index < lines.size && lines[index].startsWith(' ') && !isBoundary(lines[index]) &&
                        !lines[index].trim().matches(Regex("\\([a-z]\\) .*"))) {
                        paragraph.append('\n').append(lines[index++].trim())
                    }
                    body.append("<li>${legalInlineHtml(paragraph.toString())}</li>")
                }
                body.append("</ul>")
            }
            else -> {
                val paragraph = StringBuilder(text)
                index++
                while (index < lines.size && !isBoundary(lines[index]) &&
                    !lines[index].trim().matches(Regex("\\([a-z]\\) .*"))) {
                    paragraph.append('\n').append(lines[index++].trim())
                }
                val paragraphHtml = legalInlineHtml(paragraph.toString())
                    .replace("\n最終更新:", "<br>最終更新:")
                    .replace("\nThe Japanese text is authoritative.", "<br>The Japanese text is authoritative.")
                body.append("<p>$paragraphHtml</p>")
            }
        }
    }
    val navigation = contents.joinToString("") { (id, title) ->
        "<li><a href=\"#$id\">${legalInlineHtml(title)}</a></li>"
    }
    return "<details class=\"contents\"><summary>${escapeLegalHtml(contentsLabel)}</summary>" +
        "<nav><ul>$navigation</ul></nav></details><article>$body</article>"
}

private val inlineTokens = Regex("`([^`]+)`|\\*\\*([^*]+)\\*\\*|\\[([^]]+)]\\(([^)]+)\\)|<(https?://[^>]+)>")

private fun legalInlineHtml(source: String): String = buildString {
    var end = 0
    for (match in inlineTokens.findAll(source)) {
        append(escapeLegalHtml(source.substring(end, match.range.first)))
        val groups = match.groupValues
        append(when {
            groups[1].isNotEmpty() -> "<code>${escapeLegalHtml(groups[1])}</code>"
            groups[2].isNotEmpty() -> "<strong>${legalInlineHtml(groups[2])}</strong>"
            groups[3].isNotEmpty() -> "${escapeLegalHtml(groups[3])} <span class=\"reference\">(${escapeLegalHtml(groups[4])})</span>"
            else -> "<span class=\"reference\">${escapeLegalHtml(groups[5])}</span>"
        })
        end = match.range.last + 1
    }
    append(escapeLegalHtml(source.substring(end)))
}

internal fun escapeLegalHtml(text: String): String = text.replace("&", "&amp;")
    .replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

/** The colours come from the active app skin; the page never loads fonts, scripts or stylesheets. */
internal fun legalHtmlPage(body: String, title: String, language: String, background: String,
    foreground: String, muted: String, accent: String, card: String, border: String,
): String = """
    <!doctype html><html lang="${escapeLegalHtml(language)}"><head>
    <meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'">
    <title>${escapeLegalHtml(title)}</title><style>
    * { box-sizing: border-box; }
    html { background: $background; color: $foreground; }
    body { margin: 0 auto; max-width: 800px; padding: 20px 22px 48px;
      font-family: sans-serif; font-size: 16px; line-height: 1.85; overflow-wrap: anywhere; }
    h1, h2, h3, h4, h5, h6 { line-height: 1.5; font-weight: 700; scroll-margin-top: 20px; }
    h1 { font-size: 1.5em; margin: 28px 0 16px; }
    h2 { font-size: 1.25em; margin: 36px 0 18px; padding-bottom: 10px; color: $accent; border-bottom: 1px solid $border; }
    h3 { font-size: 1.1em; margin: 26px 0 12px; }
    p { margin: 0 0 18px; }
    ul { padding-left: 1.4em; margin: 12px 0 24px; } li { margin-bottom: 12px; }
    strong { font-weight: 700; color: $accent; }
    code { font-family: monospace; font-size: .88em; padding: 2px 5px; border-radius: 5px; background: $card; }
    pre { margin: 20px 0; padding: 18px; border: 1px solid $border; border-radius: 12px;
      background: $card; font-family: sans-serif; white-space: pre-line; overflow-wrap: anywhere; line-height: 1.75; }
    pre code { padding: 0; background: transparent; font-family: inherit; font-size: .9em; }
    hr { border: 0; border-top: 1px solid $border; margin: 36px 0; }
    .reference { color: $muted; font-size: .9em; }
    .contents { border: 1px solid $border; border-radius: 12px; background: $card; padding: 12px 16px; }
    summary { color: $accent; font-weight: 700; cursor: pointer; }
    .contents ul { margin-bottom: 0; } .contents li { margin-bottom: 8px; }
    a { color: $accent; text-underline-offset: 3px; }
    table { width: 100%; border-collapse: collapse; margin: 20px 0 28px; font-size: .95em; }
    th, td { padding: 12px; text-align: left; vertical-align: top; border: 1px solid $border; }
    th { background: $card; font-weight: 700; }
    @media (max-width: 560px) {
      table, tbody, tr, td { display: block; } thead { display: none; }
      tr { border: 1px solid $border; border-radius: 12px; margin-bottom: 12px; overflow: hidden; background: $card; }
      td { border: 0; padding: 10px 14px; } td + td { border-top: 1px solid $border; }
      td::before { content: attr(data-label); display: block; color: $accent; font-weight: 700; font-size: .88em; margin-bottom: 3px; }
    }
    </style></head><body>$body</body></html>
""".trimIndent()
