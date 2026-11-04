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
    private val clock =
        DateTimeFormatter.ofPattern(
            if (DateFormat.is24HourFormat(context)) "HH:mm" else "hh:mm a",
            locale,
        )
    private val shortDate = DateTimeFormatter.ofPattern("EEE, d MMM", locale)
    private val fullDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)

    fun today(): LocalDate = LocalDate.now(zone)

    fun time(millis: Long): String = clock.format(Instant.ofEpochMilli(millis).atZone(zone))

    fun hour(hour: Int): String = clock.format(LocalTime.of(hour, 0))

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
