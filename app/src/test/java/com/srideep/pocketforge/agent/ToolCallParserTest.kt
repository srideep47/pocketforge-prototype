package com.srideep.pocketforge.agent

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

    @Test
    fun `repairs a tool call that is missing its closing brace`() {
        // The shape Qwen3.5-2B produced on device: a complete call whose outer brace
        // never arrived, with CSS braces inside the content string to trip up any
        // repair that just counts from the end.
        val parser = ToolCallParser()
        val events = parser.feed(
            "<tool_call>{\"name\": \"create_file\", \"arguments\": {\"path\": \"index.html\", " +
                "\"content\": \"<style>body { color: #ff0000; }</style><h1>Hello</h1>\"}</tool_call>",
        )

        val call = events.filterIsInstance<AgentEvent.Call>().single().call
        assertEquals("create_file", call.name)
        assertEquals("index.html", call.arguments.getString("path"))
        assertTrue(call.arguments.getString("content").contains("<h1>Hello</h1>"))
    }

    @Test
    fun `recovers when the opening tag is repeated before the json`() {
        val parser = ToolCallParser()
        val events = parser.feed(
            "<tool_call>\n<tool_call>\n{\"name\":\"list_files\",\"arguments\":{}}\n</tool_call>",
        )
        assertEquals("list_files", events.filterIsInstance<AgentEvent.Call>().single().call.name)
    }

    @Test
    fun `recovers a tool call the model emitted without any tags`() {
        // Observed on device: the model drops the XML wrapper and just emits the object.
        val parser = ToolCallParser()
        val events = parser.feed(
            "{\"name\": \"start_dev_server\", \"arguments\": {}}",
        ) + parser.finish()

        assertEquals("start_dev_server", events.filterIsInstance<AgentEvent.Call>().single().call.name)
    }

    @Test
    fun `prose that merely mentions json is not a tool call`() {
        val parser = ToolCallParser()
        val events = parser.feed("I will send {\"name\": \"something_else\"} shortly.") + parser.finish()

        assertTrue(events.filterIsInstance<AgentEvent.Call>().isEmpty())
        assertTrue(events.filterIsInstance<AgentEvent.Text>().isNotEmpty())
    }

    @Test
    fun `salvages a call whose key carries a stray escape`() {
        // Verbatim shape from the Honor device: the model wrote write_file instead of
        // create_file and escaped the closing quote of the path key, which makes the whole
        // object unparseable even though the HTML in content was correct.
        val parser = ToolCallParser()
        val events = parser.feed(
            "<tool_call>{\"name\": \"write_file\", \"arguments\": {\"path\\\": " +
                "\"index.html\", \"content\": \"<h1>Hello</h1>\"}}</tool_call>",
        ) + parser.finish()

        val call = events.filterIsInstance<AgentEvent.Call>().single().call
        assertEquals("write_file", call.name)
        assertEquals("index.html", call.arguments.getString("path"))
        assertEquals("<h1>Hello</h1>", call.arguments.getString("content"))
    }
}
