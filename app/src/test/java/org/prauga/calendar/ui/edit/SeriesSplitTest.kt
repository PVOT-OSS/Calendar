/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.edit

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SeriesSplitTest {

    private val day = 86_400_000L
    private val start = 1793871000000L

    @Test
    fun openSeriesEndsTheSecondBeforeTheOccurrence() {
        val r = SeriesSplit.split("FREQ=DAILY", start, false, "Asia/Kolkata", start + 3 * day)
        assertEquals("FREQ=DAILY;UNTIL=20261108T092959Z", r.before)
        assertEquals("FREQ=DAILY", r.from)
    }

    @Test
    fun countedSeriesSplitsTheCount() {
        val r = SeriesSplit.split("FREQ=DAILY;COUNT=10", start, false, "Asia/Kolkata", start + 3 * day)
        assertEquals("FREQ=DAILY;COUNT=3", r.before)
        assertEquals("FREQ=DAILY;COUNT=7", r.from)
    }

    @Test
    fun allDaySeriesEndsTheDayBefore() {
        val midnight = 1793836800000L
        val r = SeriesSplit.split("FREQ=WEEKLY;UNTIL=20271231", midnight, true, "UTC", midnight + 7 * day)
        assertEquals("FREQ=WEEKLY;UNTIL=20261111", r.before)
    }
}
