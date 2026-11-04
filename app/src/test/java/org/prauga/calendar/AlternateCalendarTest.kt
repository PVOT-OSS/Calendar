/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class AlternateCalendarTest {

    @Test
    fun persianNewYearIsFarvardin1() {
        Locale.setDefault(Locale.US)
        assertTrue(AlternateCalendar.label("persian", 2026, 2, 21, true).contains("Farvardin"))
        assertEquals("2", AlternateCalendar.label("persian", 2026, 2, 22, true))
        assertTrue(AlternateCalendar.label("persian", 2026, 2, 21, false).contains("1405"))
    }

    @Test
    fun hebrewAndChineseLabels() {
        Locale.setDefault(Locale.US)
        assertTrue(AlternateCalendar.label("hebrew", 2026, 8, 12, true).contains("Tishri"))
        val newYear = AlternateCalendar.label("chinese", 2026, 1, 17, true)
        assertTrue(newYear, newYear != "17")
    }
}
