/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.tasks

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TasksRepositoryTest {

    private val week = 7 * 24 * 3600_000L

    @Test
    fun weeklyTaskRepeatsUntilTheEnd() {
        val start = 1794380400000L
        val list = TasksRepository.occurrences("FREQ=WEEKLY", start, false, "Asia/Kolkata", start + 3 * week + 1)
        assertEquals(listOf(start, start + week, start + 2 * week, start + 3 * week), list)
    }

    @Test
    fun allDayAndBadRules() {
        val day = 1794355200000L
        assertEquals(listOf(day, day + 24 * 3600_000L), TasksRepository.occurrences("FREQ=DAILY;COUNT=2", day, true, "UTC", day + week))
        assertEquals(emptyList<Long>(), TasksRepository.occurrences("FREQ=NONSENSE", day, true, "UTC", day + week))
    }
}
