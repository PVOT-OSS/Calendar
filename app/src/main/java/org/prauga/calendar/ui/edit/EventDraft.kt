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
import java.time.Instant
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

    fun load(resolver: ContentResolver, eventId: Long, zone: ZoneId): EventDraft? {
        val draft = resolver.query(
            ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
            arrayOf(
                Events.TITLE, Events.EVENT_COLOR, Events.CALENDAR_ID, Events.ALL_DAY, Events.DTSTART, Events.DTEND,
                Events.EVENT_LOCATION, Events.DESCRIPTION, Events.RRULE,
            ),
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) return null
            val allDay = c.getInt(3) != 0
            val startZone = if (allDay) ZoneOffset.UTC else zone
            val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getLong(4)), startZone)
            var end = LocalDateTime.ofInstant(Instant.ofEpochMilli(c.getLong(5)), startZone)
            if (allDay) end = end.minusDays(1)
            EventDraft(
                title = c.getString(0).orEmpty(),
                color = if (c.isNull(1)) null else c.getInt(1),
                calendarId = c.getLong(2),
                allDay = allDay,
                start = start,
                end = maxOf(end, start),
                location = c.getString(6).orEmpty(),
                description = c.getString(7).orEmpty(),
                rrule = c.getString(8)?.takeIf { it.isNotBlank() },
            )
        } ?: return null
        val reminder = resolver.query(
            Reminders.CONTENT_URI, arrayOf(Reminders.MINUTES),
            "${Reminders.EVENT_ID}=?", arrayOf(eventId.toString()), "${Reminders.MINUTES} ASC",
        )?.use { if (it.moveToFirst()) it.getInt(0) else null } ?: GeneralPreferences.NO_REMINDER
        val guests = ArrayList<String>()
        resolver.query(
            Attendees.CONTENT_URI, arrayOf(Attendees.ATTENDEE_EMAIL, Attendees.ATTENDEE_RELATIONSHIP),
            "${Attendees.EVENT_ID}=?", arrayOf(eventId.toString()), null,
        )?.use {
            while (it.moveToNext()) {
                if (it.getInt(1) != Attendees.RELATIONSHIP_ORGANIZER) it.getString(0)?.let { email -> guests += email }
            }
        }
        return draft.copy(reminder = reminder, guests = guests)
    }

    fun update(resolver: ContentResolver, eventId: Long, draft: EventDraft, before: EventDraft, zone: ZoneId): Boolean {
        val values = eventValues(draft, zone, null).apply {
            remove(Events.CALENDAR_ID)
            if (draft.color == null) putNull(Events.EVENT_COLOR)
        }
        if (resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), values, null, null) <= 0) {
            return false
        }
        if (draft.reminder != before.reminder) {
            resolver.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID}=?", arrayOf(eventId.toString()))
            if (draft.reminder != GeneralPreferences.NO_REMINDER) {
                resolver.insert(Reminders.CONTENT_URI, ContentValues().apply {
                    put(Reminders.EVENT_ID, eventId)
                    put(Reminders.MINUTES, draft.reminder)
                    put(Reminders.METHOD, Reminders.METHOD_ALERT)
                })
            }
        }
        (before.guests - draft.guests.toSet()).forEach { email ->
            resolver.delete(
                Attendees.CONTENT_URI, "${Attendees.EVENT_ID}=? AND ${Attendees.ATTENDEE_EMAIL}=?",
                arrayOf(eventId.toString(), email),
            )
        }
        (draft.guests - before.guests.toSet()).forEach { email ->
            resolver.insert(Attendees.CONTENT_URI, ContentValues().apply {
                put(Attendees.EVENT_ID, eventId)
                put(Attendees.ATTENDEE_EMAIL, email)
                put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ATTENDEE)
                put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_REQUIRED)
                put(Attendees.ATTENDEE_STATUS, Attendees.ATTENDEE_STATUS_INVITED)
            })
        }
        return true
    }
}
