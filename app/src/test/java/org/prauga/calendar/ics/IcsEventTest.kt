/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ics

import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IcsEventTest {

    private fun parse(body: String) =
        IcsCalendar.parse("BEGIN:VCALENDAR\nVERSION:2.0\n$body\nEND:VCALENDAR\n", "Europe/Berlin")

    @Test
    fun mapsAllFieldsOfATimedEvent() {
        val cal =
            parse(
                """
            METHOD:REQUEST
            BEGIN:VEVENT
            UID:abc@example.com
            SEQUENCE:2
            SUMMARY:Team sync\, weekly
            LOCATION:Room 1
            DESCRIPTION:Line 1\nLine 2
            URL:https://example.com/meet
            DTSTART;TZID=Europe/Berlin:20260701T100000
            DTEND;TZID=Europe/Berlin:20260701T110000
            ORGANIZER;CN=Boss:mailto:boss@example.com
            ATTENDEE;CN=Jane;ROLE=OPT-PARTICIPANT;PARTSTAT=ACCEPTED:mailto:jane@example.com
            ATTENDEE;CUTYPE=ROOM:mailto:room@example.com
            CLASS:PRIVATE
            TRANSP:TRANSPARENT
            STATUS:TENTATIVE
            BEGIN:VALARM
            ACTION:DISPLAY
            TRIGGER:-PT15M
            END:VALARM
            BEGIN:VALARM
            ACTION:EMAIL
            TRIGGER;RELATED=START:-P1D
            END:VALARM
            END:VEVENT
            """
                    .trimIndent()
            )
        assertEquals("REQUEST", cal.method)
        val e = cal.events.single()
        assertEquals("abc@example.com", e.uid)
        assertEquals(2, e.sequence)
        assertEquals("Team sync, weekly", e.title)
        assertEquals("Line 1\nLine 2", e.description)
        assertEquals("https://example.com/meet", e.url)
        assertEquals("boss@example.com", e.organizer)
        assertEquals(1782892800000L, e.start.millis)
        assertEquals(1782896400000L, e.end.millis)
        assertEquals(
            listOf(
                IcsAttendee(
                    "jane@example.com",
                    "Jane",
                    Attendees.TYPE_OPTIONAL,
                    Attendees.ATTENDEE_STATUS_ACCEPTED,
                ),
                IcsAttendee(
                    "room@example.com",
                    null,
                    Attendees.TYPE_RESOURCE,
                    Attendees.ATTENDEE_STATUS_NONE,
                ),
            ),
            e.attendees,
        )
        assertEquals(
            listOf(IcsAlarm(15, Reminders.METHOD_ALERT), IcsAlarm(1440, Reminders.METHOD_EMAIL)),
            e.alarms,
        )

        val v = e.toEventValues(7)
        assertEquals(7L, v.getAsLong(Events.CALENDAR_ID))
        assertEquals(1782896400000L, v.getAsLong(Events.DTEND))
        assertEquals("Europe/Berlin", v.getAsString(Events.EVENT_TIMEZONE))
        assertEquals(0, v.getAsInteger(Events.ALL_DAY))
        assertEquals("abc@example.com", v.getAsString(Events.UID_2445))
        assertEquals(Events.ACCESS_PRIVATE, v.getAsInteger(Events.ACCESS_LEVEL))
        assertEquals(Events.AVAILABILITY_FREE, v.getAsInteger(Events.AVAILABILITY))
        assertEquals(Events.STATUS_TENTATIVE, v.getAsInteger(Events.STATUS))
        assertEquals(1, v.getAsInteger(Events.HAS_ALARM))
    }

    @Test
    fun allDayEventWithoutEndLastsOneDay() {
        val e =
            parse("BEGIN:VEVENT\nUID:1\nDTSTART;VALUE=DATE:20260102\nEND:VEVENT").events.single()
        assertTrue(e.allDay)
        assertEquals(e.start.millis + IcsTime.DAY, e.end.millis)
        assertEquals("UTC", e.toEventValues(1).getAsString(Events.EVENT_TIMEZONE))
    }

    @Test
    fun outlookAllDayFlagBecomesAllDay() {
        val e =
            parse(
                    "BEGIN:VEVENT\nDTSTART;TZID=Europe/Berlin:20260102T000000\n" +
                        "DTEND;TZID=Europe/Berlin:20260103T000000\nX-MICROSOFT-CDO-ALLDAYEVENT:TRUE\nEND:VEVENT"
                )
                .events
                .single()
        assertTrue(e.allDay)
        assertEquals(1767312000000L, e.start.millis)
        assertEquals(1767312000000L + IcsTime.DAY, e.end.millis)
    }

    @Test
    fun recurringEventUsesDurationAndExdates() {
        val e =
            parse(
                    """
            BEGIN:VEVENT
            UID:r
            DTSTART:20260105T090000Z
            DURATION:PT30M
            RRULE:FREQ=WEEKLY;BYDAY=MO
            EXDATE:20260112T090000Z,20260119T090000Z
            END:VEVENT
            """
                        .trimIndent()
                )
                .events
                .single()
        assertTrue(e.isRecurring)
        val v = e.toEventValues(1)
        assertEquals("P1800S", v.getAsString(Events.DURATION))
        assertNull(v.getAsLong(Events.DTEND))
        assertEquals("FREQ=WEEKLY;BYDAY=MO", v.getAsString(Events.RRULE))
        assertEquals("20260112T090000Z,20260119T090000Z", v.getAsString(Events.EXDATE))
    }

    @Test
    fun recurrenceIdAndCancellation() {
        val events =
            parse(
                """
            METHOD:CANCEL
            BEGIN:VEVENT
            UID:r
            RECURRENCE-ID:20260112T090000Z
            DTSTART:20260112T100000Z
            STATUS:CANCELLED
            END:VEVENT
            """
                    .trimIndent()
            )
        assertEquals("CANCEL", events.method)
        val e = events.events.single()
        assertTrue(e.isException)
        assertTrue(e.cancelled)
        assertEquals(1768208400000L, e.recurrenceId!!.millis)
    }

    @Test
    fun eventsWithoutStartAreSkippedAndZonesReported() {
        val cal =
            parse(
                "BEGIN:VEVENT\nUID:no-start\nEND:VEVENT\n" +
                    "BEGIN:VEVENT\nUID:x\nDTSTART;TZID=Mars/Olympus:20260101T100000\nEND:VEVENT"
            )
        assertEquals(listOf("x"), cal.events.map { it.uid })
        assertEquals(listOf("Mars/Olympus"), cal.warnings)
        assertFalse(cal.events.single().allDay)
    }

    @Test
    fun alarmsAfterStartAreDropped() {
        val e =
            parse(
                    "BEGIN:VEVENT\nDTSTART:20260101T100000Z\nBEGIN:VALARM\nTRIGGER:PT5M\nEND:VALARM\n" +
                        "BEGIN:VALARM\nTRIGGER;VALUE=DATE-TIME:20260101T093000Z\nEND:VALARM\nEND:VEVENT"
                )
                .events
                .single()
        assertEquals(listOf(IcsAlarm(30, Reminders.METHOD_ALERT)), e.alarms)
    }
}
