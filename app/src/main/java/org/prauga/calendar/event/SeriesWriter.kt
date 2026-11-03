/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.event

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object SeriesWriter {

    private val UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)

    fun syncId(resolver: ContentResolver, eventId: Long): String? = resolver.query(
        ContentUris.withAppendedId(Events.CONTENT_URI, eventId), arrayOf(Events._SYNC_ID), null, null, null
    )?.use { if (it.moveToFirst()) it.getString(0) else null }

    fun ensureSyncId(
        resolver: ContentResolver,
        eventId: Long,
        accountName: String,
        accountType: String,
        preferred: String? = null
    ): String? {
        syncId(resolver, eventId)?.let { return it }
        if (accountType != CalendarContract.ACCOUNT_TYPE_LOCAL) return null
        val syncId = preferred ?: "local-$eventId"
        resolver.update(
            asSyncAdapter(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), accountName, accountType),
            ContentValues().apply { put(Events._SYNC_ID, syncId) }, null, null
        )
        return syncId
    }

    fun formatExdate(millis: Long, allDay: Boolean): String =
        if (allDay) DATE.format(Instant.ofEpochMilli(millis)) else UTC.format(Instant.ofEpochMilli(millis))

    fun addExdate(resolver: ContentResolver, masterId: Long, instanceMillis: Long, allDay: Boolean) {
        val date = formatExdate(instanceMillis, allDay)
        updateExdates(resolver, masterId) { dates -> if (date in dates) dates else dates + date }
    }

    fun removeExdate(resolver: ContentResolver, masterId: Long, instanceMillis: Long, allDay: Boolean) {
        val date = formatExdate(instanceMillis, allDay)
        updateExdates(resolver, masterId) { dates -> dates - date }
    }

    private fun updateExdates(resolver: ContentResolver, masterId: Long, change: (List<String>) -> List<String>) {
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, masterId)
        val values = ContentValues()
        var current: String? = null
        resolver.query(uri, TIME_COLUMNS + Events.EXDATE, null, null, null)?.use {
            if (!it.moveToFirst()) return
            putTimeColumns(it, values)
            current = it.getString(TIME_COLUMNS.size)
        } ?: return
        val dates = current?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        val updated = change(dates)
        if (updated == dates) return
        values.put(Events.EXDATE, updated.takeIf { it.isNotEmpty() }?.joinToString(","))
        resolver.update(uri, values, null, null)
    }

    val TIME_COLUMNS = arrayOf(
        Events.DTSTART, Events.DURATION, Events.RRULE, Events.RDATE, Events.EVENT_TIMEZONE, Events.ALL_DAY
    )

    fun putTimeColumns(cursor: android.database.Cursor, values: ContentValues) {
        values.put(Events.DTSTART, cursor.getLong(0))
        values.put(Events.DURATION, cursor.getString(1))
        values.put(Events.RRULE, cursor.getString(2))
        values.put(Events.RDATE, cursor.getString(3))
        values.put(Events.EVENT_TIMEZONE, cursor.getString(4))
        values.put(Events.ALL_DAY, cursor.getInt(5))
    }

    fun asSyncAdapter(uri: Uri, accountName: String, accountType: String): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, accountName)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, accountType)
        .build()
}
