/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.tasks

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import org.prauga.calendar.Event
import org.prauga.calendar.calendarcommon2.Time
import org.prauga.calendar.settings.GeneralPreferences
import java.util.concurrent.ConcurrentHashMap

object TasksRepository {
    private const val TAG = "TasksRepository"
    const val PREF_ENABLED = "preferences_show_tasks"

    data class Provider(val authority: String, val readPermission: String, val writePermission: String)

    val PROVIDERS = listOf(
        Provider("org.dmfs.tasks", "org.dmfs.permission.READ_TASKS", "org.dmfs.permission.WRITE_TASKS"),
        Provider("org.tasks.opentasks", "org.tasks.permission.READ_TASKS", "org.tasks.permission.WRITE_TASKS"),
    )

    private const val STATUS_COMPLETED = 2
    private const val STATUS_NEEDS_ACTION = 0

    private val tasksByEventId = ConcurrentHashMap<Long, Pair<Provider, Long>>()

    fun isEnabled(context: Context) =
        GeneralPreferences.getSharedPreferences(context).getBoolean(PREF_ENABLED, false)

    fun installedProviders(context: Context): List<Provider> =
        PROVIDERS.filter { context.packageManager.resolveContentProvider(it.authority, 0) != null }

    private fun canRead(context: Context, provider: Provider) =
        ContextCompat.checkSelfPermission(context, provider.readPermission) == PackageManager.PERMISSION_GRANTED

    private fun canWrite(context: Context, provider: Provider) =
        ContextCompat.checkSelfPermission(context, provider.writePermission) == PackageManager.PERMISSION_GRANTED

    fun isTask(eventId: Long) = eventId < 0 && tasksByEventId.containsKey(eventId)

    @JvmStatic
    fun appendTasks(context: Context, events: MutableList<Event>, startDay: Int, endDay: Int) {
        if (!isEnabled(context)) return
        val tz = org.prauga.calendar.Utils.getTimeZone(context, null)
        val time = Time(tz)
        val from = time.setJulianDay(startDay)
        val to = time.setJulianDay(endDay + 1)
        for (provider in installedProviders(context)) {
            if (!canRead(context, provider)) continue
            try {
                context.contentResolver.query(
                    Uri.parse("content://${provider.authority}/tasks"),
                    arrayOf("_id", "title", "due", "is_allday", "status", "list_color"),
                    "due >= ? AND due < ? AND _deleted = 0 AND visible = 1 AND rrule IS NULL",
                    arrayOf((from - DAY).toString(), (to + DAY).toString()), "due"
                )?.use { c ->
                    while (c.moveToNext()) {
                        val taskId = c.getLong(0)
                        val allDay = c.getInt(3) != 0
                        val due = c.getLong(2)
                        val done = c.getInt(4) == STATUS_COMPLETED
                        val event = taskEvent(provider, taskId, c.getString(1) ?: "", due, allDay, done, c.getInt(5), tz)
                        if (event.endDay >= startDay && event.startDay <= endDay) add(events, event)
                    }
                }
                appendRepeating(context, provider, events, startDay, endDay, from, to, tz)
            } catch (e: RuntimeException) {
                Log.w(TAG, "Couldn't read tasks from ${provider.authority}", e)
            }
        }
    }

    private fun appendRepeating(
        context: Context, provider: Provider, events: MutableList<Event>, startDay: Int, endDay: Int,
        from: Long, to: Long, tz: String
    ) {
        context.contentResolver.query(
            Uri.parse("content://${provider.authority}/tasks"),
            arrayOf("_id", "title", "dtstart", "due", "is_allday", "status", "list_color", "rrule", "tz"),
            "rrule IS NOT NULL AND due IS NOT NULL AND due < ? AND _deleted = 0 AND visible = 1",
            arrayOf((to + DAY).toString()), null
        )?.use { c ->
            while (c.moveToNext()) {
                val taskId = c.getLong(0)
                val due = c.getLong(3)
                val dtstart = if (c.isNull(2)) due else c.getLong(2)
                val allDay = c.getInt(4) != 0
                val done = c.getInt(5) == STATUS_COMPLETED
                val taskZone = if (allDay) "UTC" else c.getString(8) ?: tz
                occurrences(c.getString(7), dtstart, allDay, taskZone, to + DAY).forEachIndexed { index, start ->
                    val occurrenceDue = start + (due - dtstart)
                    if (occurrenceDue < due || occurrenceDue < from - DAY) return@forEachIndexed
                    val current = occurrenceDue == due
                    val event = taskEvent(
                        provider, taskId, c.getString(1) ?: "", occurrenceDue, allDay, current && done,
                        c.getInt(6), tz, if (current) 0 else index + 1
                    )
                    if (!current) futureOccurrences += event.id
                    if (event.endDay >= startDay && event.startDay <= endDay) add(events, event)
                }
            }
        }
    }

    fun occurrences(rrule: String, start: Long, allDay: Boolean, zone: String, until: Long): List<Long> {
        val result = mutableListOf<Long>()
        try {
            val rule = org.dmfs.rfc5545.recur.RecurrenceRule(rrule)
            val first = if (allDay) {
                val utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = start }
                org.dmfs.rfc5545.DateTime(utc.get(java.util.Calendar.YEAR), utc.get(java.util.Calendar.MONTH), utc.get(java.util.Calendar.DAY_OF_MONTH))
            } else {
                org.dmfs.rfc5545.DateTime(java.util.TimeZone.getTimeZone(zone), start)
            }
            val set = org.dmfs.rfc5545.iterable.RecurrenceSet(first, org.dmfs.rfc5545.iterable.instanceiterable.RuleInstances(rule))
            for (instance in set) {
                val t = instance.timestamp
                if (t >= until || result.size >= 1000) break
                result += t
            }
        } catch (e: Exception) {
            Log.w(TAG, "Bad task RRULE $rrule", e)
        }
        return result
    }

    private val futureOccurrences = java.util.Collections.newSetFromMap(ConcurrentHashMap<Long, Boolean>())

    fun canToggle(eventId: Long) = isTask(eventId) && eventId !in futureOccurrences

    private fun add(events: MutableList<Event>, event: Event) {
        if (event.drawAsAllday()) {
            events += event
            return
        }
        var index = events.indexOfFirst { it.drawAsAllday() || it.startMillis > event.startMillis }
        if (index < 0) index = events.size
        events.add(index, event)
    }

    private fun taskEvent(
        provider: Provider, taskId: Long, title: String, due: Long, allDay: Boolean, done: Boolean,
        color: Int, tz: String, occurrence: Int = 0
    ): Event {
        val id = -((provider.authority.hashCode().toLong() and 0xffff) * 1_000_000_000_000L +
            taskId * 10_000L + occurrence + 1)
        tasksByEventId[id] = provider to taskId
        val e = Event()
        e.id = id
        e.title = (if (done) "☑ " else "☐ ") + title
        e.location = ""
        e.color = color
        e.allDay = allDay
        e.isRepeating = false
        e.status = 1
        e.selfAttendeeStatus = 0
        val time = Time(if (allDay) Time.TIMEZONE_UTC else tz)
        if (allDay) {
            time.set(due)
            val local = Time(tz)
            local.set(0, 0, 0, time.day, time.month, time.year)
            e.startMillis = local.normalize()
            e.endMillis = e.startMillis + DAY
            e.startDay = Time.getJulianDay(e.startMillis, local.gmtOffset)
            e.endDay = e.startDay
            e.startTime = 0
            e.endTime = 24 * 60
        } else {
            time.set(due)
            e.startMillis = due - 30 * 60_000L
            e.endMillis = due
            e.startDay = Time.getJulianDay(e.startMillis, time.gmtOffset)
            e.endDay = Time.getJulianDay(e.endMillis - 1, time.gmtOffset)
            val start = Time(tz).apply { set(e.startMillis) }
            e.startTime = start.hour * 60 + start.minute
            e.endTime = e.startTime + 30
        }
        return e
    }

    @JvmStatic
    fun mergeIntoAgenda(context: Context, cursor: android.database.Cursor, startDay: Int, endDay: Int): android.database.Cursor {
        if (!isEnabled(context)) return cursor
        val tasks = mutableListOf<Event>()
        appendTasks(context, tasks, startDay, endDay)
        if (tasks.isEmpty()) return cursor
        val columns = cursor.columnNames
        val rows = ArrayList<Array<Any?>>()
        while (cursor.moveToNext()) {
            rows += Array(columns.size) { i ->
                when (cursor.getType(i)) {
                    android.database.Cursor.FIELD_TYPE_NULL -> null
                    android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                    android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                    else -> cursor.getString(i)
                }
            }
        }
        cursor.close()
        val utc = Time(Time.TIMEZONE_UTC)
        for (task in tasks) {
            val begin = if (task.allDay) utc.setJulianDay(task.startDay) else task.startMillis
            val end = if (task.allDay) begin + DAY else task.endMillis
            rows += Array(columns.size) { i ->
                when (columns[i]) {
                    android.provider.CalendarContract.Instances._ID, android.provider.CalendarContract.Instances.EVENT_ID -> task.id
                    android.provider.CalendarContract.Instances.TITLE -> task.title.toString()
                    android.provider.CalendarContract.Instances.EVENT_LOCATION -> ""
                    android.provider.CalendarContract.Instances.ALL_DAY -> if (task.allDay) 1L else 0L
                    android.provider.CalendarContract.Instances.HAS_ALARM -> 0L
                    android.provider.CalendarContract.Instances.DISPLAY_COLOR -> task.color.toLong()
                    android.provider.CalendarContract.Instances.BEGIN -> begin
                    android.provider.CalendarContract.Instances.END -> end
                    android.provider.CalendarContract.Instances.START_DAY -> task.startDay.toLong()
                    android.provider.CalendarContract.Instances.END_DAY -> task.endDay.toLong()
                    android.provider.CalendarContract.Instances.STATUS -> 1L
                    android.provider.CalendarContract.Instances.SELF_ATTENDEE_STATUS -> 0L
                    android.provider.CalendarContract.Instances.CAN_ORGANIZER_RESPOND -> 1L
                    android.provider.CalendarContract.Instances.OWNER_ACCOUNT,
                    android.provider.CalendarContract.Instances.ORGANIZER -> ""
                    android.provider.CalendarContract.Instances.EVENT_TIMEZONE -> if (task.allDay) "UTC" else org.prauga.calendar.Utils.getTimeZone(context, null)
                    else -> null
                }
            }
        }
        val dayIndex = columns.indexOf(android.provider.CalendarContract.Instances.START_DAY)
        val beginIndex = columns.indexOf(android.provider.CalendarContract.Instances.BEGIN)
        val titleIndex = columns.indexOf(android.provider.CalendarContract.Instances.TITLE)
        rows.sortWith(compareBy<Array<Any?>>({ (it[dayIndex] as? Number)?.toLong() ?: 0L },
            { (it[beginIndex] as? Number)?.toLong() ?: 0L }, { it[titleIndex]?.toString() ?: "" }))
        val merged = android.database.MatrixCursor(columns, rows.size)
        rows.forEach { merged.addRow(it) }
        return merged
    }

    fun open(context: Context, eventId: Long): Boolean {
        val (provider, taskId) = tasksByEventId[eventId] ?: return false
        val uri = ContentUris.withAppendedId(Uri.parse("content://${provider.authority}/tasks"), taskId)
        val view = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(view)
            true
        } catch (e: RuntimeException) {
            val pkg = context.packageManager.resolveContentProvider(provider.authority, 0)?.packageName ?: return false
            context.packageManager.getLaunchIntentForPackage(pkg)?.let {
                context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } ?: false
        }
    }

    fun toggleDone(context: Context, eventId: Long): Boolean {
        val (provider, taskId) = tasksByEventId[eventId] ?: return false
        if (!canToggle(eventId) || !canWrite(context, provider)) return false
        val uri = ContentUris.withAppendedId(Uri.parse("content://${provider.authority}/tasks"), taskId)
        return try {
            val done = context.contentResolver.query(uri, arrayOf("status"), null, null, null)
                ?.use { it.moveToFirst() && it.getInt(0) == STATUS_COMPLETED } ?: return false
            context.contentResolver.update(uri, ContentValues().apply {
                put("status", if (done) STATUS_NEEDS_ACTION else STATUS_COMPLETED)
            }, null, null) > 0
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't update task", e)
            false
        }
    }

    private const val DAY = 24 * 3600_000L
}
