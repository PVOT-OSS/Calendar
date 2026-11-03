/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.event

import android.content.Context
import org.prauga.calendar.EventRecurrenceFormatter
import org.prauga.calendar.calendarcommon2.EventRecurrence
import org.prauga.calendar.calendarcommon2.Time
import org.prauga.calendar.R

object RepeatPresets {

    data class Preset(val rrule: String?, val label: String)

    private val DAYS = arrayOf("SU", "MO", "TU", "WE", "TH", "FR", "SA")

    fun rules(startMillis: Long, timeZone: String): List<String> {
        val start = Time(timeZone).apply { set(startMillis) }
        val weekday = DAYS[start.weekDay]
        val nth = (start.day - 1) / 7 + 1
        val daysInMonth = start.getActualMaximum(Time.MONTH_DAY)
        val ordinal = if (nth >= 5 || (nth == 4 && start.day + 7 > daysInMonth)) -1 else nth
        return listOf(
            "FREQ=DAILY",
            "FREQ=WEEKLY;BYDAY=$weekday",
            "FREQ=MONTHLY;BYDAY=$ordinal$weekday",
            "FREQ=YEARLY",
            "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"
        )
    }

    fun presets(context: Context, startMillis: Long, timeZone: String): List<Preset> {
        val start = Time(timeZone).apply { set(startMillis) }
        val presets = mutableListOf(Preset(null, context.getString(R.string.does_not_repeat)))
        for (rule in rules(startMillis, timeZone)) {
            val recurrence = EventRecurrence().apply {
                parse(rule)
                setStartDate(start)
            }
            val label = EventRecurrenceFormatter.getRepeatString(context, context.resources, recurrence, true)
                ?: continue
            presets += Preset(rule, label)
        }
        return presets
    }
}
