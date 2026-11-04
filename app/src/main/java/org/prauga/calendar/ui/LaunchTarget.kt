/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui

import android.content.Intent
import android.provider.CalendarContract
import org.prauga.calendar.Utils
import org.prauga.calendar.ui.calendar.CalendarMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class LaunchTarget(val date: LocalDate, val mode: CalendarMode?) {

    companion object {
        fun from(intent: Intent?, zone: ZoneId): LaunchTarget? {
            intent ?: return null
            val segments = intent.data?.pathSegments.orEmpty()
            val millis = when {
                intent.data?.authority == CalendarContract.AUTHORITY && segments.size == 2 && segments[0] == "time" ->
                    segments[1].toLongOrNull()
                intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0L) > 0 ->
                    intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0L)
                else -> null
            }
            val day = intent.getStringExtra(Utils.INTENT_KEY_VIEW_TYPE) == Utils.INTENT_VALUE_VIEW_TYPE_DAY
            if (millis == null && !day) return null
            val date = millis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: LocalDate.now(zone)
            return LaunchTarget(date, if (day) CalendarMode.Day else null)
        }
    }
}
