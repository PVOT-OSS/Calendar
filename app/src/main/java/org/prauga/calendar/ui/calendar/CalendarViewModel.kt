/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.prauga.calendar.Utils
import org.prauga.calendar.data.Agenda
import org.prauga.calendar.data.AgendaRepository
import org.prauga.calendar.data.JulianDay
import org.prauga.calendar.ui.Formats
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

enum class CalendarMode { Day, Week, Month, Year }

data class DaySpan(val first: LocalDate, val last: LocalDate) {
    val days: Int get() = (last.toEpochDay() - first.toEpochDay() + 1).toInt()
}

data class CalendarState(
    val mode: CalendarMode = CalendarMode.Week,
    val selected: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val span: DaySpan = DaySpan(selected, selected),
    val agendas: List<Agenda> = emptyList(),
) {
    fun on(date: LocalDate): List<Agenda> {
        val day = JulianDay.of(date)
        return agendas.filter { it.occursOn(day) }
    }
}

class CalendarViewModel(application: Application, private val saved: SavedStateHandle) :
    AndroidViewModel(application) {

    private val repository = AgendaRepository(application)
    private val formats = Formats(application)
    private val today = formats.today()
    private val firstDayOfWeek = DayOfWeek.SUNDAY.plus(Utils.getFirstDayOfWeek(application).toLong())

    private val position = MutableStateFlow(
        Position(
            mode = saved.get<String>(KEY_MODE)?.let { CalendarMode.valueOf(it) } ?: CalendarMode.Week,
            selected = saved.get<Long>(KEY_DATE)?.let { LocalDate.ofEpochDay(it) } ?: today,
        )
    )

    private data class Position(val mode: CalendarMode, val selected: LocalDate)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<CalendarState> = position
        .flatMapLatest { p ->
            val span = spanOf(p.mode, p.selected)
            repository.agendas(JulianDay.of(span.first), span.days).map { list ->
                CalendarState(p.mode, p.selected, today, firstDayOfWeek, span, list)
            }
        }
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000),
            CalendarState(position.value.mode, position.value.selected, today, firstDayOfWeek),
        )

    val selected: StateFlow<LocalDate> = position.map { it.selected }
        .stateIn(viewModelScope, SharingStarted.Eagerly, position.value.selected)

    private fun move(p: Position) {
        position.value = p
        saved[KEY_MODE] = p.mode.name
        saved[KEY_DATE] = p.selected.toEpochDay()
    }

    fun setMode(mode: CalendarMode) = move(position.value.copy(mode = mode))

    fun select(date: LocalDate) = move(position.value.copy(selected = date))

    fun goToday() = select(today)

    fun openDay(date: LocalDate) = move(Position(CalendarMode.Day, date))

    fun openMonth(month: YearMonth) {
        val day = position.value.selected.dayOfMonth.coerceAtMost(month.lengthOfMonth())
        move(Position(CalendarMode.Month, month.atDay(day)))
    }

    fun step(forward: Boolean) {
        val n = if (forward) 1L else -1L
        val p = position.value
        select(
            when (p.mode) {
                CalendarMode.Day -> p.selected.plusDays(n)
                CalendarMode.Week -> p.selected.plusWeeks(n)
                CalendarMode.Month -> p.selected.plusMonths(n)
                CalendarMode.Year -> p.selected.plusYears(n)
            }
        )
    }

    fun refresh() = repository.refresh()

    private fun spanOf(mode: CalendarMode, date: LocalDate): DaySpan = when (mode) {
        CalendarMode.Day -> DaySpan(date, date)
        CalendarMode.Week -> weekOf(date)
        CalendarMode.Month -> {
            val month = YearMonth.from(date)
            DaySpan(weekOf(month.atDay(1)).first, weekOf(month.atEndOfMonth()).last)
        }
        CalendarMode.Year -> DaySpan(date.withDayOfYear(1), date.withDayOfYear(date.lengthOfYear()))
    }

    fun weekOf(date: LocalDate): DaySpan {
        val first = date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
        return DaySpan(first, first.plusDays(6))
    }

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_DATE = "date"
    }
}
