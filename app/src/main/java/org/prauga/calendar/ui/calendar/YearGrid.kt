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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onOpenMonth: (YearMonth) -> Unit,
    onSwipe: (forward: Boolean) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val swipePx = with(LocalDensity.current) { 72.dp.toPx() }
    val grid = rememberLazyGridState(initialFirstVisibleItemIndex = (focus.ordinal / 3) * 3)
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = grid,
        modifier =
            modifier.fillMaxSize().pointerInput(year) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (abs(total) > swipePx) onSwipe(total < 0) },
                    onHorizontalDrag = { _, delta -> total += delta },
                )
            },
        contentPadding =
            PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = contentPadding.calculateBottomPadding(),
            ),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(Month.entries) { month ->
            MiniMonth(YearMonth.of(year, month), today, firstDayOfWeek) {
                onOpenMonth(YearMonth.of(year, month))
            }
        }
    }
}

@Composable
private fun MiniMonth(
    month: YearMonth,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    onClick: () -> Unit,
) {
    val locale = Locale.getDefault()
    val name = month.month.getDisplayName(TextStyle.FULL, locale)
    val colors = MaterialTheme.colorScheme
    val isCurrent = YearMonth.from(today) == month
    Column(
        Modifier.clip(RoundedCornerShape(8.dp))
            .clearAndSetSemantics {
                contentDescription = "$name ${month.year}"
                onClick {
                    onClick()
                    true
                }
            }
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isCurrent) colors.primary else colors.onSurface,
        )
        monthCells(month, firstDayOfWeek).chunked(7).forEach { week ->
            Row {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val isToday = date == today
                        Box(
                            Modifier.weight(1f)
                                .aspectRatio(1f)
                                .background(
                                    if (isToday) colors.primary else Color.Transparent,
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                fontSize = 9.sp,
                                lineHeight = 9.sp,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                color =
                                    when {
                                        isToday -> colors.onPrimary
                                        date.dayOfWeek == DayOfWeek.SUNDAY ->
                                            colors.onSurfaceVariant
                                        else -> colors.onSurface
                                    },
                            )
                        }
                    }
                }
            }
        }
    }
}
