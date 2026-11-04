/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.edit

import org.prauga.calendar.tasks.TasksRepository
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object SeriesSplit {

    data class Result(val before: String, val from: String)

    private val UTC_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private val UTC_DATE = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)

    fun split(rrule: String, dtStart: Long, allDay: Boolean, zone: String, occurrence: Long): Result {
        val parts = rrule.split(';').filter { it.isNotBlank() }
        fun key(part: String) = part.substringBefore('=').uppercase()
        val count = parts.firstOrNull { key(it) == "COUNT" }?.substringAfter('=')?.toIntOrNull()
        if (count != null) {
            val done = TasksRepository.occurrences(rrule, dtStart, allDay, zone, occurrence).size
            val rest = parts.filter { key(it) != "COUNT" }
            return Result(
                before = (rest + "COUNT=$done").joinToString(";"),
                from = (rest + "COUNT=${(count - done).coerceAtLeast(1)}").joinToString(";"),
            )
        }
        val until = if (allDay) UTC_DATE.format(Instant.ofEpochMilli(occurrence).minusSeconds(86_400))
        else UTC_TIME.format(Instant.ofEpochMilli(occurrence - 1000))
        return Result(
            before = (parts.filter { key(it) != "UNTIL" } + "UNTIL=$until").joinToString(";"),
            from = rrule,
        )
    }
}
