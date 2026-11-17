package com.inofarm.jogja.core.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ThemePreferenceTest {
    private class MemoryStorage(var value: String? = null) : ThemeModeStorage {
        var writes = 0
        override fun read() = value
        override fun write(value: String) { this.value = value; writes++ }
    }

    @Test fun defaultsToLightWhenNothingIsStored() {
        val storage = MemoryStorage()
        assertEquals(ThemeMode.LIGHT, ThemePreference(storage).mode.value)
        assertNull("Reading the default must not write it", storage.value)
    }

    @Test fun unknownStoredValueFallsBackToLight() {
        assertEquals(ThemeMode.LIGHT, ThemePreference(MemoryStorage("system")).mode.value)
    }

    @Test fun selectionIsStoredAndSurvivesANewInstance() {
        val storage = MemoryStorage()
        ThemePreference(storage).select(ThemeMode.DARK)
        assertEquals("dark", storage.value)
        assertEquals(ThemeMode.DARK, ThemePreference(storage).mode.value)
    }

    @Test fun selectingTheSameModeDoesNotWriteAgain() {
        val storage = MemoryStorage("dark")
        val preference = ThemePreference(storage)
        preference.select(ThemeMode.DARK)
        assertEquals(0, storage.writes)
        preference.select(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, preference.mode.value)
        assertEquals(1, storage.writes)
    }
}
