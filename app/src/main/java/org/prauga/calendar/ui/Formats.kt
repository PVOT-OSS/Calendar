/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import org.prauga.calendar.R
import org.prauga.calendar.Utils
import org.prauga.calendar.data.Agenda

class Formats(private val context: Context) {

    val zone: ZoneId =
        runCatching { ZoneId.of(Utils.getTimeZone(context, null)) }
            .getOrDefault(ZoneId.systemDefault())

    private val locale = Locale.getDefault()
    private val is24 = DateFormat.is24HourFormat(context)
    private val clock = DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "hh:mm a", locale)
    private val hourOnly = DateTimeFormatter.ofPattern("h", locale)
    private val amPm = DateTimeFormatter.ofPattern("a", locale)
    private val shortDate = DateTimeFormatter.ofPattern("EEE, d MMM", locale)
    private val fullDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)

    fun today(): LocalDate = LocalDate.now(zone)

    fun time(millis: Long): String = clock.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun hourParts(hour: Int): Pair<String, String> {
        val time = LocalTime.of(hour, 0)
        return if (is24) clock.format(time) to "" else hourOnly.format(time) to amPm.format(time)
    }

    fun timeRange(agenda: Agenda): String =
        when {
            agenda.allDay -> ""
            agenda.isTask -> time(agenda.end)
            else -> "${time(agenda.begin)} - ${time(agenda.end)}"
        }

    fun dayLabel(date: LocalDate, today: LocalDate = today()): String =
        when (date) {
            today -> context.getString(R.string.ui_today)
            today.plusDays(1) -> context.getString(R.string.ui_tomorrow)
            today.minusDays(1) -> context.getString(R.string.ui_yesterday)
            else -> shortDate.format(date)
        }

    fun fullDate(date: LocalDate): String = fullDate.format(date)
}
