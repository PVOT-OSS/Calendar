/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.prauga.calendar.data.Agenda
import org.prauga.calendar.data.JulianDay
import org.prauga.calendar.ui.Formats
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

private val HourHeight = 72.dp
private val LabelWidth = 84.dp

@Composable
fun Timeline(
    date: LocalDate,
    items: List<Agenda>,
    today: LocalDate,
    formats: Formats,
    onOpen: (Agenda) -> Unit,
    onCreate: (LocalTime) -> Unit,
    onSwipe: (forward: Boolean) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val julianDay = JulianDay.of(date)
    val allDay = remember(items, julianDay) { items.filter { it.allDay && it.occursOn(julianDay) } }
    val slots = remember(items, julianDay) { TimelineLayout.layout(items, julianDay) }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val hourPx = with(density) { HourHeight.toPx() }
    val swipePx = with(density) { 72.dp.toPx() }

    LaunchedEffect(date, slots.isEmpty()) {
        val minute = when {
            date == today -> LocalTime.now(formats.zone).let { it.hour * 60 + it.minute } - 60
            slots.isNotEmpty() -> slots.minOf { it.startMinute } - 30
            else -> 8 * 60
        }.coerceAtLeast(0)
        scroll.scrollTo((minute / 60f * hourPx).toInt())
    }

    Column(
        modifier
            .fillMaxSize()
            .pointerInput(date) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (abs(total) > swipePx) onSwipe(total < 0) },
                    onHorizontalDrag = { _, delta -> total += delta },
                )
            }
    ) {
        if (allDay.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                allDay.forEach { AllDayChip(it, onClick = { onOpen(it) }) }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scroll)
                .padding(top = 12.dp, bottom = contentPadding.calculateBottomPadding())
        ) {
            HourGrid(formats, onCreate, hourPx)
            BoxWithConstraints(
                Modifier
                    .padding(start = LabelWidth, end = 16.dp)
                    .fillMaxWidth()
                    .height(HourHeight * 24)
            ) {
                val width = maxWidth
                slots.forEach { slot ->
                    val columnWidth = width / slot.columns
                    TimelineCard(
                        agenda = slot.agenda,
                        time = formats.timeRange(slot.agenda),
                        onClick = { onOpen(slot.agenda) },
                        modifier = Modifier
                            .offset(x = columnWidth * slot.column, y = HourHeight * (slot.startMinute / 60f))
                            .width(columnWidth - 4.dp)
                            .height(HourHeight * ((slot.endMinute - slot.startMinute) / 60f) - 4.dp),
                    )
                }
                if (date == today) NowLine(formats)
            }
        }
    }
}

@Composable
private fun HourGrid(formats: Formats, onCreate: (LocalTime) -> Unit, hourPx: Float) {
    Column(
        Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val hour = (offset.y / hourPx).toInt().coerceIn(0, 23)
                    onCreate(LocalTime.of(hour, 0))
                }
            }
    ) {
        repeat(24) { hour ->
            Row(Modifier.height(HourHeight).fillMaxWidth()) {
                Text(
                    text = formats.hour(hour),
                    modifier = Modifier
                        .width(LabelWidth)
                        .offset(y = (-8).dp)
                        .padding(start = 16.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun NowLine(formats: Formats) {
    val minute by produceState(initialValue = minuteNow(formats)) {
        while (true) {
            delay(30_000)
            value = minuteNow(formats)
        }
    }
    val color = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .offset(x = (-6).dp, y = HourHeight * (minute / 60f) - 6.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Box(Modifier.weight(1f).height(2.dp).background(color))
    }
}

private fun minuteNow(formats: Formats) = LocalTime.now(formats.zone).let { it.hour * 60 + it.minute }

@Composable
private fun TimelineCard(agenda: Agenda, time: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clearAndSetSemantics {
                contentDescription = "${agenda.title}, $time"
                onClick { onClick(); true }
            }
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row {
            Box(Modifier.width(4.dp).fillMaxHeight().background(Color(agenda.color)))
            Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text(
                    text = agenda.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (agenda.title.startsWith("☑")) TextDecoration.LineThrough else null,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (time.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = time,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun AllDayChip(agenda: Agenda, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).background(Color(agenda.color), CircleShape))
        Text(
            text = agenda.title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}
