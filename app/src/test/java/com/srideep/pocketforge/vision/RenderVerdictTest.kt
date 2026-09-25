package com.srideep.pocketforge.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderVerdictTest {
    @Test
    fun `layout yes with a named problem needs a fix`() {
        val verdict = RenderVerdict.parse("Yes, the title text is cut off at the right edge.", null, null)
        assertEquals(listOf("the title text is cut off at the right edge"), verdict.problems)
        assertTrue(verdict.fixRequest().contains("the title text is cut off"))
    }

    @Test
    fun `layout no is clean`() {
        assertFalse(RenderVerdict.parse("No, everything is readable.", "Yes.", "a todo list").hasProblems)
    }

    @Test
    fun `bare yes names nothing and is not acted on`() {
        assertFalse(RenderVerdict.parse("Yes.", null, null).hasProblems)
    }

    @Test
    fun `yes followed by reassurance is not a problem`() {
        assertFalse(RenderVerdict.parse("Yes, nothing is overlapping and it looks fine.", null, null).hasProblems)
    }

    @Test
    fun `unclear answers never trigger a fix`() {
        val verdict = RenderVerdict.parse(
            "The screen shows a calculator with a display and buttons.",
            "The screen shows a calculator.",
            "a calculator",
        )
        assertFalse(verdict.hasProblems)
    }

    @Test
    fun `goal no with detail uses the detail`() {
        val verdict = RenderVerdict.parse("No.", "No, the add button is missing.", "a todo list with an add button")
        assertEquals(listOf("the add button is missing"), verdict.problems)
    }

    @Test
    fun `bare goal no falls back to the goal`() {
        val verdict = RenderVerdict.parse("No.", "No", "a login form")
        assertEquals(listOf("it does not show: a login form"), verdict.problems)
    }

    @Test
    fun `no nothing is missing is not a problem`() {
        assertFalse(RenderVerdict.parse("No.", "No, nothing is missing.", "a login form").hasProblems)
    }

    @Test
    fun `both problems are collected`() {
        val verdict = RenderVerdict.parse(
            "**Yes** - the buttons overlap the footer.",
            "No, there is no search box.",
            "a search page",
        )
        assertEquals(listOf("the buttons overlap the footer", "there is no search box"), verdict.problems)
    }

    @Test
    fun `leading word must be a whole word`() {
        assertNull(RenderVerdict.leadingYesNo("Nothing overlaps.").first)
        assertNull(RenderVerdict.leadingYesNo("Not really.").first)
        assertEquals(true, RenderVerdict.leadingYesNo("<think>hmm</think>\nYES: text is tiny").first)
        assertEquals("text is tiny", RenderVerdict.leadingYesNo("YES: text is tiny").second)
    }
}
