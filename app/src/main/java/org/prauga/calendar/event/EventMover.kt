/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.event

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.Cursor
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.prauga.calendar.ics.IcsTime

class EventMover(private val resolver: ContentResolver) {

    data class Info(
        val eventId: Long,
        val canModify: Boolean,
        val recurring: Boolean,
        val allDay: Boolean,
    )

    enum class Scope {
        THIS,
        ALL,
    }

    fun info(eventId: Long): Info? =
        resolver
            .query(
                ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
                arrayOf(
                    Events.CALENDAR_ACCESS_LEVEL,
                    Events.ORGANIZER,
                    Events.OWNER_ACCOUNT,
                    Events.GUESTS_CAN_MODIFY,
                    Events.RRULE,
                    Events.RDATE,
                    Events.ALL_DAY,
                ),
                null,
                null,
                null,
            )
            ?.use {
                if (!it.moveToFirst()) return null
                val access = it.getInt(0)
                val isOrganizer =
                    it.isNull(1) || it.getString(1).equals(it.getString(2), ignoreCase = true)
                Info(
                    eventId = eventId,
                    canModify =
                        access >= Calendars.CAL_ACCESS_CONTRIBUTOR &&
                            (isOrganizer || it.getInt(3) != 0),
                    recurring =
                        !it.getString(4).isNullOrEmpty() || !it.getString(5).isNullOrEmpty(),
                    allDay = it.getInt(6) != 0,
                )
            }

    fun move(
        eventId: Long,
        oldBegin: Long,
        oldEnd: Long,
        newBegin: Long,
        newEnd: Long,
        scope: Scope,
    ): (() -> Unit)? {
        val info = info(eventId) ?: return null
        if (!info.canModify) return null
        return when {
            !info.recurring -> moveSingle(eventId, newBegin, newEnd)
            scope == Scope.ALL -> moveSeries(eventId, oldBegin, newBegin, newEnd, info.allDay)
            else -> moveOccurrence(eventId, oldBegin, newBegin, newEnd, info.allDay)
        }
    }

    private fun moveSingle(eventId: Long, newBegin: Long, newEnd: Long): (() -> Unit)? {
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
        val before = ContentValues()
        resolver.query(uri, MOVE_COLUMNS, null, null, null)?.use {
            if (!it.moveToFirst()) return null
            putMoveColumns(it, before)
        } ?: return null
        val after =
            ContentValues(before).apply {
                put(Events.DTSTART, newBegin)
                put(Events.DTEND, newEnd)
            }
        if (resolver.update(uri, after, null, null) <= 0) return null
        return { resolver.update(uri, before, null, null) }
    }

    private fun moveSeries(
        eventId: Long,
        oldBegin: Long,
        newBegin: Long,
        newEnd: Long,
        allDay: Boolean,
    ): (() -> Unit)? {
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
        val before = ContentValues()
        val zone: String
        resolver.query(uri, SeriesWriter.TIME_COLUMNS, null, null, null)?.use {
            if (!it.moveToFirst()) return null
            SeriesWriter.putTimeColumns(it, before)
            zone = it.getString(4) ?: "UTC"
        } ?: return null
        val delta = newBegin - oldBegin
        val exdate =
            resolver.query(uri, arrayOf(Events.EXDATE), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        before.put(Events.EXDATE, exdate)
        val after =
            ContentValues(before).apply {
                put(Events.DTSTART, before.getAsLong(Events.DTSTART) + delta)
                put(Events.DURATION, IcsTime.formatDuration(newEnd - newBegin, allDay))
                put(
                    Events.RRULE,
                    shiftWeekday(before.getAsString(Events.RRULE), oldBegin, newBegin, zone),
                )
                put(Events.EXDATE, shiftExdates(exdate, delta))
            }
        shiftExceptions(eventId, delta)
        if (resolver.update(uri, after, null, null) <= 0) {
            shiftExceptions(eventId, -delta)
            return null
        }
        return {
            shiftExceptions(eventId, -delta)
            resolver.update(uri, before, null, null)
        }
    }

    private fun shiftExceptions(masterId: Long, delta: Long) {
        resolver
            .query(
                Events.CONTENT_URI,
                arrayOf(Events._ID, Events.ORIGINAL_INSTANCE_TIME) + MOVE_COLUMNS,
                "${Events.ORIGINAL_ID}=? AND ${Events.DELETED}=0",
                arrayOf(masterId.toString()),
                null,
            )
            ?.use {
                while (it.moveToNext()) {
                    resolver.update(
                        ContentUris.withAppendedId(Events.CONTENT_URI, it.getLong(0)),
                        ContentValues().apply {
                            put(Events.ORIGINAL_INSTANCE_TIME, it.getLong(1) + delta)
                            putMoveColumns(it, this, 2)
                        },
                        null,
                        null,
                    )
                }
            }
    }

    private fun moveOccurrence(
        masterId: Long,
        oldBegin: Long,
        newBegin: Long,
        newEnd: Long,
        allDay: Boolean,
    ): (() -> Unit)? {
        val overrides =
            ContentValues().apply {
                put(Events.DTSTART, newBegin)
                put(Events.DTEND, newEnd)
            }
        val created = createException(masterId, oldBegin, allDay, overrides) ?: return null
        return {
            resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, created.id), null, null)
            if (created.separate) SeriesWriter.removeExdate(resolver, masterId, oldBegin, allDay)
        }
    }

    data class Created(val id: Long, val separate: Boolean)

    fun createException(
        masterId: Long,
        occurrenceBegin: Long,
        allDay: Boolean,
        overrides: ContentValues,
        withReminders: Boolean = true,
    ): Created? {
        val values = ContentValues()
        var accountName = ""
        var accountType = ""
        resolver
            .query(
                ContentUris.withAppendedId(Events.CONTENT_URI, masterId),
                COPY_COLUMNS + ACCOUNT_COLUMNS,
                null,
                null,
                null,
            )
            ?.use {
                if (!it.moveToFirst()) return null
                for ((i, column) in COPY_COLUMNS.withIndex()) {
                    if (!it.isNull(i)) {
                        if (it.getType(i) == Cursor.FIELD_TYPE_INTEGER)
                            values.put(column, it.getLong(i))
                        else values.put(column, it.getString(i))
                    }
                }
                accountName = it.getString(COPY_COLUMNS.size) ?: ""
                accountType = it.getString(COPY_COLUMNS.size + 1) ?: ""
            } ?: return null
        values.putAll(overrides)

        val syncId = SeriesWriter.ensureSyncId(resolver, masterId, accountName, accountType)
        if (syncId == null) {
            SeriesWriter.addExdate(resolver, masterId, occurrenceBegin, allDay)
            val uri = resolver.insert(Events.CONTENT_URI, values) ?: return null
            val newId = ContentUris.parseId(uri)
            if (withReminders) copyReminders(masterId, newId)
            return Created(newId, separate = true)
        }
        values.put(Events.ORIGINAL_ID, masterId)
        values.put(Events.ORIGINAL_SYNC_ID, syncId)
        values.put(Events.ORIGINAL_INSTANCE_TIME, occurrenceBegin)
        values.put(Events.ORIGINAL_ALL_DAY, if (allDay) 1 else 0)
        if (!values.containsKey(Events.ALL_DAY)) values.put(Events.ALL_DAY, if (allDay) 1 else 0)
        val uri = resolver.insert(Events.CONTENT_URI, values) ?: return null
        val exceptionId = ContentUris.parseId(uri)
        if (withReminders) copyReminders(masterId, exceptionId)
        return Created(exceptionId, separate = false)
    }

    private fun copyReminders(from: Long, to: Long) {
        resolver
            .query(
                Reminders.CONTENT_URI,
                arrayOf(Reminders.MINUTES, Reminders.METHOD),
                "${Reminders.EVENT_ID}=?",
                arrayOf(from.toString()),
                null,
            )
            ?.use {
                while (it.moveToNext()) {
                    resolver.insert(
                        Reminders.CONTENT_URI,
                        ContentValues().apply {
                            put(Reminders.EVENT_ID, to)
                            put(Reminders.MINUTES, it.getInt(0))
                            put(Reminders.METHOD, it.getInt(1))
                        },
                    )
                }
            }
    }

    companion object {
        private val COPY_COLUMNS =
            arrayOf(
                Events.CALENDAR_ID,
                Events.TITLE,
                Events.DESCRIPTION,
                Events.EVENT_LOCATION,
                Events.EVENT_TIMEZONE,
                Events.ACCESS_LEVEL,
                Events.AVAILABILITY,
                Events.EVENT_COLOR,
                Events.HAS_ALARM,
                Events.ORGANIZER,
                Events.HAS_ATTENDEE_DATA,
                Events.STATUS,
            )
        private val ACCOUNT_COLUMNS = arrayOf(Events.ACCOUNT_NAME, Events.ACCOUNT_TYPE)

        private val MOVE_COLUMNS =
            arrayOf(Events.DTSTART, Events.DTEND, Events.ALL_DAY, Events.EVENT_TIMEZONE)

        private fun putMoveColumns(cursor: Cursor, values: ContentValues, offset: Int = 0) {
            values.put(Events.DTSTART, cursor.getLong(offset))
            values.put(Events.DTEND, cursor.getLong(offset + 1))
            values.put(Events.ALL_DAY, cursor.getInt(offset + 2))
            values.put(Events.EVENT_TIMEZONE, cursor.getString(offset + 3))
        }

        private val DAYS = listOf("SU", "MO", "TU", "WE", "TH", "FR", "SA")

        fun shiftExdates(exdate: String?, delta: Long): String? {
            if (exdate.isNullOrEmpty() || delta == 0L) return exdate
            val format =
                DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
            return exdate.split(',').joinToString(",") { value ->
                val v = value.trim()
                if (v.length == 16 && v.endsWith("Z")) {
                    format.format(Instant.from(format.parse(v)).plusMillis(delta))
                } else {
                    v
                }
            }
        }

        fun shiftWeekday(rrule: String?, oldBegin: Long, newBegin: Long, zone: String): String? {
            if (rrule == null) return null
            val tz = ZoneId.of(zone)
            val oldDay = DAYS[Instant.ofEpochMilli(oldBegin).atZone(tz).dayOfWeek.value % 7]
            val newDay = DAYS[Instant.ofEpochMilli(newBegin).atZone(tz).dayOfWeek.value % 7]
            if (oldDay == newDay) return rrule
            return rrule.split(';').joinToString(";") { part ->
                if (part.equals("BYDAY=$oldDay", ignoreCase = true)) "BYDAY=$newDay" else part
            }
        }
    }
}
