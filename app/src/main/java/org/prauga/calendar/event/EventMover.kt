/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.event

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import org.prauga.calendar.ics.IcsTime
import java.time.Instant
import java.time.ZoneId

class EventMover(private val resolver: ContentResolver) {

    data class Info(
        val eventId: Long,
        val canModify: Boolean,
        val recurring: Boolean,
        val allDay: Boolean
    )

    enum class Scope { THIS, ALL }

    fun info(eventId: Long): Info? = resolver.query(
        ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
        arrayOf(
            Events.CALENDAR_ACCESS_LEVEL, Events.ORGANIZER, Events.OWNER_ACCOUNT, Events.GUESTS_CAN_MODIFY,
            Events.RRULE, Events.RDATE, Events.ALL_DAY
        ), null, null, null
    )?.use {
        if (!it.moveToFirst()) return null
        val access = it.getInt(0)
        val isOrganizer = it.isNull(1) || it.getString(1).equals(it.getString(2), ignoreCase = true)
        Info(
            eventId = eventId,
            canModify = access >= Calendars.CAL_ACCESS_CONTRIBUTOR && (isOrganizer || it.getInt(3) != 0),
            recurring = !it.getString(4).isNullOrEmpty() || !it.getString(5).isNullOrEmpty(),
            allDay = it.getInt(6) != 0
        )
    }

    fun move(eventId: Long, oldBegin: Long, oldEnd: Long, newBegin: Long, newEnd: Long, scope: Scope): (() -> Unit)? {
        val info = info(eventId) ?: return null
        if (!info.canModify || info.allDay) return null
        return when {
            !info.recurring -> moveSingle(eventId, newBegin, newEnd)
            scope == Scope.ALL -> moveSeries(eventId, oldBegin, newBegin, newEnd)
            else -> moveOccurrence(eventId, oldBegin, newBegin, newEnd)
        }
    }

    private fun moveSingle(eventId: Long, newBegin: Long, newEnd: Long): (() -> Unit)? {
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
        val before = ContentValues()
        resolver.query(uri, arrayOf(Events.DTSTART, Events.DTEND), null, null, null)?.use {
            if (!it.moveToFirst()) return null
            before.put(Events.DTSTART, it.getLong(0))
            before.put(Events.DTEND, it.getLong(1))
        } ?: return null
        val after = ContentValues().apply {
            put(Events.DTSTART, newBegin)
            put(Events.DTEND, newEnd)
        }
        if (resolver.update(uri, after, null, null) <= 0) return null
        return { resolver.update(uri, before, null, null) }
    }

    private fun moveSeries(eventId: Long, oldBegin: Long, newBegin: Long, newEnd: Long): (() -> Unit)? {
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
        val before = ContentValues()
        val zone: String
        resolver.query(uri, SeriesWriter.TIME_COLUMNS, null, null, null)?.use {
            if (!it.moveToFirst()) return null
            SeriesWriter.putTimeColumns(it, before)
            zone = it.getString(4) ?: "UTC"
        } ?: return null
        val delta = newBegin - oldBegin
        val exdate = resolver.query(uri, arrayOf(Events.EXDATE), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
        before.put(Events.EXDATE, exdate)
        val after = ContentValues(before).apply {
            put(Events.DTSTART, before.getAsLong(Events.DTSTART) + delta)
            put(Events.DURATION, IcsTime.formatDuration(newEnd - newBegin, false))
            put(Events.RRULE, shiftWeekday(before.getAsString(Events.RRULE), oldBegin, newBegin, zone))
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
        resolver.query(
            Events.CONTENT_URI, arrayOf(Events._ID, Events.ORIGINAL_INSTANCE_TIME, Events.DTSTART, Events.DTEND),
            "${Events.ORIGINAL_ID}=? AND ${Events.DELETED}=0", arrayOf(masterId.toString()), null
        )?.use {
            while (it.moveToNext()) {
                resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, it.getLong(0)), ContentValues().apply {
                    put(Events.ORIGINAL_INSTANCE_TIME, it.getLong(1) + delta)
                    put(Events.DTSTART, it.getLong(2))
                    put(Events.DTEND, it.getLong(3))
                }, null, null)
            }
        }
    }

    private fun moveOccurrence(masterId: Long, oldBegin: Long, newBegin: Long, newEnd: Long): (() -> Unit)? {
        val master = ContentValues()
        var accountName = ""
        var accountType = ""
        resolver.query(ContentUris.withAppendedId(Events.CONTENT_URI, masterId), COPY_COLUMNS + ACCOUNT_COLUMNS, null, null, null)?.use {
            if (!it.moveToFirst()) return null
            for ((i, column) in COPY_COLUMNS.withIndex()) {
                if (!it.isNull(i)) {
                    if (it.getType(i) == android.database.Cursor.FIELD_TYPE_INTEGER) master.put(column, it.getLong(i))
                    else master.put(column, it.getString(i))
                }
            }
            accountName = it.getString(COPY_COLUMNS.size) ?: ""
            accountType = it.getString(COPY_COLUMNS.size + 1) ?: ""
        } ?: return null
        master.put(Events.DTSTART, newBegin)
        master.put(Events.DTEND, newEnd)

        val syncId = SeriesWriter.ensureSyncId(resolver, masterId, accountName, accountType)
        if (syncId == null) {
            SeriesWriter.addExdate(resolver, masterId, oldBegin, false)
            val uri = resolver.insert(Events.CONTENT_URI, master) ?: return null
            val newId = ContentUris.parseId(uri)
            copyReminders(masterId, newId)
            return {
                resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, newId), null, null)
                SeriesWriter.removeExdate(resolver, masterId, oldBegin, false)
            }
        }
        master.put(Events.ORIGINAL_ID, masterId)
        master.put(Events.ORIGINAL_SYNC_ID, syncId)
        master.put(Events.ORIGINAL_INSTANCE_TIME, oldBegin)
        master.put(Events.ORIGINAL_ALL_DAY, 0)
        val uri = resolver.insert(Events.CONTENT_URI, master) ?: return null
        val exceptionId = ContentUris.parseId(uri)
        copyReminders(masterId, exceptionId)
        return { resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, exceptionId), null, null) }
    }

    private fun copyReminders(from: Long, to: Long) {
        resolver.query(
            Reminders.CONTENT_URI, arrayOf(Reminders.MINUTES, Reminders.METHOD),
            "${Reminders.EVENT_ID}=?", arrayOf(from.toString()), null
        )?.use {
            while (it.moveToNext()) {
                resolver.insert(Reminders.CONTENT_URI, ContentValues().apply {
                    put(Reminders.EVENT_ID, to)
                    put(Reminders.MINUTES, it.getInt(0))
                    put(Reminders.METHOD, it.getInt(1))
                })
            }
        }
    }

    companion object {
        private val COPY_COLUMNS = arrayOf(
            Events.CALENDAR_ID, Events.TITLE, Events.DESCRIPTION, Events.EVENT_LOCATION, Events.EVENT_TIMEZONE,
            Events.ACCESS_LEVEL, Events.AVAILABILITY, Events.EVENT_COLOR, Events.HAS_ALARM, Events.ORGANIZER,
            Events.HAS_ATTENDEE_DATA, Events.STATUS
        )
        private val ACCOUNT_COLUMNS = arrayOf(Events.ACCOUNT_NAME, Events.ACCOUNT_TYPE)
        private val DAYS = listOf("SU", "MO", "TU", "WE", "TH", "FR", "SA")

        fun shiftExdates(exdate: String?, delta: Long): String? {
            if (exdate.isNullOrEmpty() || delta == 0L) return exdate
            val format = java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .withZone(java.time.ZoneOffset.UTC)
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
