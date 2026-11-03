/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.birthdays

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class BirthdaySyncTest {

    @Test
    fun parsesContactDateForms() {
        assertEquals(LocalDate.of(1990, 11, 20), BirthdaySync.parseDate("1990-11-20"))
        assertEquals(LocalDate.of(2000, 2, 29), BirthdaySync.parseDate("--02-29"))
        assertEquals(LocalDate.of(1985, 1, 2), BirthdaySync.parseDate("19850102"))
        assertEquals(LocalDate.of(1990, 11, 20), BirthdaySync.parseDate("1990-11-20T00:00:00Z"))
        assertNull(BirthdaySync.parseDate("next tuesday"))
        assertNull(BirthdaySync.parseDate("1990-13-40"))
        assertNull(BirthdaySync.parseDate(null))
    }
}
