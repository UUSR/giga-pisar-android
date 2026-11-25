package ru.gigapisar.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.gigapisar.settings.SettingsRepository.BrainSettings

class BrainSettingsTest {
    @Test
    fun upperKeyEditsOnlyWhenItDiffersFromLower() {
        // Its own edit: made of the words as said.
        assertEquals(TakeEdit.TRANSLATE, BrainSettings(lower = TakeEdit.TIDY, upper = UpperKey.TRANSLATE).upperEdit)
        assertEquals(TakeEdit.SHORTEN, BrainSettings(upper = UpperKey.SHORTEN).upperEdit)
        // The same edit as volume down, or none at all: the key puts the words back.
        assertNull(BrainSettings(lower = TakeEdit.TIDY, upper = UpperKey.TIDY).upperEdit)
        assertNull(BrainSettings(lower = TakeEdit.TIDY, upper = UpperKey.ORIGINAL).upperEdit)
        assertNull(BrainSettings(upper = UpperKey.RECORD).upperEdit)
        assertNull(BrainSettings(upper = UpperKey.VOLUME).upperEdit)
    }

    @Test
    fun twoTranslationsDifferByLanguage() {
        val same = BrainSettings(lower = TakeEdit.TRANSLATE, upper = UpperKey.TRANSLATE, lowerLanguage = "English", upperLanguage = " english ")
        assertNull(same.upperEdit)
        val other = same.copy(upperLanguage = "Spanish")
        assertEquals(TakeEdit.TRANSLATE, other.upperEdit)
    }

    @Test
    fun brainIsInUseWhenAnythingAsksForIt() {
        assertFalse(BrainSettings().inUse)
        assertFalse(BrainSettings(upper = UpperKey.ORIGINAL).inUse)
        assertTrue(BrainSettings(commands = true).inUse)
        assertTrue(BrainSettings(lower = TakeEdit.SHORTEN).inUse)
        assertTrue(BrainSettings(upper = UpperKey.TRANSLATE).inUse)
    }
}
