/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.calendar

import org.prauga.calendar.data.Agenda

data class Slot(
    val agenda: Agenda,
    val startMinute: Int,
    val endMinute: Int,
    val column: Int,
    val columns: Int,
)

object TimelineLayout {

    const val MINUTES_PER_DAY = 24 * 60
    const val MIN_DURATION = 30

    fun layout(items: List<Agenda>, julianDay: Int): List<Slot> {
        val timed =
            items
                .filter { !it.allDay && it.occursOn(julianDay) }
                .map { a ->
                    val start = if (a.startDay < julianDay) 0 else a.startMinute
                    val end = if (a.endDay > julianDay) MINUTES_PER_DAY else a.endMinute
                    val shown = maxOf(end, start + MIN_DURATION).coerceAtMost(MINUTES_PER_DAY)
                    Triple(a, start.coerceAtMost(MINUTES_PER_DAY - MIN_DURATION), shown)
                }
                .sortedWith(compareBy<Triple<Agenda, Int, Int>>({ it.second }, { -it.third }))

        val result = ArrayList<Slot>(timed.size)
        var cluster = ArrayList<Pair<Triple<Agenda, Int, Int>, Int>>()
        val columnEnds = ArrayList<Int>()
        var clusterEnd = -1

        fun flush() {
            val columns = columnEnds.size
            cluster.forEach { (t, column) ->
                result += Slot(t.first, t.second, t.third, column, columns)
            }
            cluster = ArrayList()
            columnEnds.clear()
        }

        for (t in timed) {
            if (t.second >= clusterEnd) flush()
            val free = columnEnds.indexOfFirst { it <= t.second }
            val column = if (free >= 0) free else columnEnds.size.also { columnEnds += 0 }
            columnEnds[column] = t.third
            cluster += t to column
            clusterEnd = maxOf(clusterEnd, t.third)
        }
        flush()
        return result
    }
}
