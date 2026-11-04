/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ics

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.ExtendedProperties
import android.provider.CalendarContract.Reminders
import android.util.Log
import org.prauga.calendar.event.ExtendedProperty
import org.prauga.calendar.event.SeriesWriter
import org.prauga.calendar.persistence.CalendarTarget

enum class ImportAction { ADD, UPDATE, DELETE, SKIP }

data class ImportResult(val added: Int, val updated: Int, val deleted: Int, val failed: Int)

class IcsImporter(private val resolver: ContentResolver) {

    fun plan(events: List<IcsEvent>, method: String?, calendarId: Long): List<ImportAction> {
        val masters = HashMap<String, Long?>()
        return events.map { event ->
            val masterId = event.uid?.let { uid -> masters.getOrPut(uid) { findMaster(calendarId, uid) } }
            val existing = when {
                masterId == null -> null
                event.isException -> findException(masterId, event.recurrenceId!!.millis)
                else -> masterId
            }
            actionFor(event, method, existing != null)
        }
    }

    fun import(events: List<IcsEvent>, method: String?, target: CalendarTarget): ImportResult {
        var added = 0
        var updated = 0
        var deleted = 0
        var failed = 0
        val masterIds = HashMap<String, Long>()
        for (event in events.sortedBy { it.isException }) {
            try {
                val uid = event.uid
                val masterId = uid?.let { masterIds[it] ?: findMaster(target.id, it) }
                if (event.isException && masterId != null) {
                    val instance = event.recurrenceId!!
                    val existing = findException(masterId, instance.millis)
                    when (actionFor(event, method, existing != null)) {
                        ImportAction.DELETE -> {
                            delete(existing!!)
                            if (event.cancelled || method == "CANCEL") SeriesWriter.addExdate(resolver, masterId, instance.millis, instance.allDay)
                            deleted++
                        }
                        ImportAction.UPDATE -> { update(existing!!, event, target); updated++ }
                        ImportAction.ADD -> {
                            if (event.cancelled) SeriesWriter.addExdate(resolver, masterId, instance.millis, instance.allDay) else insertException(masterId, event, target)
                            added++
                        }
                        ImportAction.SKIP -> {}
                    }
                    continue
                }
                val standalone = if (event.isException) event.copy(recurrenceId = null) else event
                when (actionFor(standalone, method, masterId != null)) {
                    ImportAction.DELETE -> { delete(masterId!!); deleted++ }
                    ImportAction.UPDATE -> {
                        update(masterId!!, standalone, target)
                        uid?.let { masterIds[it] = masterId }
                        updated++
                    }
                    ImportAction.ADD -> {
                        val id = insert(standalone, target)
                        if (id != null) {
                            uid?.let { masterIds[it] = id }
                            added++
                        } else {
                            failed++
                        }
                    }
                    ImportAction.SKIP -> {}
                }
            } catch (e: RuntimeException) {
                Log.w(TAG, "Failed to import ${event.uid}", e)
                failed++
            }
        }
        return ImportResult(added, updated, deleted, failed)
    }

    private fun actionFor(event: IcsEvent, method: String?, exists: Boolean): ImportAction {
        val cancel = event.cancelled || method == "CANCEL"
        return when {
            cancel && exists -> ImportAction.DELETE
            cancel && event.isException -> ImportAction.ADD
            cancel -> ImportAction.SKIP
            exists -> ImportAction.UPDATE
            else -> ImportAction.ADD
        }
    }

    private fun findMaster(calendarId: Long, uid: String): Long? = resolver.query(
        Events.CONTENT_URI, arrayOf(Events._ID),
        "${Events.CALENDAR_ID}=? AND ${Events.UID_2445}=? AND ${Events.ORIGINAL_ID} IS NULL AND ${Events.DELETED}=0",
        arrayOf(calendarId.toString(), uid), null
    )?.use { if (it.moveToFirst()) it.getLong(0) else null }

    private fun findException(masterId: Long, instanceMillis: Long): Long? = resolver.query(
        Events.CONTENT_URI, arrayOf(Events._ID),
        "${Events.ORIGINAL_ID}=? AND ${Events.ORIGINAL_INSTANCE_TIME}=? AND ${Events.DELETED}=0",
        arrayOf(masterId.toString(), instanceMillis.toString()), null
    )?.use { if (it.moveToFirst()) it.getLong(0) else null }

    private fun insert(event: IcsEvent, target: CalendarTarget): Long? {
        val uri = resolver.insert(Events.CONTENT_URI, event.toEventValues(target.id)) ?: return null
        val id = ContentUris.parseId(uri)
        writeChildren(id, event, target)
        return id
    }

    private fun insertException(masterId: Long, event: IcsEvent, target: CalendarTarget) {
        val instance = event.recurrenceId!!
        val syncId = SeriesWriter.ensureSyncId(resolver, masterId, target.accountName, target.accountType, event.uid)
        if (syncId == null) {
            SeriesWriter.addExdate(resolver, masterId, instance.millis, instance.allDay)
            insert(event.copy(recurrenceId = null, uid = null), target)
            return
        }
        val values = event.toEventValues(target.id).apply {
            remove(Events.RRULE); remove(Events.RDATE); remove(Events.EXDATE); putNull(Events.DURATION)
            put(Events.DTEND, event.end.millis)
            put(Events.ORIGINAL_ID, masterId)
            put(Events.ORIGINAL_SYNC_ID, syncId)
            put(Events.ORIGINAL_INSTANCE_TIME, instance.millis)
            put(Events.ORIGINAL_ALL_DAY, if (instance.allDay) 1 else 0)
        }
        val uri = resolver.insert(Events.CONTENT_URI, values)
        if (uri != null) writeChildren(ContentUris.parseId(uri), event, target)
    }

    private fun update(id: Long, event: IcsEvent, target: CalendarTarget) {
        val values = event.toEventValues(target.id)
        if (event.isException) {
            values.remove(Events.RRULE); values.remove(Events.RDATE); values.remove(Events.EXDATE)
            values.remove(Events.DURATION)
            values.put(Events.DTEND, event.end.millis)
        }
        resolver.update(ContentUris.withAppendedId(Events.CONTENT_URI, id), values, null, null)
        resolver.delete(Attendees.CONTENT_URI, "${Attendees.EVENT_ID}=?", arrayOf(id.toString()))
        resolver.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID}=?", arrayOf(id.toString()))
        writeChildren(id, event, target)
    }

    private fun delete(id: Long) {
        resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, id), null, null)
    }

    private fun writeChildren(eventId: Long, event: IcsEvent, target: CalendarTarget) {
        event.organizer?.let { organizer ->
            if (event.attendees.none { it.email.equals(organizer, true) }) {
                resolver.insert(Attendees.CONTENT_URI, ContentValues().apply {
                    put(Attendees.EVENT_ID, eventId)
                    put(Attendees.ATTENDEE_EMAIL, organizer)
                    put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ORGANIZER)
                    put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_REQUIRED)
                    put(Attendees.ATTENDEE_STATUS, Attendees.ATTENDEE_STATUS_ACCEPTED)
                })
            }
        }
        for (attendee in event.attendees) {
            resolver.insert(Attendees.CONTENT_URI, ContentValues().apply {
                put(Attendees.EVENT_ID, eventId)
                put(Attendees.ATTENDEE_EMAIL, attendee.email)
                attendee.name?.let { put(Attendees.ATTENDEE_NAME, it) }
                put(
                    Attendees.ATTENDEE_RELATIONSHIP,
                    if (attendee.email.equals(event.organizer, true)) Attendees.RELATIONSHIP_ORGANIZER
                    else Attendees.RELATIONSHIP_ATTENDEE
                )
                put(Attendees.ATTENDEE_TYPE, attendee.type)
                put(Attendees.ATTENDEE_STATUS, attendee.status)
            })
        }
        for (alarm in event.alarms) {
            resolver.insert(Reminders.CONTENT_URI, ContentValues().apply {
                put(Reminders.EVENT_ID, eventId)
                put(Reminders.MINUTES, alarm.minutes)
                put(Reminders.METHOD, alarm.method)
            })
        }
        event.url?.let { url ->
            val uri = ExtendedProperty.contentUri(target.accountName, target.accountType)
            resolver.delete(
                uri, "${ExtendedProperties.EVENT_ID}=? AND ${ExtendedProperties.NAME}=?",
                arrayOf(eventId.toString(), ExtendedProperty.URL_NAME)
            )
            resolver.insert(uri, ContentValues().apply {
                put(ExtendedProperties.EVENT_ID, eventId)
                put(ExtendedProperties.NAME, ExtendedProperty.URL_NAME)
                put(ExtendedProperties.VALUE, url)
            })
        }
    }

    companion object {
        private const val TAG = "IcsImporter"
    }
}
