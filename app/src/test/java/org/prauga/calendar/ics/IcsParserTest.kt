/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IcsParserTest {

    @Test
    fun unfoldsContinuationLinesAndMixedLineEndings() {
        val lines = IcsParser.unfold("A:one\r\n two\n\tthree\rB:x\n\n")
        assertEquals(listOf("A:onetwothree", "B:x"), lines)
    }

    @Test
    fun parsesParametersWithQuotesAndColons() {
        val p = IcsParser.parseLine(
            "ATTENDEE;CN=\"Doe, Jane\";ROLE=OPT-PARTICIPANT;X-URL=\"http://a:b\":mailto:jane@example.com"
        )!!
        assertEquals("ATTENDEE", p.name)
        assertEquals("Doe, Jane", p.param("cn"))
        assertEquals("OPT-PARTICIPANT", p.param("ROLE"))
        assertEquals("http://a:b", p.param("X-URL"))
        assertEquals("mailto:jane@example.com", p.value)
    }

    @Test
    fun lineWithoutColonIsIgnored() {
        assertNull(IcsParser.parseLine("garbage"))
    }

    @Test
    fun unescapesText() {
        val p = IcsParser.parseLine("DESCRIPTION:a\\, b\\; c\\nd\\Ne\\\\f")!!
        assertEquals("a, b; c\nd\ne\\f", p.text)
        assertEquals("a\\, b\\; c\\nd", IcsParser.escapeText("a, b; c\nd"))
    }

    @Test
    fun buildsNestedComponents() {
        val roots = IcsParser.parse(
            """
            BEGIN:VCALENDAR
            METHOD:PUBLISH
            BEGIN:VTIMEZONE
            TZID:Europe/Berlin
            END:VTIMEZONE
            BEGIN:VEVENT
            UID:1
            BEGIN:VALARM
            TRIGGER:-PT15M
            END:VALARM
            END:VEVENT
            BEGIN:VEVENT
            UID:2
            END:VEVENT
            END:VCALENDAR
            """.trimIndent()
        )
        assertEquals(1, roots.size)
        val cal = roots[0]
        assertEquals("PUBLISH", cal.text("METHOD"))
        assertEquals(2, cal.components("VEVENT").size)
        val first = cal.components("VEVENT")[0]
        assertEquals("1", first.text("UID"))
        assertNull(first.first("TRIGGER"))
        assertEquals("-PT15M", first.components("VALARM")[0].text("TRIGGER"))
    }

    @Test
    fun toleratesMissingEndLines() {
        val roots = IcsParser.parse("BEGIN:VCALENDAR\nBEGIN:VEVENT\nUID:1\n")
        assertEquals("1", roots[0].components("VEVENT")[0].text("UID"))
    }
}
