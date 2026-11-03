/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.holidays

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.util.Log
import org.prauga.calendar.settings.GeneralPreferences
import org.json.JSONObject
import org.prauga.calendar.R
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

object HolidaySync {
    private const val TAG = "HolidaySync"
    const val ACCOUNT_NAME = "Holidays"
    const val PREF_COUNTRIES = "preferences_holiday_countries"
    private const val NAME_PREFIX = "pvot_holidays_"
    private const val COLOR = 0xFF0B8043.toInt()

    data class Holiday(val date: LocalDate, val name: String)

    fun availableCountries(context: Context): List<String> {
        val json = JSONObject(readAsset(context, "holidays/index.json"))
        val countries = json.getJSONArray("countries")
        return (0 until countries.length()).map { countries.getString(it) }
    }

    fun countryName(code: String): String = Locale("", code).getDisplayCountry(Locale.getDefault())

    fun selectedCountries(context: Context): Set<String> =
        GeneralPreferences.getSharedPreferences(context).getStringSet(PREF_COUNTRIES, emptySet()) ?: emptySet()

    fun holidays(context: Context, code: String): List<Holiday> {
        val array = JSONObject(readAsset(context, "holidays/$code.json")).getJSONArray("holidays")
        return (0 until array.length()).mapNotNull {
            val entry = array.getJSONArray(it)
            runCatching { Holiday(LocalDate.parse(entry.getString(0)), entry.getString(1)) }.getOrNull()
        }
    }

    @Synchronized
    fun sync(context: Context) {
        val resolver = context.contentResolver
        val selected = selectedCountries(context)
        try {
            val existing = holidayCalendars(resolver)
            for ((code, id) in existing) {
                if (code !in selected) resolver.delete(asSyncAdapter(ContentUris.withAppendedId(Calendars.CONTENT_URI, id)), null, null)
            }
            for (code in selected) {
                val id = existing[code] ?: createCalendar(context, code) ?: continue
                apply(resolver, id, code, holidays(context, code))
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "Holiday sync failed", e)
        }
    }

    fun refreshIfNeeded(context: Context) {
        if (selectedCountries(context).isEmpty()) return
        val prefs = GeneralPreferences.getSharedPreferences(context)
        val version = readAsset(context, "holidays/index.json").hashCode()
        if (prefs.getInt(PREF_DATA_VERSION, 0) == version) return
        sync(context)
        prefs.edit().putInt(PREF_DATA_VERSION, version).apply()
    }

    private const val PREF_DATA_VERSION = "holidays_data_version"

    private fun holidayCalendars(resolver: ContentResolver): Map<String, Long> {
        val result = HashMap<String, Long>()
        resolver.query(
            Calendars.CONTENT_URI, arrayOf(Calendars._ID, Calendars.NAME),
            "${Calendars.ACCOUNT_TYPE}=? AND ${Calendars.ACCOUNT_NAME}=?",
            arrayOf(CalendarContract.ACCOUNT_TYPE_LOCAL, ACCOUNT_NAME), null
        )?.use {
            while (it.moveToNext()) {
                val name = it.getString(1) ?: continue
                if (name.startsWith(NAME_PREFIX)) result[name.removePrefix(NAME_PREFIX)] = it.getLong(0)
            }
        }
        return result
    }

    private fun createCalendar(context: Context, code: String): Long? {
        val uri = context.contentResolver.insert(asSyncAdapter(Calendars.CONTENT_URI), ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, NAME_PREFIX + code)
            put(Calendars.CALENDAR_DISPLAY_NAME, context.getString(R.string.holidays_calendar_name, countryName(code)))
            put(Calendars.CALENDAR_COLOR, COLOR)
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_READ)
            put(Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, "UTC")
        }) ?: return null
        return ContentUris.parseId(uri)
    }

    private fun apply(resolver: ContentResolver, calendarId: Long, code: String, holidays: List<Holiday>) {
        val wanted = LinkedHashMap<String, Holiday>()
        for (h in holidays) {
            var key = "holiday-$code-${h.date}"
            var n = 1
            while (key in wanted) key = "holiday-$code-${h.date}-${n++}"
            wanted[key] = h
        }
        val existing = HashMap<String, Pair<Long, String?>>()
        resolver.query(
            Events.CONTENT_URI, arrayOf(Events._ID, Events._SYNC_ID, Events.TITLE),
            "${Events.CALENDAR_ID}=? AND ${Events.DELETED}=0", arrayOf(calendarId.toString()), null
        )?.use {
            while (it.moveToNext()) {
                val key = it.getString(1)
                if (key == null || key in existing) {
                    resolver.delete(asSyncAdapter(ContentUris.withAppendedId(Events.CONTENT_URI, it.getLong(0))), null, null)
                } else {
                    existing[key] = it.getLong(0) to it.getString(2)
                }
            }
        }
        for ((key, row) in existing) {
            if (key !in wanted) resolver.delete(asSyncAdapter(ContentUris.withAppendedId(Events.CONTENT_URI, row.first)), null, null)
        }
        for ((key, holiday) in wanted) {
            val row = existing[key]
            if (row == null) {
                resolver.insert(asSyncAdapter(Events.CONTENT_URI), values(calendarId, key, holiday))
            } else if (row.second != holiday.name) {
                resolver.update(asSyncAdapter(ContentUris.withAppendedId(Events.CONTENT_URI, row.first)), values(calendarId, key, holiday), null, null)
            }
        }
    }

    private fun values(calendarId: Long, key: String, holiday: Holiday) = ContentValues().apply {
        val start = holiday.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        put(Events.CALENDAR_ID, calendarId)
        put(Events._SYNC_ID, key)
        put(Events.TITLE, holiday.name)
        put(Events.DTSTART, start)
        put(Events.DTEND, start + 24 * 3600_000L)
        put(Events.ALL_DAY, 1)
        put(Events.EVENT_TIMEZONE, "UTC")
        put(Events.AVAILABILITY, Events.AVAILABILITY_FREE)
        put(Events.HAS_ALARM, 0)
    }

    private fun readAsset(context: Context, path: String): String =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun asSyncAdapter(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()
}
