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
                    "due >= ? AND due < ? AND _deleted = 0 AND visible = 1",
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
            } catch (e: RuntimeException) {
                Log.w(TAG, "Couldn't read tasks from ${provider.authority}", e)
            }
        }
    }

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
        color: Int, tz: String
    ): Event {
        val id = -(provider.authority.hashCode().toLong() and 0xffff) * 10_000_000L - taskId - 1
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
        if (!canWrite(context, provider)) return false
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
