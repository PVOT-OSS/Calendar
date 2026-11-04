/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.event

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RepeatPresetsTest {

    @Test
    fun rulesFollowTheStartDate() {
        assertEquals(
            listOf(
                "FREQ=DAILY",
                "FREQ=WEEKLY;BYDAY=MO",
                "FREQ=MONTHLY;BYDAY=2MO",
                "FREQ=YEARLY",
                "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR",
            ),
            RepeatPresets.rules(1794218400000L, "UTC"),
        )
    }

    @Test
    fun lastWeekdayOfTheMonthUsesMinusOne() {
        assertEquals("FREQ=MONTHLY;BYDAY=-1MO", RepeatPresets.rules(1796032800000L, "UTC")[2])
        assertEquals("FREQ=MONTHLY;BYDAY=-1TH", RepeatPresets.rules(1795687200000L, "UTC")[2])
    }

    @Test
    fun presetsHaveLabels() {
        val presets =
            RepeatPresets.presets(RuntimeEnvironment.getApplication(), 1794218400000L, "UTC")
        assertEquals(6, presets.size)
        assertEquals(null, presets[0].rrule)
        presets.forEach { assert(it.label.isNotBlank()) }
    }
}
