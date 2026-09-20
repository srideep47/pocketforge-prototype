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
}
