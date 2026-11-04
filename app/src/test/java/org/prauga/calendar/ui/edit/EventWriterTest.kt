/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.edit

import android.provider.CalendarContract.Events
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class EventWriterTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val start = LocalDateTime.of(2026, 11, 5, 15, 0)

    @Test
    fun timedEventGetsLocalTimes() {
        val v = EventWriter.eventValues(
            EventDraft(title = " Sync ", calendarId = 3, start = start, end = start.plusHours(1)), zone, null,
        )
        assertEquals("Sync", v.getAsString(Events.TITLE))
        assertEquals(1793871000000L, v.getAsLong(Events.DTSTART))
        assertEquals(1793874600000L, v.getAsLong(Events.DTEND))
        assertEquals("Asia/Kolkata", v.getAsString(Events.EVENT_TIMEZONE))
        assertFalse(v.containsKey(Events.EVENT_COLOR))
    }

    @Test
    fun allDayEventSpansUtcMidnights() {
        val v = EventWriter.eventValues(
            EventDraft(calendarId = 3, allDay = true, start = start, end = start.plusDays(1)), zone, null,
        )
        assertEquals(1793836800000L, v.getAsLong(Events.DTSTART))
        assertEquals(1793836800000L + 2 * 86_400_000L, v.getAsLong(Events.DTEND))
        assertEquals("UTC", v.getAsString(Events.EVENT_TIMEZONE))
    }

    @Test
    fun repeatingEventUsesADuration() {
        val v = EventWriter.eventValues(
            EventDraft(calendarId = 3, start = start, end = start.plusMinutes(90), rrule = "FREQ=WEEKLY;BYDAY=TH"),
            zone, null,
        )
        assertEquals("FREQ=WEEKLY;BYDAY=TH", v.getAsString(Events.RRULE))
        assertEquals("P5400S", v.getAsString(Events.DURATION))
        assertNull(v.getAsLong(Events.DTEND))
    }
}
