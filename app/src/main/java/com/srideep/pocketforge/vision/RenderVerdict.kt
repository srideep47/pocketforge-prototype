package com.srideep.pocketforge.vision

/**
 * What the vision model made of a screenshot of the page the agent wrote.
 *
 * @property problems what to fix, in the model's words. Empty when the page looked right and
 *   also when the answers were unclear: a fix round rewrites the whole page, so it is only
 *   worth its risk when the critic named something concrete.
 */
data class RenderVerdict(val problems: List<String>) {

    val hasProblems: Boolean get() = problems.isNotEmpty()

    /** One line for the chat. */
    fun summary(): String = if (hasProblems) problems.joinToString("; ") else "no problems seen"

    /** The change request for the one automatic fix round. */
    fun fixRequest(): String =
        "Fix these problems seen in a screenshot of the page: " + problems.joinToString("; ") + "."

    companion object {

        private val THINK = Regex("(?s)<think>.*?</think>")

        /** Yes/no first, as the questions ask; `\b` keeps "not"/"nothing" from reading as "no". */
        private val LEADING_ANSWER = Regex("""^(yes|no)\b[\s.,:;!*-]*(.*)$""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

        /**
         * Detail that says all is well despite the leading word. The 0.8B model often answers
         * the second half of a question with the first word ("No, nothing is missing") or
         * starts with a reflexive "Yes" and then reports no problem at all.
         */
        private val REASSURANCE = Regex(
            """\b(nothing|no (issues?|problems?)|not (broken|messy)|looks? (fine|good|correct|clean|great)|all (the )?(text|content|elements?) (is|are) (visible|readable))\b""",
            RegexOption.IGNORE_CASE,
        )

        private const val MAX_DETAIL_CHARS = 200

        /**
         * Both questions are phrased so that "yes" means a problem.
         *
         * They were chosen by testing on screenshots of a broken page, a page cut off at the
         * edge and a clean page: "is anything overlapping, cut off, unreadable or empty?" caught
         * the cut-off page, "is this screen broken or messy?" caught the broken one, and both
         * passed the clean page. A third question, "does the screen show <request>?", was
         * dropped: it answered "No, the screen is empty" for the clean page, and a false "no"
         * there makes the coder rewrite a page that was fine.
         *
         * @param layoutAnswer answer to the overlap/cut-off question.
         * @param messyAnswer answer to the broken/messy question, or null if not asked.
         */
        fun parse(layoutAnswer: String, messyAnswer: String?): RenderVerdict {
            val problems = mutableListOf<String>()
            for (answer in listOfNotNull(layoutAnswer, messyAnswer)) {
                val (yes, detail) = leadingYesNo(answer)
                // A bare "yes" names nothing to fix, and small models say yes to most questions.
                if (yes == true && detail.isNotEmpty() && !REASSURANCE.containsMatchIn(detail)) {
                    problems += detail
                }
            }
            return RenderVerdict(problems)
        }

        /**
         * The leading yes/no (null when the answer starts with anything else) and the tidied
         * rest of the answer.
         */
        internal fun leadingYesNo(answer: String): Pair<Boolean?, String> {
            // Markdown emphasis and quotes are common around the first word.
            val clean = answer.replace(THINK, "").trim().trimStart('*', '_', '"', '\'', '-', ' ')
            val match = LEADING_ANSWER.find(clean) ?: return null to ""
            val detail = match.groupValues[2]
                .replace('\n', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()
                .trimEnd('*', '.', ' ')
                .take(MAX_DETAIL_CHARS)
            return match.groupValues[1].equals("yes", ignoreCase = true) to detail
        }
    }
}
