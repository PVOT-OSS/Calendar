/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IcsTimeTest {

    private fun prop(line: String) = IcsParser.parseLine(line)!!

    @Test
    fun parsesUtcTime() {
        val t = IcsTime.parse(prop("DTSTART:20260102T030405Z"), "Europe/Berlin")!!
        assertEquals(1767323045000L, t.millis)
        assertEquals("UTC", t.timeZone)
        assertEquals(false, t.allDay)
    }

    @Test
    fun parsesDateAsUtcMidnight() {
        val t = IcsTime.parse(prop("DTSTART;VALUE=DATE:20260102"), "Europe/Berlin")!!
        assertEquals(1767312000000L, t.millis)
        assertTrue(t.allDay)
        assertEquals("UTC", t.timeZone)
    }

    @Test
    fun parsesTzidTime() {
        val t = IcsTime.parse(prop("DTSTART;TZID=Europe/Berlin:20260701T100000"), "UTC")!!
        assertEquals(1782892800000L, t.millis)
        assertEquals("Europe/Berlin", t.timeZone)
    }

    @Test
    fun floatingTimeUsesDefaultZone() {
        val t = IcsTime.parse(prop("DTSTART:20260701T100000"), "America/New_York")!!
        assertEquals("America/New_York", t.timeZone)
        assertEquals(1782914400000L, t.millis)
    }

    @Test
    fun resolvesVendorAndWindowsZoneIds() {
        assertEquals("Europe/Berlin", IcsTime.resolveZone("/mozilla.org/20050126_1/Europe/Berlin"))
        assertEquals("Europe/Berlin", IcsTime.resolveZone("W. Europe Standard Time"))
        assertNull(IcsTime.resolveZone("Not A Zone"))
    }

    @Test
    fun reportsUnknownZoneAndFallsBack() {
        var unknown: String? = null
        val t =
            IcsTime.parse(prop("DTSTART;TZID=Nowhere:20260701T100000"), "UTC") { unknown = it }!!
        assertEquals("Nowhere", unknown)
        assertEquals("UTC", t.timeZone)
    }

    @Test
    fun badValueIsNull() {
        assertNull(IcsTime.parse(prop("DTSTART:tomorrow"), "UTC"))
    }

    @Test
    fun parsesDurations() {
        assertEquals(-15 * IcsTime.MINUTE, IcsTime.parseDuration("-PT15M"))
        assertEquals(IcsTime.DAY + 2 * IcsTime.HOUR, IcsTime.parseDuration("P1DT2H"))
        assertEquals(14 * IcsTime.DAY, IcsTime.parseDuration("P2W"))
        assertNull(IcsTime.parseDuration("P"))
        assertNull(IcsTime.parseDuration("15M"))
    }

    @Test
    fun formatsDurationsAndUtc() {
        assertEquals("P3600S", IcsTime.formatDuration(IcsTime.HOUR, false))
        assertEquals("P2D", IcsTime.formatDuration(2 * IcsTime.DAY, true))
        assertEquals("20260102T030405Z", IcsTime.formatUtc(1767323045000L))
    }
}
