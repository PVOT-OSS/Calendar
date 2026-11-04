/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar

import android.content.Context
import android.icu.text.DateFormat
import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import org.prauga.calendar.settings.GeneralPreferences
import java.util.Locale

object AlternateCalendar {
    const val PREF = "preferences_alternate_calendar"
    const val NONE = "none"

    val TYPES = listOf("chinese", "dangi", "hebrew", "islamic-umalqura", "persian", "indian", "ethiopic")

    @Volatile private var cachedType: String? = null

    fun type(context: Context): String {
        cachedType?.let { return it }
        val type = GeneralPreferences.getSharedPreferences(context).getString(PREF, NONE) ?: NONE
        cachedType = type
        return type
    }

    fun invalidate() {
        cachedType = null
        formats.clear()
    }

    fun isEnabled(context: Context) = type(context) != NONE

    fun displayName(type: String): String =
        ULocale("@calendar=$type").getDisplayKeywordValue("calendar", ULocale.getDefault())
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

    private val formats = HashMap<String, DateFormat>()

    fun label(context: Context, year: Int, month: Int, day: Int, short: Boolean): String? {
        val type = type(context)
        if (type == NONE) return null
        return label(type, year, month, day, short)
    }

    fun label(type: String, year: Int, month: Int, day: Int, short: Boolean): String {
        val calendar = Calendar.getInstance(TimeZone.GMT_ZONE, ULocale("${ULocale.getDefault()}@calendar=$type"))
        val gregorian = java.util.GregorianCalendar(java.util.TimeZone.getTimeZone("GMT")).apply {
            clear()
            set(year, month, day, 12, 0)
        }
        calendar.timeInMillis = gregorian.timeInMillis
        val skeleton = when {
            !short -> "yMMMMd"
            calendar.get(Calendar.DAY_OF_MONTH) == 1 -> "MMMd"
            else -> "d"
        }
        val format = synchronized(formats) {
            formats.getOrPut("$type/$skeleton") {
                DateFormat.getInstanceForSkeleton(calendar, skeleton, ULocale.getDefault()).apply {
                    timeZone = TimeZone.GMT_ZONE
                }
            }
        }
        return synchronized(format) { format.format(calendar.time) }
    }
}
