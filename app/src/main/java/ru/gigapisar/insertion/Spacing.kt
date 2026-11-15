package ru.gigapisar.insertion

/** Keeps a dictation from sticking to the text that is already before the cursor. */
object Spacing {
    private const val CLOSING = ".,!?;:…)]}»\"”'’%"
    private const val OPENING = "«\"“(["

    /**
     * A space when [text] goes right after a word or a closing mark, an empty string otherwise:
     * at the start of a field or a line, after a space or an opening bracket nothing is added.
     */
    fun separator(
        before: Char?,
        text: String,
    ): String {
        val first = text.firstOrNull() ?: return ""
        if (before == null) return ""
        val afterWord = before.isLetterOrDigit() || before in CLOSING
        val startsWord = first.isLetterOrDigit() || first in OPENING
        return if (afterWord && startsWord) " " else ""
    }
}
