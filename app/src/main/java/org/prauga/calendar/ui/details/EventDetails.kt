/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.details

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import org.prauga.calendar.MeetingLinks
import org.prauga.calendar.ics.IcsTime
import org.prauga.calendar.persistence.CalendarTarget

data class Guest(
    val name: String,
    val email: String,
    val status: Int,
    val organizer: Boolean,
    val optional: Boolean,
)

data class EventDetails(
    val id: Long,
    val title: String,
    val location: String,
    val description: String,
    val allDay: Boolean,
    val rrule: String?,
    val color: Int,
    val calendar: CalendarTarget,
    val ownerAccount: String,
    val canModify: Boolean,
    val guests: List<Guest>,
    val reminders: List<Int>,
    val meetingUrl: String?,
    val dtStart: Long,
    val dtEnd: Long,
) {
    val self: Guest? get() = guests.firstOrNull { it.email.equals(ownerAccount, ignoreCase = true) }

    val canRespond: Boolean get() = self != null && self?.organizer == false
}

object EventDetailsLoader {

    fun load(resolver: ContentResolver, eventId: Long): EventDetails? {
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
        val event = resolver.query(
            uri,
            arrayOf(
                Events.TITLE, Events.EVENT_LOCATION, Events.DESCRIPTION, Events.ALL_DAY, Events.RRULE,
                Events.DISPLAY_COLOR, Events.CALENDAR_ID, Events.CALENDAR_DISPLAY_NAME, Events.ACCOUNT_NAME,
                Events.ACCOUNT_TYPE, Events.CALENDAR_COLOR, Events.OWNER_ACCOUNT, Events.CALENDAR_ACCESS_LEVEL,
                Events.ORGANIZER, Events.GUESTS_CAN_MODIFY, Events.DTSTART, Events.DTEND, Events.DURATION,
            ),
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) return null
            val owner = c.getString(11).orEmpty()
            val organizer = c.getString(13)
            val isOrganizer = organizer == null || organizer.equals(owner, ignoreCase = true)
            EventDetails(
                id = eventId,
                title = c.getString(0).orEmpty(),
                location = c.getString(1).orEmpty(),
                description = c.getString(2).orEmpty(),
                allDay = c.getInt(3) != 0,
                rrule = c.getString(4)?.takeIf { it.isNotBlank() },
                color = c.getInt(5),
                calendar = CalendarTarget(
                    c.getLong(6), c.getString(7).orEmpty(), c.getString(8).orEmpty(), c.getString(9).orEmpty(), c.getInt(10),
                ),
                ownerAccount = owner,
                canModify = c.getInt(12) >= Calendars.CAL_ACCESS_CONTRIBUTOR && (isOrganizer || c.getInt(14) != 0),
                guests = emptyList(),
                reminders = emptyList(),
                meetingUrl = MeetingLinks.find(c.getString(1), c.getString(2))?.url,
                dtStart = c.getLong(15),
                dtEnd = if (c.isNull(16)) c.getLong(15) + durationMillis(c.getString(17)) else c.getLong(16),
            )
        } ?: return null

        val guests = ArrayList<Guest>()
        resolver.query(
            Attendees.CONTENT_URI,
            arrayOf(Attendees.ATTENDEE_NAME, Attendees.ATTENDEE_EMAIL, Attendees.ATTENDEE_STATUS,
                Attendees.ATTENDEE_RELATIONSHIP, Attendees.ATTENDEE_TYPE),
            "${Attendees.EVENT_ID}=?", arrayOf(eventId.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val email = c.getString(1).orEmpty()
                guests += Guest(
                    name = c.getString(0)?.takeIf { it.isNotBlank() } ?: email,
                    email = email,
                    status = c.getInt(2),
                    organizer = c.getInt(3) == Attendees.RELATIONSHIP_ORGANIZER,
                    optional = c.getInt(4) == Attendees.TYPE_OPTIONAL,
                )
            }
        }
        val reminders = ArrayList<Int>()
        resolver.query(
            Reminders.CONTENT_URI, arrayOf(Reminders.MINUTES),
            "${Reminders.EVENT_ID}=?", arrayOf(eventId.toString()), "${Reminders.MINUTES} ASC",
        )?.use { c -> while (c.moveToNext()) reminders += c.getInt(0) }
        return event.copy(
            guests = guests.sortedWith(compareBy({ !it.organizer }, { it.name.lowercase() })),
            reminders = reminders,
        )
    }

    private fun durationMillis(duration: String?): Long =
        duration?.let { IcsTime.parseDuration(it) } ?: 3_600_000L

    fun respond(resolver: ContentResolver, details: EventDetails, status: Int): Boolean {
        val self = details.self ?: return false
        return resolver.update(
            Attendees.CONTENT_URI,
            ContentValues().apply { put(Attendees.ATTENDEE_STATUS, status) },
            "${Attendees.EVENT_ID}=? AND ${Attendees.ATTENDEE_EMAIL}=?",
            arrayOf(details.id.toString(), self.email),
        ) > 0
    }
}
