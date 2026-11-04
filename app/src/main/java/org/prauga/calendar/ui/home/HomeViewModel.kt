/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.prauga.calendar.Utils
import org.prauga.calendar.data.Agenda
import org.prauga.calendar.data.AgendaDetails
import org.prauga.calendar.data.AgendaRepository
import org.prauga.calendar.data.JulianDay
import org.prauga.calendar.ui.Formats

data class HomeDay(val date: LocalDate, val items: List<Agenda>)

data class HomeState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val eventsThisWeek: Int = 0,
    val tasksThisWeek: Int = 0,
    val weekendPlan: String? = null,
    val days: List<HomeDay> = emptyList(),
    val details: Map<Long, AgendaDetails> = emptyMap(),
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    val repository = AgendaRepository(application)
    private val formats = Formats(application)

    val state: StateFlow<HomeState> = run {
        val today = formats.today()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek()))
        val startDay = JulianDay.of(weekStart)
        val days = (JulianDay.of(today.plusDays(UPCOMING_DAYS)) - startDay) + 1
        repository
            .agendas(startDay, days)
            .map { build(it, today, weekStart) }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                HomeState(today = today),
            )
    }

    private suspend fun build(
        all: List<Agenda>,
        today: LocalDate,
        weekStart: LocalDate,
    ): HomeState {
        val now = System.currentTimeMillis()
        val weekDays = JulianDay.of(weekStart)..JulianDay.of(weekStart.plusDays(6))
        val thisWeek = all.filter { it.startDay in weekDays && !it.declined }
        val todayDay = JulianDay.of(today)
        fun ahead(a: Agenda) = if (a.allDay) a.endDay >= todayDay else a.end > now
        val saturday =
            if (today.dayOfWeek == DayOfWeek.SUNDAY) today.minusDays(1)
            else today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY))
        val weekend = JulianDay.of(saturday)..JulianDay.of(saturday.plusDays(1))
        val weekendPlan =
            all.firstOrNull { a ->
                    !a.isTask && !a.declined && ahead(a) && weekend.any { a.occursOn(it) }
                }
                ?.title

        val upcoming =
            all.filter { ahead(it) && !it.declined }
                .groupBy { maxOf(it.startDay, todayDay) }
                .toSortedMap()
                .map { (day, items) -> HomeDay(JulianDay.toDate(day), items) }
        val shown = upcoming.flatMap { it.items }.take(DETAILS_LIMIT)
        return HomeState(
            loading = false,
            today = today,
            eventsThisWeek = thisWeek.count { !it.isTask },
            tasksThisWeek = thisWeek.count { it.isTask },
            weekendPlan = weekendPlan,
            days = upcoming,
            details = repository.details(shown.map { it.eventId }),
        )
    }

    private fun firstDayOfWeek(): DayOfWeek {
        val timeDay = Utils.getFirstDayOfWeek(getApplication())
        return DayOfWeek.SUNDAY.plus(timeDay.toLong())
    }

    private companion object {
        const val UPCOMING_DAYS = 14L
        const val DETAILS_LIMIT = 40
    }
}
