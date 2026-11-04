/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ics

import android.content.ContentValues
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

data class IcsAttendee(val email: String, val name: String?, val type: Int, val status: Int)

data class IcsAlarm(val minutes: Int, val method: Int)

data class IcsEvent(
    val uid: String?,
    val recurrenceId: IcsDateTime?,
    val sequence: Int,
    val title: String?,
    val location: String?,
    val description: String?,
    val url: String?,
    val organizer: String?,
    val attendees: List<IcsAttendee>,
    val start: IcsDateTime,
    val end: IcsDateTime,
    val rrule: String?,
    val rdates: List<IcsDateTime>,
    val exdates: List<IcsDateTime>,
    val cancelled: Boolean,
    val tentative: Boolean,
    val transparent: Boolean,
    val accessLevel: Int,
    val alarms: List<IcsAlarm>,
) {
    val allDay: Boolean
        get() = start.allDay

    val isRecurring: Boolean
        get() = rrule != null || rdates.isNotEmpty()

    val isException: Boolean
        get() = recurrenceId != null

    fun toEventValues(calendarId: Long): ContentValues =
        ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId)
            put(Events.TITLE, title ?: "")
            put(Events.EVENT_LOCATION, location ?: "")
            put(Events.DESCRIPTION, description ?: "")
            put(Events.DTSTART, start.millis)
            put(Events.EVENT_TIMEZONE, start.timeZone)
            put(Events.ALL_DAY, if (allDay) 1 else 0)
            if (isRecurring) {
                put(Events.DURATION, IcsTime.formatDuration(end.millis - start.millis, allDay))
                putNull(Events.DTEND)
                put(Events.RRULE, rrule)
                put(
                    Events.RDATE,
                    rdates.takeIf { it.isNotEmpty() }?.joinToString(",") { formatListDate(it) },
                )
                put(
                    Events.EXDATE,
                    exdates.takeIf { it.isNotEmpty() }?.joinToString(",") { formatListDate(it) },
                )
            } else {
                put(Events.DTEND, end.millis)
                putNull(Events.DURATION)
                putNull(Events.RRULE)
                if (!allDay && end.timeZone != start.timeZone)
                    put(Events.EVENT_END_TIMEZONE, end.timeZone)
            }
            uid?.let { put(Events.UID_2445, it) }
            organizer?.let { put(Events.ORGANIZER, it) }
            put(Events.HAS_ATTENDEE_DATA, 1)
            put(Events.HAS_ALARM, if (alarms.isEmpty()) 0 else 1)
            put(
                Events.AVAILABILITY,
                if (transparent) Events.AVAILABILITY_FREE else Events.AVAILABILITY_BUSY,
            )
            put(Events.ACCESS_LEVEL, accessLevel)
            put(
                Events.STATUS,
                when {
                    cancelled -> Events.STATUS_CANCELED
                    tentative -> Events.STATUS_TENTATIVE
                    else -> Events.STATUS_CONFIRMED
                },
            )
        }

    private fun formatListDate(date: IcsDateTime): String =
        if (date.allDay) IcsTime.formatUtc(date.millis).take(8) else IcsTime.formatUtc(date.millis)

    companion object {
        fun from(
            component: IcsComponent,
            defaultTimeZone: String,
            onWarning: (String) -> Unit = {},
        ): IcsEvent? {
            val startProp = component.first("DTSTART") ?: return null
            var start = IcsTime.parse(startProp, defaultTimeZone, onWarning) ?: return null
            var end =
                component.first("DTEND")?.let { IcsTime.parse(it, defaultTimeZone, onWarning) }
            if (end == null) {
                val duration = component.text("DURATION")?.let { IcsTime.parseDuration(it) }
                end =
                    when {
                        duration != null -> start.copy(millis = start.millis + duration)
                        start.allDay -> start.copy(millis = start.millis + IcsTime.DAY)
                        else -> start
                    }
            }
            if (end.millis < start.millis) end = start

            if (
                !start.allDay && component.text("X-MICROSOFT-CDO-ALLDAYEVENT").equals("TRUE", true)
            ) {
                start = toAllDay(start)
                end =
                    toAllDay(end).let {
                        if (it.millis <= start.millis)
                            start.copy(millis = start.millis + IcsTime.DAY)
                        else it
                    }
            }

            val status = component.text("STATUS")?.uppercase()
            val transp = component.text("TRANSP")?.uppercase()
            val access =
                when (component.text("CLASS")?.uppercase()) {
                    "PRIVATE" -> Events.ACCESS_PRIVATE
                    "CONFIDENTIAL" -> Events.ACCESS_CONFIDENTIAL
                    "PUBLIC" -> Events.ACCESS_PUBLIC
                    else -> Events.ACCESS_DEFAULT
                }
            return IcsEvent(
                uid = component.text("UID")?.trim()?.takeIf { it.isNotEmpty() },
                recurrenceId =
                    component.first("RECURRENCE-ID")?.let {
                        IcsTime.parse(it, start.timeZone, onWarning)
                    },
                sequence = component.text("SEQUENCE")?.trim()?.toIntOrNull() ?: 0,
                title = component.text("SUMMARY"),
                location = component.text("LOCATION"),
                description = component.text("DESCRIPTION"),
                url = component.text("URL")?.trim()?.takeIf { it.isNotEmpty() },
                organizer = component.first("ORGANIZER")?.let { emailOf(it.value) },
                attendees = component.all("ATTENDEE").mapNotNull { attendee(it) },
                start = start,
                end = end,
                rrule = component.first("RRULE")?.value?.trim()?.takeIf { it.isNotEmpty() },
                rdates = dateList(component.all("RDATE"), start, onWarning),
                exdates = dateList(component.all("EXDATE"), start, onWarning),
                cancelled = status == "CANCELLED",
                tentative = status == "TENTATIVE",
                transparent = transp == "TRANSPARENT",
                accessLevel = access,
                alarms =
                    component
                        .components("VALARM")
                        .mapNotNull { alarm(it, start, end, onWarning) }
                        .distinct(),
            )
        }

        private fun toAllDay(time: IcsDateTime): IcsDateTime {
            val local =
                Instant.ofEpochMilli(time.millis).atZone(ZoneId.of(time.timeZone)).toLocalDate()
            return IcsDateTime(
                local.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                true,
                "UTC",
            )
        }

        private fun dateList(
            props: List<IcsProperty>,
            start: IcsDateTime,
            onWarning: (String) -> Unit,
        ): List<IcsDateTime> =
            props.flatMap { prop ->
                val isDate = prop.param("VALUE") == "DATE"
                prop.value.split(',').mapNotNull { value ->
                    val v = value.trim()
                    if (v.isEmpty() || prop.param("VALUE") == "PERIOD" || v.contains('/')) null
                    else
                        IcsTime.parseValue(
                            v,
                            isDate || (v.length == 8 && start.allDay),
                            prop.param("TZID"),
                            start.timeZone,
                            onWarning,
                        )
                }
            }

        private fun emailOf(value: String): String? {
            val v = value.trim()
            val email = if (v.startsWith("mailto:", ignoreCase = true)) v.substring(7) else v
            return email.trim().takeIf { it.isNotEmpty() }
        }

        private fun attendee(prop: IcsProperty): IcsAttendee? {
            val email = emailOf(prop.value) ?: prop.param("EMAIL") ?: return null
            val type =
                when {
                    prop.param("CUTYPE").equals("RESOURCE", true) ||
                        prop.param("CUTYPE").equals("ROOM", true) -> Attendees.TYPE_RESOURCE
                    prop.param("ROLE").equals("OPT-PARTICIPANT", true) ||
                        prop.param("ROLE").equals("NON-PARTICIPANT", true) ->
                        Attendees.TYPE_OPTIONAL
                    else -> Attendees.TYPE_REQUIRED
                }
            val status =
                when (prop.param("PARTSTAT")?.uppercase()) {
                    "ACCEPTED" -> Attendees.ATTENDEE_STATUS_ACCEPTED
                    "DECLINED" -> Attendees.ATTENDEE_STATUS_DECLINED
                    "TENTATIVE" -> Attendees.ATTENDEE_STATUS_TENTATIVE
                    "NEEDS-ACTION" -> Attendees.ATTENDEE_STATUS_INVITED
                    else -> Attendees.ATTENDEE_STATUS_NONE
                }
            return IcsAttendee(email, prop.param("CN"), type, status)
        }

        private fun alarm(
            component: IcsComponent,
            start: IcsDateTime,
            end: IcsDateTime,
            onWarning: (String) -> Unit,
        ): IcsAlarm? {
            val trigger = component.first("TRIGGER") ?: return null
            val fireAt =
                if (trigger.param("VALUE") == "DATE-TIME") {
                    IcsTime.parse(trigger, start.timeZone, onWarning)?.millis ?: return null
                } else {
                    val offset = IcsTime.parseDuration(trigger.value) ?: return null
                    val base =
                        if (trigger.param("RELATED").equals("END", true)) end.millis
                        else start.millis
                    base + offset
                }
            val before = start.millis - fireAt
            if (before < 0) return null
            val method =
                if (component.text("ACTION").equals("EMAIL", true)) Reminders.METHOD_EMAIL
                else Reminders.METHOD_ALERT
            return IcsAlarm((before / IcsTime.MINUTE).toInt(), method)
        }
    }
}

data class IcsCalendar(
    val method: String?,
    val events: List<IcsEvent>,
    val warnings: List<String>,
) {
    companion object {
        fun parse(text: String, defaultTimeZone: String): IcsCalendar {
            val warnings = linkedSetOf<String>()
            var method: String? = null
            val events = mutableListOf<IcsEvent>()
            for (root in IcsParser.parse(text)) {
                val calendars = if (root.name == "VCALENDAR") listOf(root) else emptyList()
                for (calendar in calendars) {
                    if (method == null) method = calendar.text("METHOD")?.trim()?.uppercase()
                    calendar.components("VEVENT").mapNotNullTo(events) {
                        IcsEvent.from(it, defaultTimeZone) { zone -> warnings += zone }
                    }
                }
                if (root.name == "VEVENT") {
                    IcsEvent.from(root, defaultTimeZone) { zone -> warnings += zone }
                        ?.let { events += it }
                }
            }
            return IcsCalendar(method, events, warnings.toList())
        }
    }
}
