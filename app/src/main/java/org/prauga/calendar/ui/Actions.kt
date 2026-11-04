/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import org.prauga.calendar.AboutActivity
import org.prauga.calendar.CalendarController
import org.prauga.calendar.data.Agenda
import org.prauga.calendar.settings.SettingsActivity
import org.prauga.calendar.tasks.TasksRepository
import org.prauga.calendar.ui.details.DetailsActivity
import org.prauga.calendar.ui.edit.EditorActivity
import org.prauga.calendar.ui.search.SearchActivity
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object Actions {

    fun open(context: Context, agenda: Agenda) {
        if (agenda.isTask) {
            TasksRepository.open(context, agenda.eventId)
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(Events.CONTENT_URI, agenda.eventId))
            .setClass(context, DetailsActivity::class.java)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, agenda.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, agenda.end)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, agenda.allDay)
        context.startActivity(intent)
    }

    fun create(context: Context, date: LocalDate, zone: ZoneId) {
        val now = ZonedDateTime.now(zone)
        val start = if (date == now.toLocalDate()) {
            now.toLocalTime().withMinute(0).withSecond(0).withNano(0).plusHours(1)
                .let { if (it == LocalTime.MIDNIGHT) LocalTime.of(23, 0) else it }
        } else {
            LocalTime.of(9, 0)
        }
        create(context, date, start, zone)
    }

    fun create(context: Context, date: LocalDate, start: LocalTime, zone: ZoneId) {
        val begin = date.atTime(start).atZone(zone).toInstant().toEpochMilli()
        context.startActivity(
            Intent(context, EditorActivity::class.java)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 3_600_000L)
        )
    }

    fun join(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
        }
    }

    fun sync(context: Context) = CalendarController.getInstance(context).refreshCalendars()

    fun search(context: Context) = context.startActivity(Intent(context, SearchActivity::class.java))

    fun settings(context: Context) = context.startActivity(Intent(context, SettingsActivity::class.java))

    fun about(context: Context) = context.startActivity(Intent(context, AboutActivity::class.java))
}
