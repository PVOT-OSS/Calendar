/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.edit

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import org.prauga.calendar.ics.IcsTime
import org.prauga.calendar.settings.GeneralPreferences
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

data class EventDraft(
    val title: String = "",
    val color: Int? = null,
    val calendarId: Long = -1,
    val allDay: Boolean = false,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val location: String = "",
    val description: String = "",
    val rrule: String? = null,
    val reminder: Int = GeneralPreferences.NO_REMINDER,
    val guests: List<String> = emptyList(),
) {
    val valid: Boolean get() = calendarId >= 0 && !end.isBefore(start)

    fun startMillis(zone: ZoneId): Long =
        if (allDay) start.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        else start.atZone(zone).toInstant().toEpochMilli()

    fun endMillis(zone: ZoneId): Long =
        if (allDay) end.toLocalDate().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        else end.atZone(zone).toInstant().toEpochMilli()

    fun withStartDate(date: LocalDate): EventDraft {
        val shift = date.toEpochDay() - start.toLocalDate().toEpochDay()
        return copy(start = start.plusDays(shift), end = end.plusDays(shift))
    }

    fun withStartTime(time: LocalTime): EventDraft {
        val length = Duration.between(start, end)
        val newStart = start.toLocalDate().atTime(time)
        return copy(start = newStart, end = newStart.plus(length))
    }
}

object EventWriter {

    fun eventValues(draft: EventDraft, zone: ZoneId, organizer: String?): ContentValues {
        val begin = draft.startMillis(zone)
        val finish = draft.endMillis(zone)
        return ContentValues().apply {
            put(Events.CALENDAR_ID, draft.calendarId)
            put(Events.TITLE, draft.title.trim())
            put(Events.EVENT_LOCATION, draft.location.trim())
            put(Events.DESCRIPTION, draft.description.trim())
            put(Events.DTSTART, begin)
            put(Events.EVENT_TIMEZONE, if (draft.allDay) "UTC" else zone.id)
            put(Events.ALL_DAY, if (draft.allDay) 1 else 0)
            if (draft.rrule != null) {
                put(Events.RRULE, draft.rrule)
                put(Events.DURATION, IcsTime.formatDuration(finish - begin, draft.allDay))
                putNull(Events.DTEND)
            } else {
                put(Events.DTEND, finish)
            }
            draft.color?.let { put(Events.EVENT_COLOR, it) }
            put(Events.HAS_ALARM, if (draft.reminder != GeneralPreferences.NO_REMINDER) 1 else 0)
            if (draft.guests.isNotEmpty()) {
                put(Events.HAS_ATTENDEE_DATA, 1)
                organizer?.let { put(Events.ORGANIZER, it) }
            }
        }
    }

    fun insert(resolver: ContentResolver, draft: EventDraft, zone: ZoneId, organizer: String?): Long? {
        val uri = resolver.insert(Events.CONTENT_URI, eventValues(draft, zone, organizer)) ?: return null
        val id = ContentUris.parseId(uri)
        if (draft.reminder != GeneralPreferences.NO_REMINDER) {
            resolver.insert(Reminders.CONTENT_URI, ContentValues().apply {
                put(Reminders.EVENT_ID, id)
                put(Reminders.MINUTES, draft.reminder)
                put(Reminders.METHOD, Reminders.METHOD_ALERT)
            })
        }
        draft.guests.forEach { email ->
            resolver.insert(Attendees.CONTENT_URI, ContentValues().apply {
                put(Attendees.EVENT_ID, id)
                put(Attendees.ATTENDEE_EMAIL, email)
                put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ATTENDEE)
                put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_REQUIRED)
                put(Attendees.ATTENDEE_STATUS, Attendees.ATTENDEE_STATUS_INVITED)
            })
        }
        return id
    }
}
