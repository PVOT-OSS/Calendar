/*
 * SPDX-FileCopyrightText: 2026 Shivansh Pandey <shivanshp987@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.delay
import org.prauga.calendar.data.Agenda
import org.prauga.calendar.data.JulianDay
import org.prauga.calendar.ui.Formats

private val WeekHourHeight = 60.dp
private val Gutter = 52.dp

@Composable
fun WeekGrid(
    days: List<LocalDate>,
    today: LocalDate,
    agendas: List<Agenda>,
    formats: Formats,
    onOpenDay: (LocalDate) -> Unit,
    onOpen: (Agenda) -> Unit,
    onCreate: (LocalDate, LocalTime) -> Unit,
    onSwipe: (forward: Boolean) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val rule = colors.outlineVariant.copy(alpha = 0.45f)
    val density = LocalDensity.current
    val hourPx = with(density) { WeekHourHeight.toPx() }
    val swipePx = with(density) { 72.dp.toPx() }
    val byDay =
        remember(agendas, days) {
            days.map { date ->
                val jd = JulianDay.of(date)
                val items = agendas.filter { it.occursOn(jd) }
                items.filter { it.allDay } to TimelineLayout.layout(items, jd)
            }
        }
    val scroll = rememberScrollState()
    val earliest = byDay.flatMap { it.second }.minOfOrNull { it.startMinute }
    LaunchedEffect(days.firstOrNull(), earliest == null) {
        val minute =
            when {
                today in days -> LocalTime.now(formats.zone).let { it.hour * 60 + it.minute } - 60
                earliest != null -> earliest - 30
                else -> 8 * 60
            }.coerceAtLeast(0)
        scroll.scrollTo((minute / 60f * hourPx).toInt())
    }

    Column(
        modifier.fillMaxSize().pointerInput(days.firstOrNull()) {
            var total = 0f
            detectHorizontalDragGestures(
                onDragStart = { total = 0f },
                onDragEnd = { if (abs(total) > swipePx) onSwipe(total < 0) },
                onHorizontalDrag = { _, delta -> total += delta },
            )
        }
    ) {
        DayHeader(days, today, formats, onOpenDay)
        if (byDay.any { it.first.isNotEmpty() }) AllDayRow(byDay.map { it.first }, onOpen)
        HorizontalDivider(color = rule)
        Box(
            Modifier.fillMaxWidth()
                .weight(1f)
                .verticalScroll(scroll)
                .padding(bottom = contentPadding.calculateBottomPadding())
        ) {
            HourLabels(formats)
            BoxWithConstraints(
                Modifier.padding(start = Gutter)
                    .fillMaxWidth()
                    .height(WeekHourHeight * 24)
                    .drawBehind {
                        val column = size.width / days.size
                        for (hour in 0..24) {
                            val y = hour * hourPx
                            drawLine(rule, Offset(0f, y), Offset(size.width, y), 1f)
                        }
                        for (day in 0..days.size) {
                            val x = day * column
                            drawLine(rule, Offset(x, 0f), Offset(x, size.height), 1f)
                        }
                    }
                    .pointerInput(days.firstOrNull()) {
                        detectTapGestures { offset ->
                            val day = (offset.x / (size.width / days.size)).toInt()
                            val hour = (offset.y / hourPx).toInt().coerceIn(0, 23)
                            days.getOrNull(day)?.let { onCreate(it, LocalTime.of(hour, 0)) }
                        }
                    }
            ) {
                val column = maxWidth / days.size
                byDay.forEachIndexed { index, (_, slots) ->
                    slots.forEach { slot ->
                        val width = column / slot.columns
                        WeekCard(
                            agenda = slot.agenda,
                            time = formats.timeRange(slot.agenda),
                            onClick = { onOpen(slot.agenda) },
                            modifier =
                                Modifier.offset(
                                        x = column * index + width * slot.column + 1.dp,
                                        y = WeekHourHeight * (slot.startMinute / 60f) + 1.dp,
                                    )
                                    .width(width - 2.dp)
                                    .height(
                                        WeekHourHeight *
                                            ((slot.endMinute - slot.startMinute) / 60f) - 2.dp
                                    ),
                        )
                    }
                }
                val todayIndex = days.indexOf(today)
                if (todayIndex >= 0) NowMark(formats, column * todayIndex, column)
            }
        }
    }
}

@Composable
private fun DayHeader(
    days: List<LocalDate>,
    today: LocalDate,
    formats: Formats,
    onOpenDay: (LocalDate) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val locale = Locale.getDefault()
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp)) {
        Spacer(Modifier.width(Gutter))
        days.forEach { date ->
            val isToday = date == today
            Column(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clearAndSetSemantics {
                        contentDescription = formats.fullDate(date)
                        onClick {
                            onOpenDay(date)
                            true
                        }
                    }
                    .clickable { onOpenDay(date) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = date.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isToday) colors.primary else colors.onSurfaceVariant,
                )
                Box(
                    Modifier.size(36.dp)
                        .background(
                            if (isToday) colors.primary else Color.Transparent,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                        color =
                            when {
                                isToday -> colors.onPrimary
                                date.dayOfWeek == DayOfWeek.SUNDAY -> colors.onSurfaceVariant
                                else -> colors.onSurface
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun AllDayRow(perDay: List<List<Agenda>>, onOpen: (Agenda) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Spacer(Modifier.width(Gutter))
        perDay.forEach { items ->
            Column(
                Modifier.weight(1f).padding(horizontal = 1.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items.firstOrNull()?.let { first ->
                    val tint = eventTint(first.color)
                    Text(
                        text = first.title,
                        modifier =
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(tint.container(strong = true))
                                .clickable { onOpen(first) }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = tint.text,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
                if (items.size > 1) {
                    Text(
                        text = "+${items.size - 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HourLabels(formats: Formats) {
    val small = MaterialTheme.typography.labelSmall.toSpanStyle()
    Column(Modifier.width(Gutter)) {
        repeat(24) { hour ->
            val (number, suffix) = formats.hourParts(hour)
            Box(Modifier.height(WeekHourHeight).fillMaxWidth()) {
                if (hour > 0) {
                    Text(
                        text =
                            buildAnnotatedString {
                                append(number)
                                if (suffix.isNotEmpty()) {
                                    append(" ")
                                    withStyle(small) { append(suffix) }
                                }
                            },
                        modifier = Modifier.fillMaxWidth().offset(y = (-8).dp).padding(end = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekCard(agenda: Agenda, time: String, onClick: () -> Unit, modifier: Modifier) {
    val tint = eventTint(agenda.color)
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.container(strong = true))
            .clearAndSetSemantics {
                contentDescription = "${agenda.title}, $time"
                onClick {
                    onClick()
                    true
                }
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 3.dp)
    ) {
        Text(
            text = agenda.title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = tint.text,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun NowMark(formats: Formats, x: Dp, width: Dp) {
    val minute by
        produceState(initialValue = nowMinute(formats)) {
            while (true) {
                delay(30_000)
                value = nowMinute(formats)
            }
        }
    val color = MaterialTheme.colorScheme.primary
    Row(
        Modifier.offset(x = x - 5.dp, y = WeekHourHeight * (minute / 60f) - 5.dp)
            .width(width + 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Box(Modifier.weight(1f).height(2.dp).background(color))
    }
}

private fun nowMinute(formats: Formats) =
    LocalTime.now(formats.zone).let { it.hour * 60 + it.minute }
