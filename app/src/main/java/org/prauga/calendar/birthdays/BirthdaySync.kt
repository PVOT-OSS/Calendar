/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.birthdays

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.util.Log
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneOffset
import org.prauga.calendar.R

object BirthdaySync {
    private const val TAG = "BirthdaySync"
    const val ACCOUNT_NAME = "Contacts"
    private const val CALENDAR_NAME = "pvot_birthdays"
    private const val COLOR = 0xFF33B679.toInt()

    data class Entry(val uid: String, val title: String, val dtstart: Long)

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED

    @Synchronized
    fun sync(context: Context): Boolean {
        if (!hasPermission(context)) return false
        val resolver = context.contentResolver
        return try {
            val calendarId = calendarId(resolver) ?: createCalendar(context) ?: return false
            apply(resolver, calendarId, readContacts(context))
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Birthday sync failed", e)
            false
        }
    }

    fun remove(context: Context) {
        val resolver = context.contentResolver
        val id = calendarId(resolver) ?: return
        resolver.delete(
            asSyncAdapter(ContentUris.withAppendedId(Calendars.CONTENT_URI, id)),
            null,
            null,
        )
    }

    fun calendarId(resolver: ContentResolver): Long? =
        resolver
            .query(
                Calendars.CONTENT_URI,
                arrayOf(Calendars._ID),
                "${Calendars.ACCOUNT_TYPE}=? AND ${Calendars.ACCOUNT_NAME}=? AND ${Calendars.NAME}=?",
                arrayOf(CalendarContract.ACCOUNT_TYPE_LOCAL, ACCOUNT_NAME, CALENDAR_NAME),
                null,
            )
            ?.use { if (it.moveToFirst()) it.getLong(0) else null }

    private fun createCalendar(context: Context): Long? {
        val uri =
            context.contentResolver.insert(
                asSyncAdapter(Calendars.CONTENT_URI),
                ContentValues().apply {
                    put(Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
                    put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                    put(Calendars.NAME, CALENDAR_NAME)
                    put(
                        Calendars.CALENDAR_DISPLAY_NAME,
                        context.getString(R.string.birthdays_calendar_name),
                    )
                    put(Calendars.CALENDAR_COLOR, COLOR)
                    put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_READ)
                    put(Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
                    put(Calendars.VISIBLE, 1)
                    put(Calendars.SYNC_EVENTS, 1)
                    put(Calendars.CALENDAR_TIME_ZONE, "UTC")
                },
            ) ?: return null
        return ContentUris.parseId(uri)
    }

    private fun readContacts(context: Context): List<Entry> {
        val entries = mutableListOf<Entry>()
        context.contentResolver
            .query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data.LOOKUP_KEY,
                    ContactsContract.Data.DISPLAY_NAME,
                    CommonDataKinds.Event.START_DATE,
                    CommonDataKinds.Event.TYPE,
                    CommonDataKinds.Event.LABEL,
                ),
                "${ContactsContract.Data.MIMETYPE}=? AND ${CommonDataKinds.Event.TYPE} IN (?,?)",
                arrayOf(
                    CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                    CommonDataKinds.Event.TYPE_BIRTHDAY.toString(),
                    CommonDataKinds.Event.TYPE_ANNIVERSARY.toString(),
                ),
                null,
            )
            ?.use {
                while (it.moveToNext()) {
                    val key = it.getString(0) ?: continue
                    val name = it.getString(1)?.takeIf { n -> n.isNotBlank() } ?: continue
                    val date = parseDate(it.getString(2)) ?: continue
                    val birthday = it.getInt(3) == CommonDataKinds.Event.TYPE_BIRTHDAY
                    val title =
                        context.getString(
                            if (birthday) R.string.birthdays_title_birthday
                            else R.string.birthdays_title_anniversary,
                            name,
                        )
                    entries +=
                        Entry(
                            uid = "${if (birthday) "birthday" else "anniversary"}-$key",
                            title = title,
                            dtstart = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                        )
                }
            }
        return entries.distinctBy { it.uid }
    }

    fun parseDate(value: String?): LocalDate? {
        val v = value?.trim() ?: return null
        return try {
            when {
                v.startsWith("--") && v.length >= 7 ->
                    LocalDate.of(2000, v.substring(2, 4).toInt(), v.substring(5, 7).toInt())
                v.length >= 10 && v[4] == '-' ->
                    LocalDate.of(
                        v.substring(0, 4).toInt(),
                        v.substring(5, 7).toInt(),
                        v.substring(8, 10).toInt(),
                    )
                v.length == 8 && v.all(Char::isDigit) ->
                    LocalDate.of(
                        v.substring(0, 4).toInt(),
                        v.substring(4, 6).toInt(),
                        v.substring(6, 8).toInt(),
                    )
                else -> null
            }
        } catch (e: RuntimeException) {
            null
        }
    }

    private fun apply(resolver: ContentResolver, calendarId: Long, entries: List<Entry>) {
        val existing = HashMap<String, Triple<Long, String?, Long>>()
        resolver
            .query(
                Events.CONTENT_URI,
                arrayOf(Events._ID, Events._SYNC_ID, Events.TITLE, Events.DTSTART),
                "${Events.CALENDAR_ID}=? AND ${Events.DELETED}=0",
                arrayOf(calendarId.toString()),
                null,
            )
            ?.use {
                while (it.moveToNext()) {
                    val uid = it.getString(1) ?: continue
                    if (uid in existing) {
                        resolver.delete(
                            asSyncAdapter(
                                ContentUris.withAppendedId(Events.CONTENT_URI, it.getLong(0))
                            ),
                            null,
                            null,
                        )
                        continue
                    }
                    existing[uid] = Triple(it.getLong(0), it.getString(2), it.getLong(3))
                }
            }
        val wanted = entries.associateBy { it.uid }
        for ((uid, row) in existing) {
            if (uid !in wanted)
                resolver.delete(
                    asSyncAdapter(ContentUris.withAppendedId(Events.CONTENT_URI, row.first)),
                    null,
                    null,
                )
        }
        for (entry in entries) {
            val row = existing[entry.uid]
            if (row == null) {
                resolver.insert(asSyncAdapter(Events.CONTENT_URI), eventValues(calendarId, entry))
            } else if (row.second != entry.title || row.third != entry.dtstart) {
                resolver.update(
                    asSyncAdapter(ContentUris.withAppendedId(Events.CONTENT_URI, row.first)),
                    eventValues(calendarId, entry),
                    null,
                    null,
                )
            }
        }
    }

    private fun eventValues(calendarId: Long, entry: Entry) =
        ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId)
            put(Events._SYNC_ID, entry.uid)
            put(Events.UID_2445, entry.uid)
            put(Events.TITLE, entry.title)
            put(Events.DTSTART, entry.dtstart)
            put(Events.DURATION, "P1D")
            put(Events.ALL_DAY, 1)
            put(Events.EVENT_TIMEZONE, "UTC")
            put(Events.RRULE, "FREQ=YEARLY")
            put(Events.AVAILABILITY, Events.AVAILABILITY_FREE)
            put(Events.HAS_ALARM, 0)
        }

    private fun asSyncAdapter(uri: Uri): Uri =
        uri.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
}
