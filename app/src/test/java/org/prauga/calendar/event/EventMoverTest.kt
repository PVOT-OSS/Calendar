/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.event

import org.junit.Assert.assertEquals
import org.junit.Test

class EventMoverTest {

    private val monday = 1794218400000L
    private val day = 24 * 3600_000L

    @Test
    fun weeklyRuleFollowsTheNewWeekday() {
        assertEquals(
            "FREQ=WEEKLY;BYDAY=TU;COUNT=6",
            EventMover.shiftWeekday("FREQ=WEEKLY;BYDAY=MO;COUNT=6", monday, monday + day, "UTC")
        )
    }

    @Test
    fun otherRulesAreLeftAlone() {
        assertEquals("FREQ=DAILY", EventMover.shiftWeekday("FREQ=DAILY", monday, monday + day, "UTC"))
        assertEquals(
            "FREQ=WEEKLY;BYDAY=MO,WE",
            EventMover.shiftWeekday("FREQ=WEEKLY;BYDAY=MO,WE", monday, monday + day, "UTC")
        )
        assertEquals(
            "FREQ=WEEKLY;BYDAY=MO",
            EventMover.shiftWeekday("FREQ=WEEKLY;BYDAY=MO", monday, monday + 3600_000L, "UTC")
        )
    }

    @Test
    fun timedExdatesMoveWithTheSeries() {
        assertEquals(
            "20261216T020000Z,20261223",
            EventMover.shiftExdates("20261216T013000Z,20261223", 1800_000L)
        )
        assertEquals(null, EventMover.shiftExdates(null, 1000L))
    }
}
