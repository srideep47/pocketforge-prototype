package com.srideep.pocketforge.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EditArtifactTest {

    private val page = """
        <html>
          <body>
            <button class="go" style="background: blue">Go</button>
            <p>Hello</p>
          </body>
        </html>
    """.trimIndent()

    @Test
    fun `exact find is replaced`() {
        val edits = EditArtifact.extract(
            "Sure.\n<edit>\n<find>background: blue</find>\n<replace>background: orange</replace>\n</edit>",
        )
        val outcome = EditArtifact.apply(page, edits) as EditArtifact.Outcome.Applied
        assertTrue(outcome.html.contains("background: orange"))
        assertEquals(1, outcome.count)
    }

    @Test
    fun `re-indented multi-line find still matches`() {
        val edits = EditArtifact.extract(
            "<edit><find>\n<button class=\"go\" style=\"background: blue\">Go</button>\n<p>Hello</p>\n</find>" +
                "<replace>    <p>Bye</p></replace></edit>",
        )
        val outcome = EditArtifact.apply(page, edits) as EditArtifact.Outcome.Applied
        assertTrue(outcome.html.contains("<p>Bye</p>"))
        assertTrue(!outcome.html.contains("Go</button>"))
    }

    @Test
    fun `nothing is applied when one find misses`() {
        val edits = EditArtifact.extract(
            "<edit><find>Hello</find><replace>Hi</replace></edit>" +
                "<edit><find>not in the page</find><replace>x</replace></edit>",
        )
        val outcome = EditArtifact.apply(page, edits)
        assertEquals(EditArtifact.Outcome.Missed(listOf("not in the page")), outcome)
    }
}
