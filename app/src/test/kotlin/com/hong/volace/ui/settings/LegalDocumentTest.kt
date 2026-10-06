package com.hong.volace.ui.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LegalDocumentTest {
    @Test
    fun bundledDocuments_includePrivacyAndBothLicensesInFull() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        fun asset(name: String) = context.assets.open("legal/$name")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }

        assertEquals(asset("PRIVACY.md"), LegalDocument.PRIVACY.read(context))
        assertEquals(
            asset("LICENSE") + "\n\n---\n\n" + asset("THIRD_PARTY_NOTICES.md"),
            LegalDocument.LICENSES.read(context),
        )
        val displayed = formatLegalDocument(LegalDocument.LICENSES.read(context))
        assertTrue(displayed.contains("Volace License (personal use only)"))
        assertTrue(displayed.contains("END OF TERMS AND CONDITIONS"))
        assertTrue(displayed.contains("limitations under the License."))
    }

    @Test
    fun tables_keepTheirHeadersAndAllCellContentsOnNarrowScreens() {
        val source = """
            | 権限 | 目的 |
            |---|---|
            | 通知 (`POST_NOTIFICATIONS`) | 時間指定の表示 |
            | Bluetooth | 接続を知る |
        """.trimIndent()
        val displayed = formatLegalDocument(source)
        assertTrue(displayed.contains("<table><thead><tr><th scope=\"col\">権限</th>"))
        assertTrue(displayed.contains("<td data-label=\"権限\">通知 (<code>POST_NOTIFICATIONS</code>)</td>"))
        assertTrue(displayed.contains("<td data-label=\"目的\">時間指定の表示</td>"))
        assertTrue(displayed.contains("<td data-label=\"権限\">Bluetooth</td>"))
        assertTrue(displayed.contains("<td data-label=\"目的\">接続を知る</td>"))
    }

    @Test
    fun links_keepDestinationsAndFencedLicenseTermsKeepExactText() {
        val clause = "   Copyright **owner** `name` [reference](url)"
        val source = "[連絡先](https://example.com/issues)\n```text\n$clause\n```"
        val displayed = formatLegalDocument(source)
        assertTrue(displayed.contains("連絡先 <span class=\"reference\">(https://example.com/issues)</span>"))
        assertTrue(displayed.contains("<pre><code>$clause\n</code></pre>"))
        assertTrue(!displayed.contains("href=\"https://"))
    }

    @Test
    fun headingsAndEmphasis_haveSemanticHtmlAndLocalContentsLinks() {
        val displayed = formatLegalDocument("# Policy\n\n## 日本語\n\n**重要**な説明と `INTERNET`\n\n- 項目")
        assertTrue(displayed.contains("<h1 id=\"section-0\">Policy</h1>"))
        assertTrue(displayed.contains("<a href=\"#section-1\">日本語</a>"))
        assertTrue(displayed.contains("<p><strong>重要</strong>な説明と <code>INTERNET</code></p>"))
        assertTrue(displayed.contains("<ul><li>項目</li></ul>"))
    }

    @Test
    fun embeddedHtmlAndAttributeCharacters_areEscaped() {
        val displayed = formatLegalDocument("<script>alert(1)</script>\n\n`<img src=\"https://example.com\">`\n\n| A\" | B |\n|---|---|\n| value | text |")
        assertTrue(displayed.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertTrue(displayed.contains("<code>&lt;img src=&quot;https://example.com&quot;&gt;</code>"))
        assertTrue(displayed.contains("data-label=\"A&quot;\""))
        assertTrue(!displayed.contains("<script>"))
        assertTrue(!displayed.contains("<img "))
    }
}
