/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.persistence

import android.content.ContentResolver
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars

data class CalendarTarget(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    val color: Int
) {
    val isLocal: Boolean get() = accountType == CalendarContract.ACCOUNT_TYPE_LOCAL

    val label: String get() = if (displayName == accountName) displayName else "$displayName ($accountName)"
}

object WritableCalendars {
    fun list(resolver: ContentResolver): List<CalendarTarget> {
        val targets = mutableListOf<CalendarTarget>()
        resolver.query(
            Calendars.CONTENT_URI,
            arrayOf(
                Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME,
                Calendars.ACCOUNT_TYPE, Calendars.CALENDAR_COLOR
            ),
            "${Calendars.CALENDAR_ACCESS_LEVEL}>=${Calendars.CAL_ACCESS_CONTRIBUTOR} AND ${Calendars.SYNC_EVENTS}=1",
            null,
            "${Calendars.IS_PRIMARY} DESC, ${Calendars.ACCOUNT_NAME}, ${Calendars.CALENDAR_DISPLAY_NAME}"
        )?.use {
            while (it.moveToNext()) {
                targets += CalendarTarget(
                    it.getLong(0), it.getString(1) ?: it.getString(2) ?: "", it.getString(2) ?: "",
                    it.getString(3) ?: "", it.getInt(4)
                )
            }
        }
        return targets
    }
}
