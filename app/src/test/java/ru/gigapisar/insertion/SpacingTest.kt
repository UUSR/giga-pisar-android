package ru.gigapisar.insertion

import org.junit.Assert.assertEquals
import org.junit.Test

class SpacingTest {
    @Test
    fun spaceAfterSentence() {
        assertEquals(" ", Spacing.separator('.', "Второе предложение."))
        assertEquals(" ", Spacing.separator('?', "Да"))
        assertEquals(" ", Spacing.separator('а', "и ещё"))
        assertEquals(" ", Spacing.separator(',', "«цитата»"))
    }

    @Test
    fun nothingAtStartOrAfterSpace() {
        assertEquals("", Spacing.separator(null, "Привет"))
        assertEquals("", Spacing.separator(' ', "Привет"))
        assertEquals("", Spacing.separator('\n', "Привет"))
        assertEquals("", Spacing.separator('(', "в скобках"))
        assertEquals("", Spacing.separator('@', "имя"))
    }

    @Test
    fun nothingBeforePunctuationOrEmpty() {
        assertEquals("", Spacing.separator('а', ", и дальше"))
        assertEquals("", Spacing.separator('а', ""))
    }
}
