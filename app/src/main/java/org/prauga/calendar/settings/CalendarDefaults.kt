/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.settings

import android.content.Context
import android.content.SharedPreferences
import android.text.format.DateFormat
import androidx.preference.PreferenceDataStore
import java.util.Calendar
import java.util.TimeZone

object CalendarDefaults {
    const val INHERIT = -2
    const val INHERIT_STRING = "-2"

    val ALL_DAY_REMINDERS = intArrayOf(
        GeneralPreferences.NO_REMINDER, 0, 15 * 60, 7 * 60, 2 * 24 * 60 - 9 * 60, 7 * 24 * 60 - 9 * 60
    )

    fun reminderKey(calendarId: Long) = "calendar_${calendarId}_default_reminder"

    fun allDayReminderKey(calendarId: Long) = "calendar_${calendarId}_default_allday_reminder"

    private fun prefs(context: Context): SharedPreferences = GeneralPreferences.getSharedPreferences(context)

    fun reminderMinutes(context: Context, calendarId: Long, allDay: Boolean): Int {
        val prefs = prefs(context)
        if (allDay) {
            prefs.getString(allDayReminderKey(calendarId), INHERIT_STRING)?.toIntOrNull()
                ?.takeIf { it != INHERIT }?.let { return it }
        }
        prefs.getString(reminderKey(calendarId), INHERIT_STRING)?.toIntOrNull()
            ?.takeIf { it != INHERIT }?.let { return it }
        return prefs.getString(GeneralPreferences.KEY_DEFAULT_REMINDER, GeneralPreferences.NO_REMINDER_STRING)
            ?.toIntOrNull() ?: GeneralPreferences.NO_REMINDER
    }

    class Store(context: Context) : PreferenceDataStore() {
        private val prefs = prefs(context)

        override fun putString(key: String, value: String?) {
            prefs.edit().putString(key, value).apply()
        }

        override fun getString(key: String, defValue: String?): String? = prefs.getString(key, defValue)
    }
}

fun allDayReminderLabel(context: Context, minutes: Int): String {
    val days = if (minutes <= 0) 0 else (minutes + 24 * 60 - 1) / (24 * 60)
    val minuteOfDay = days * 24 * 60 - minutes
    val time = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = minuteOfDay * 60_000L
    }
    val format = DateFormat.getTimeFormat(context).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val at = format.format(time.time)
    return when {
        days == 0 -> context.getString(org.prauga.calendar.R.string.allday_reminder_on_day, at)
        days % 7 == 0 -> context.resources.getQuantityString(org.prauga.calendar.R.plurals.allday_reminder_weeks_before, days / 7, days / 7, at)
        else -> context.resources.getQuantityString(org.prauga.calendar.R.plurals.allday_reminder_days_before, days, days, at)
    }
}
