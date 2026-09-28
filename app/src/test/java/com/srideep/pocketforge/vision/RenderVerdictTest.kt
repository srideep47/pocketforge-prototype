package com.srideep.pocketforge.vision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderVerdictTest {
    @Test
    fun `layout yes with a named problem needs a fix`() {
        val verdict = RenderVerdict.parse("Yes, the title text is cut off at the right edge.", null)
        assertEquals(listOf("the title text is cut off at the right edge"), verdict.problems)
        assertTrue(verdict.fixRequest().contains("the title text is cut off"))
    }

    // The three answers below are the 0.8B model's real replies on test screenshots.
    @Test
    fun `broken page is caught by the messy question`() {
        val verdict = RenderVerdict.parse(
            "No, nothing is overlapped, cut off, unreadable or empty.",
            "Yes, the screen has a lot of errors in numbers and shapes that make it look like broken math input.",
        )
        assertEquals(1, verdict.problems.size)
        assertTrue(verdict.problems[0].startsWith("the screen has a lot of errors"))
    }

    @Test
    fun `clean page passes both questions`() {
        val verdict = RenderVerdict.parse(
            "No, nothing is overlapping, cut off or unreadable on the screen.",
            "No, it's a clean and well-organized interface with the math function clearly labeled.",
        )
        assertFalse(verdict.hasProblems)
    }

    @Test
    fun `bare yes names nothing and is not acted on`() {
        assertFalse(RenderVerdict.parse("Yes.", "Yes").hasProblems)
    }

    @Test
    fun `yes followed by reassurance is not a problem`() {
        assertFalse(RenderVerdict.parse("Yes, nothing is overlapping and it looks fine.", null).hasProblems)
        assertFalse(RenderVerdict.parse("No.", "Yes, it is not broken or messy.").hasProblems)
    }

    @Test
    fun `unclear answers never trigger a fix`() {
        val verdict = RenderVerdict.parse(
            "The screen shows a calculator with a display and buttons.",
            "It is a calculator.",
        )
        assertFalse(verdict.hasProblems)
    }

    @Test
    fun `both problems are collected`() {
        val verdict = RenderVerdict.parse(
            "**Yes** - the buttons overlap the footer.",
            "Yes, the keypad is squashed into one column.",
        )
        assertEquals(listOf("the buttons overlap the footer", "the keypad is squashed into one column"), verdict.problems)
    }

    @Test
    fun `leading word must be a whole word`() {
        assertNull(RenderVerdict.leadingYesNo("Nothing overlaps.").first)
        assertNull(RenderVerdict.leadingYesNo("Not really.").first)
        assertEquals(true, RenderVerdict.leadingYesNo("<think>hmm</think>\nYES: text is tiny").first)
        assertEquals("text is tiny", RenderVerdict.leadingYesNo("YES: text is tiny").second)
    }

    @Test
    fun `script errors turn a clean verdict into a fix`() {
        val clean = RenderVerdict.parse("No, nothing overlaps.", "No, it looks clean.")
        assertEquals(false, clean.hasProblems)
        assertEquals(clean, clean.withScriptErrors(emptyList()))
        val broken = clean.withScriptErrors(listOf("Uncaught ReferenceError: total is not defined (line 42)"))
        assertEquals(listOf("the script fails: Uncaught ReferenceError: total is not defined (line 42)"), broken.problems)
    }

    @Test
    fun `a yes that describes a clean screen is not a problem`() {
        val verdict = RenderVerdict.parse(
            "No",
            "Yes, the image has a clean layout with no overlaps, cuts, or unreadables.",
        )
        assertEquals(false, verdict.hasProblems)
    }
}
