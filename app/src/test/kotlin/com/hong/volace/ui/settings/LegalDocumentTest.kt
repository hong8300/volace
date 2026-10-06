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
        val displayed = formatLegalDocument(LegalDocument.LICENSES.read(context)).text
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
        val displayed = formatLegalDocument(source).text
        assertEquals(
            "権限: 通知 (POST_NOTIFICATIONS)\n目的: 時間指定の表示\n\n" +
                "権限: Bluetooth\n目的: 接続を知る\n\n",
            displayed,
        )
    }

    @Test
    fun links_keepDestinationsAndFencedLicenseTermsKeepExactText() {
        val clause = "   Copyright **owner** `name` [reference](url)"
        val source = "[連絡先](https://example.com/issues)\n```text\n$clause\n```"
        assertEquals(
            "連絡先 (https://example.com/issues)\n$clause\n",
            formatLegalDocument(source).text,
        )
    }
}
