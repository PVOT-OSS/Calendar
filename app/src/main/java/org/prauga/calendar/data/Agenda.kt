/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.data

import android.provider.CalendarContract
import org.prauga.calendar.Event
import java.time.LocalDate

data class Agenda(
    val eventId: Long,
    val title: String,
    val location: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    val startDay: Int,
    val endDay: Int,
    val startMinute: Int,
    val endMinute: Int,
    val isTask: Boolean,
    val declined: Boolean,
) {
    fun occursOn(julianDay: Int) = julianDay in startDay..endDay

    companion object {
        fun from(e: Event, isTask: Boolean) = Agenda(
            eventId = e.id,
            title = e.title?.toString().orEmpty(),
            location = e.location?.toString().orEmpty(),
            begin = e.startMillis,
            end = e.endMillis,
            allDay = e.allDay,
            color = e.color,
            startDay = e.startDay,
            endDay = e.endDay,
            startMinute = e.startTime,
            endMinute = e.endTime,
            isTask = isTask,
            declined = e.selfAttendeeStatus == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED,
        )
    }
}

data class AgendaDetails(
    val guests: List<String>,
    val meetingUrl: String?,
)

object JulianDay {
    private const val EPOCH_OFFSET = 2440588L

    fun of(date: LocalDate): Int = (date.toEpochDay() + EPOCH_OFFSET).toInt()

    fun toDate(julianDay: Int): LocalDate = LocalDate.ofEpochDay(julianDay - EPOCH_OFFSET)
}
