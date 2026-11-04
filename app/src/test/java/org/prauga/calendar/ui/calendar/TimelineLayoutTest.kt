/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.calendar

import org.junit.Assert.assertEquals
import org.junit.Test
import org.prauga.calendar.data.Agenda

class TimelineLayoutTest {

    private val day = 2461356

    private fun agenda(
        id: Long,
        start: Int,
        end: Int,
        startDay: Int = day,
        endDay: Int = day,
        allDay: Boolean = false,
    ) =
        Agenda(
            id,
            "e$id",
            "",
            0,
            0,
            allDay,
            0,
            startDay,
            endDay,
            start,
            end,
            isTask = false,
            declined = false,
        )

    private fun List<Slot>.of(id: Long) = first { it.agenda.eventId == id }

    @Test
    fun separateEventsTakeTheWholeWidth() {
        val slots = TimelineLayout.layout(listOf(agenda(1, 540, 600), agenda(2, 600, 660)), day)
        assertEquals(1, slots.of(1).columns)
        assertEquals(1, slots.of(2).columns)
    }

    @Test
    fun overlappingEventsShareColumns() {
        val slots =
            TimelineLayout.layout(
                listOf(
                    agenda(1, 540, 660),
                    agenda(2, 570, 600),
                    agenda(3, 600, 630),
                    agenda(4, 700, 760),
                ),
                day,
            )
        assertEquals(0 to 2, slots.of(1).column to slots.of(1).columns)
        assertEquals(1 to 2, slots.of(2).column to slots.of(2).columns)
        assertEquals(1 to 2, slots.of(3).column to slots.of(3).columns)
        assertEquals(0 to 1, slots.of(4).column to slots.of(4).columns)
    }

    @Test
    fun multiDayEventsAreCutToTheDayAndAllDayOnesLeftOut() {
        val slots =
            TimelineLayout.layout(
                listOf(agenda(1, 1320, 120, startDay = day - 1), agenda(2, 0, 1440, allDay = true)),
                day,
            )
        assertEquals(1, slots.size)
        assertEquals(0, slots.of(1).startMinute)
        assertEquals(120, slots.of(1).endMinute)
    }

    @Test
    fun shortEventsGetAMinimumHeight() {
        val slot = TimelineLayout.layout(listOf(agenda(1, 600, 605)), day).single()
        assertEquals(630, slot.endMinute)
    }
}
