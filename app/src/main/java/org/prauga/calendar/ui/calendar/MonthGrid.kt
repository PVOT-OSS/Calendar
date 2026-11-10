/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.coerceAtMost
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
    val colors = MaterialTheme.colorScheme
    val rule = colors.outlineVariant.copy(alpha = 0.5f)
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
            .padding(bottom = contentPadding.calculateBottomPadding())
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            weekdays(firstDayOfWeek).forEach { day ->
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, locale),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        HorizontalDivider(color = rule)
        cells.chunked(7).forEach { week ->
            Row(Modifier.padding(horizontal = 8.dp)) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        DayCell(
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
            HorizontalDivider(color = rule)
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    items: List<Agenda>,
    isSelected: Boolean,
    isToday: Boolean,
    formats: Formats,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val count = pluralStringResource(R.plurals.month_day_events, items.size, items.size)
    val circle =
        when {
            isToday -> colors.primary
            isSelected -> colors.surfaceContainerHighest
            else -> Color.Transparent
        }
    Column(
        modifier
            .height(64.dp)
            .clearAndSetSemantics {
                contentDescription = "${formats.fullDate(date)}, $count"
                selected = isSelected
                onClick {
                    onClick()
                    true
                }
            }
            .clickable(onClick = onClick)
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(36.dp).background(circle, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color =
                    when {
                        isToday -> colors.onPrimary
                        date.dayOfWeek == DayOfWeek.SUNDAY -> colors.onSurfaceVariant
                        else -> colors.onSurface
                    },
            )
        }
        EventBar(items)
    }
}

@Composable
private fun EventBar(items: List<Agenda>) {
    if (items.isEmpty()) return
    val shown = items.sortedBy { it.begin }.take(6)
    Row(
        Modifier.width((6 + 6 * shown.size).dp.coerceAtMost(40.dp))
            .height(5.dp)
            .clip(RoundedCornerShape(50))
    ) {
        shown.forEach { Box(Modifier.weight(1f).fillMaxHeight().background(Color(it.color))) }
    }
}
