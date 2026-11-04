/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import org.prauga.calendar.R
import org.prauga.calendar.data.Agenda
import org.prauga.calendar.data.JulianDay
import org.prauga.calendar.ui.Formats

fun weekdays(firstDayOfWeek: DayOfWeek): List<DayOfWeek> =
    (0L until 7L).map { firstDayOfWeek.plus(it) }

fun monthCells(month: YearMonth, firstDayOfWeek: DayOfWeek): List<LocalDate?> {
    val lead = (month.atDay(1).dayOfWeek.value - firstDayOfWeek.value + 7) % 7
    val cells = ArrayList<LocalDate?>()
    repeat(lead) { cells += null }
    for (day in 1..month.lengthOfMonth()) cells += month.atDay(day)
    while (cells.size % 7 != 0) cells += null
    return cells
}

@Composable
fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    agendas: List<Agenda>,
    formats: Formats,
    onOpenDay: (LocalDate) -> Unit,
    onSwipe: (forward: Boolean) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val locale = Locale.getDefault()
    val cells = remember(month, firstDayOfWeek) { monthCells(month, firstDayOfWeek) }
    val byDay =
        remember(agendas) {
            val map = HashMap<Int, MutableList<Agenda>>()
            agendas.forEach { a ->
                (a.startDay..a.endDay).forEach { map.getOrPut(it) { ArrayList() } += a }
            }
            map
        }
    val swipePx = with(LocalDensity.current) { 72.dp.toPx() }
    Column(
        modifier
            .fillMaxSize()
            .pointerInput(month) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (abs(total) > swipePx) onSwipe(total < 0) },
                    onHorizontalDrag = { _, delta -> total += delta },
                )
            }
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .padding(bottom = contentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            weekdays(firstDayOfWeek).forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.SHORT, locale),
                    modifier =
                        Modifier.weight(1f)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        DayTile(
                            date = date,
                            items = byDay[JulianDay.of(date)].orEmpty(),
                            isSelected = date == selected,
                            isToday = date == today,
                            formats = formats,
                            onClick = { onOpenDay(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayTile(
    date: LocalDate,
    items: List<Agenda>,
    isSelected: Boolean,
    isToday: Boolean,
    formats: Formats,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val container = if (isSelected) colors.primary else colors.surfaceContainerHigh
    val content = if (isSelected) colors.onPrimary else colors.onSurface
    val shape = RoundedCornerShape(12.dp)
    val count = pluralStringResource(R.plurals.month_day_events, items.size, items.size)
    Column(
        modifier
            .aspectRatio(0.72f)
            .clip(shape)
            .background(container)
            .let { if (isToday && !isSelected) it.border(1.5.dp, colors.primary, shape) else it }
            .clearAndSetSemantics {
                contentDescription = "${formats.fullDate(date)}, $count"
                selected = isSelected
                onClick {
                    onClick()
                    true
                }
            }
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            items
                .distinctBy { it.color }
                .take(3)
                .forEach { Box(Modifier.size(7.dp).background(Color(it.color), CircleShape)) }
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
            color = content,
        )
    }
}
