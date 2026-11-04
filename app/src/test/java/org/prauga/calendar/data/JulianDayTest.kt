/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class JulianDayTest {

    @Test
    fun matchesTheProviderJulianDays() {
        assertEquals(2461359, JulianDay.of(LocalDate.of(2026, 11, 14)))
        assertEquals(2440588, JulianDay.of(LocalDate.of(1970, 1, 1)))
    }

    @Test
    fun roundTrips() {
        val date = LocalDate.of(2024, 2, 29)
        assertEquals(date, JulianDay.toDate(JulianDay.of(date)))
    }
}
