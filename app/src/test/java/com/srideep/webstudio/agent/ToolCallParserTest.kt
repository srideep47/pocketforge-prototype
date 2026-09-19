package com.srideep.webstudio.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCallParserTest {

    @Test
    fun `splits prose from a tool call`() {
        val parser = ToolCallParser()
        val events = parser.feed(
            "Let me build that.\n<tool_call>\n{\"name\": \"create_file\", " +
                "\"arguments\": {\"path\": \"index.html\", \"content\": \"<h1>hi</h1>\"}}\n</tool_call>",
        ) + parser.finish()

        val text = events.filterIsInstance<AgentEvent.Text>().joinToString("") { it.delta }
        val calls = events.filterIsInstance<AgentEvent.Call>()

        assertEquals("Let me build that.\n", text)
        assertEquals(1, calls.size)
        assertEquals("create_file", calls.first().call.name)
        assertEquals("index.html", calls.first().call.arguments.getString("path"))
    }

    @Test
    fun `holds back a tag that is split across chunks`() {
        val parser = ToolCallParser()

        // The opening tag arrives one character at a time; none of it may leak into chat.
        val leaked = "<tool_call>".map { parser.feed(it.toString()) }
            .flatten()
            .filterIsInstance<AgentEvent.Text>()
        assertTrue(leaked.isEmpty())

        val events = parser.feed("{\"name\":\"list_files\",\"arguments\":{}}</tool_call>")
        val calls = events.filterIsInstance<AgentEvent.Call>()
        assertEquals(1, calls.size)
        assertEquals("list_files", calls.first().call.name)
    }

    @Test
    fun `reports a call that is not valid json`() {
        val parser = ToolCallParser()
        val events = parser.feed("<tool_call>{oops}</tool_call>")
        assertEquals(1, events.filterIsInstance<AgentEvent.Malformed>().size)
    }

    @Test
    fun `text that only looks like a tag is still emitted`() {
        val parser = ToolCallParser()
        val events = parser.feed("use <tool to build") + parser.finish()
        val text = events.filterIsInstance<AgentEvent.Text>().joinToString("") { it.delta }
        assertEquals("use <tool to build", text)
    }
}
