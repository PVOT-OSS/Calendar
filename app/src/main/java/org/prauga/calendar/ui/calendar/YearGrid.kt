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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.prauga.calendar.data.Agenda
import org.prauga.calendar.data.JulianDay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

@Composable
fun YearGrid(
    year: Int,
    focus: Month,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    agendas: List<Agenda>,
    onOpenMonth: (YearMonth) -> Unit,
    onSwipe: (forward: Boolean) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val busy = remember(agendas) {
        val days = HashSet<Int>()
        agendas.forEach { a -> (a.startDay..a.endDay).forEach { days += it } }
        days
    }
    val swipePx = with(LocalDensity.current) { 72.dp.toPx() }
    val grid = rememberLazyGridState(initialFirstVisibleItemIndex = (focus.ordinal / 2) * 2)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = grid,
        modifier = modifier
            .fillMaxSize()
            .pointerInput(year) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (abs(total) > swipePx) onSwipe(total < 0) },
                    onHorizontalDrag = { _, delta -> total += delta },
                )
            },
        contentPadding = PaddingValues(
            start = 12.dp, end = 12.dp, top = 4.dp, bottom = contentPadding.calculateBottomPadding(),
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(Month.entries) { month ->
            MiniMonth(YearMonth.of(year, month), today, firstDayOfWeek, busy) { onOpenMonth(YearMonth.of(year, month)) }
        }
    }
}

@Composable
private fun MiniMonth(
    month: YearMonth,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    busy: Set<Int>,
    onClick: () -> Unit,
) {
    val locale = Locale.getDefault()
    val name = month.month.getDisplayName(TextStyle.FULL, locale)
    val colors = MaterialTheme.colorScheme
    val isCurrent = YearMonth.from(today) == month
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceContainer)
            .clearAndSetSemantics {
                contentDescription = "$name ${month.year}"
                onClick { onClick(); true }
            }
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(start = 2.dp, bottom = 4.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (isCurrent) colors.primary else colors.onSurface,
        )
        monthCells(month, firstDayOfWeek).chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val isToday = date == today
                        val hasEvents = JulianDay.of(date) in busy
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    when {
                                        isToday -> colors.primary
                                        hasEvents -> colors.secondaryContainer
                                        else -> colors.surfaceContainerHigh
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                fontSize = 8.sp,
                                lineHeight = 8.sp,
                                color = when {
                                    isToday -> colors.onPrimary
                                    hasEvents -> colors.onSecondaryContainer
                                    else -> colors.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
