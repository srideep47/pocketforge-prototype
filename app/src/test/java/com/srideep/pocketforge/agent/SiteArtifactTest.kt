package com.srideep.pocketforge.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SiteArtifactTest {
    @Test
    fun `extracts raw site envelope`() {
        assertEquals(
            "<!doctype html><html><body>Hello</body></html>",
            SiteArtifact.extract("<site>\n<!doctype html><html><body>Hello</body></html>\n</site>"),
        )
    }

    @Test
    fun `recovers site when closing envelope is truncated`() {
        assertEquals(
            "<!doctype html><html><body>Hello</body></html>",
            SiteArtifact.extract("<site>\n<!doctype html><html><body>Hello</body></html>"),
        )
    }

    @Test
    fun `accepts fenced html fallback`() {
        assertEquals(
            "<!doctype html><html><body>Hello</body></html>",
            SiteArtifact.extract("```html\n<!doctype html><html><body>Hello</body></html>\n```"),
        )
    }

    @Test
    fun `rejects prose`() {
        assertNull(SiteArtifact.extract("I would build a nice site."))
    }

    @Test
    fun `makes generated site self contained and responsive`() {
        val result = SiteArtifact.extract(
            """<site><!doctype html><html><head>
                <link rel="stylesheet" href="style.css">
                <script src="app.js"></script>
                </head><body>Hello</body></html></site>""",
        )!!

        assertEquals(false, result.contains("style.css"))
        assertEquals(false, result.contains("app.js"))
        assertEquals(true, result.contains("name=\"viewport\""))
    }
}
