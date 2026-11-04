/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ics

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.TimeZone

data class IcsDateTime(val millis: Long, val allDay: Boolean, val timeZone: String)

object IcsTime {
    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val UTC_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    fun parse(
        property: IcsProperty,
        defaultTimeZone: String,
        onUnknownZone: (String) -> Unit = {},
    ): IcsDateTime? {
        val value = property.value.trim()
        val isDate =
            property.param("VALUE") == "DATE" || (value.length == 8 && value.all(Char::isDigit))
        return parseValue(value, isDate, property.param("TZID"), defaultTimeZone, onUnknownZone)
    }

    fun parseValue(
        value: String,
        isDate: Boolean,
        tzid: String?,
        defaultTimeZone: String,
        onUnknownZone: (String) -> Unit = {},
    ): IcsDateTime? =
        try {
            if (isDate) {
                val date = LocalDate.parse(value.take(8), DATE)
                IcsDateTime(
                    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                    true,
                    "UTC",
                )
            } else if (value.endsWith("Z") || value.endsWith("z")) {
                val time = LocalDateTime.parse(value.dropLast(1).take(15), DATE_TIME)
                IcsDateTime(time.toInstant(ZoneOffset.UTC).toEpochMilli(), false, "UTC")
            } else {
                var zone = tzid?.let { resolveZone(it) }
                if (tzid != null && zone == null) onUnknownZone(tzid)
                if (zone == null) zone = defaultTimeZone
                val time = LocalDateTime.parse(value.take(15), DATE_TIME)
                IcsDateTime(time.atZone(ZoneId.of(zone)).toInstant().toEpochMilli(), false, zone)
            }
        } catch (e: RuntimeException) {
            null
        }

    fun resolveZone(tzid: String): String? {
        val id = tzid.trim().removeSurrounding("\"")
        if (id.isEmpty()) return null
        if (isKnown(id)) return canonical(id)
        val parts = id.split('/').filter { it.isNotEmpty() }
        for (start in parts.indices) {
            val candidate = parts.drop(start).joinToString("/")
            if (candidate.contains('/') && isKnown(candidate)) return canonical(candidate)
        }
        windowsZone(id)?.let {
            return it
        }
        return null
    }

    private fun windowsZone(id: String): String? =
        try {
            android.icu.util.TimeZone.getIDForWindowsID(id, "001")?.takeIf { it.isNotEmpty() }
        } catch (e: Throwable) {
            null
        }

    private fun isKnown(id: String): Boolean =
        id == "UTC" || id == "GMT" || TimeZone.getAvailableIDs().contains(id)

    private fun canonical(id: String): String = if (id == "GMT") "UTC" else id

    fun parseDuration(value: String): Long? {
        val match = DURATION.matchEntire(value.trim().uppercase()) ?: return null
        val (sign, weeks, days, hours, minutes, seconds) = match.destructured
        if (
            weeks.isEmpty() &&
                days.isEmpty() &&
                hours.isEmpty() &&
                minutes.isEmpty() &&
                seconds.isEmpty()
        ) {
            return null
        }
        val total =
            (weeks.toLongOrNull() ?: 0) * 7 * DAY +
                (days.toLongOrNull() ?: 0) * DAY +
                (hours.toLongOrNull() ?: 0) * HOUR +
                (minutes.toLongOrNull() ?: 0) * MINUTE +
                (seconds.toLongOrNull() ?: 0) * SECOND
        return if (sign == "-") -total else total
    }

    fun formatDuration(millis: Long, allDay: Boolean): String {
        return if (allDay && millis % DAY == 0L) "P${millis / DAY}D" else "P${millis / SECOND}S"
    }

    fun formatUtc(millis: Long): String =
        UTC_FORMAT.format(LocalDateTime.ofEpochSecond(millis / 1000, 0, ZoneOffset.UTC))

    private val DURATION =
        Regex("""([+-]?)P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?""")

    const val SECOND = 1000L
    const val MINUTE = 60 * SECOND
    const val HOUR = 60 * MINUTE
    const val DAY = 24 * HOUR
}
