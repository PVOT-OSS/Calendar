/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.event

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.ExtendedProperties
import android.provider.CalendarContract.Reminders
import org.prauga.calendar.ics.ImportTarget

class EventCopier(private val resolver: ContentResolver) {

    fun copy(eventId: Long, target: ImportTarget): Long? {
        val values = ContentValues()
        resolver.query(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), EVENT_COLUMNS, null, null, null)?.use {
            if (!it.moveToFirst()) return null
            for ((i, column) in EVENT_COLUMNS.withIndex()) copyColumn(it, i, column, values)
        } ?: return null
        values.put(Events.CALENDAR_ID, target.id)
        if (values.getAsString(Events.RRULE).isNullOrEmpty() && values.getAsString(Events.RDATE).isNullOrEmpty()) {
            values.remove(Events.DURATION)
        } else {
            values.remove(Events.DTEND)
        }
        val uri = resolver.insert(Events.CONTENT_URI, values) ?: return null
        val newId = ContentUris.parseId(uri)

        copyRows(Reminders.CONTENT_URI, Reminders.EVENT_ID, eventId, newId, arrayOf(Reminders.MINUTES, Reminders.METHOD))
        copyRows(
            Attendees.CONTENT_URI, Attendees.EVENT_ID, eventId, newId,
            arrayOf(
                Attendees.ATTENDEE_NAME, Attendees.ATTENDEE_EMAIL, Attendees.ATTENDEE_RELATIONSHIP,
                Attendees.ATTENDEE_TYPE, Attendees.ATTENDEE_STATUS
            )
        )
        resolver.query(
            ExtendedProperties.CONTENT_URI, arrayOf(ExtendedProperties.VALUE),
            "${ExtendedProperties.EVENT_ID}=? AND ${ExtendedProperties.NAME} IN (?,?)",
            arrayOf(eventId.toString(), ExtendedProperty.URL_NAME, ExtendedProperty.URL_NAME_PRIV), null
        )?.use {
            if (it.moveToFirst() && !it.isNull(0)) {
                resolver.insert(ExtendedProperty.contentUri(target.accountName, target.accountType), ContentValues().apply {
                    put(ExtendedProperties.EVENT_ID, newId)
                    put(ExtendedProperties.NAME, ExtendedProperty.URL_NAME)
                    put(ExtendedProperties.VALUE, it.getString(0))
                })
            }
        }
        return newId
    }

    private fun copyRows(uri: android.net.Uri, eventColumn: String, from: Long, to: Long, columns: Array<String>) {
        resolver.query(uri, columns, "$eventColumn=?", arrayOf(from.toString()), null)?.use {
            while (it.moveToNext()) {
                val row = ContentValues().apply { put(eventColumn, to) }
                for ((i, column) in columns.withIndex()) copyColumn(it, i, column, row)
                resolver.insert(uri, row)
            }
        }
    }

    private fun copyColumn(cursor: Cursor, index: Int, column: String, values: ContentValues) {
        when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> {}
            Cursor.FIELD_TYPE_INTEGER -> values.put(column, cursor.getLong(index))
            else -> values.put(column, cursor.getString(index))
        }
    }

    companion object {
        private val EVENT_COLUMNS = arrayOf(
            Events.TITLE, Events.DESCRIPTION, Events.EVENT_LOCATION, Events.DTSTART, Events.DTEND,
            Events.DURATION, Events.RRULE, Events.RDATE, Events.EXDATE, Events.EVENT_TIMEZONE,
            Events.EVENT_END_TIMEZONE, Events.ALL_DAY, Events.ACCESS_LEVEL, Events.AVAILABILITY,
            Events.HAS_ALARM, Events.HAS_ATTENDEE_DATA, Events.EVENT_COLOR, Events.ORGANIZER,
            Events.GUESTS_CAN_MODIFY, Events.GUESTS_CAN_INVITE_OTHERS, Events.GUESTS_CAN_SEE_GUESTS
        )
    }
}
