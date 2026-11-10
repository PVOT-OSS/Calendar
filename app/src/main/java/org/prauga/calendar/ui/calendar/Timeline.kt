/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import org.prauga.calendar.R
import org.prauga.calendar.data.Agenda
import org.prauga.calendar.data.JulianDay
import org.prauga.calendar.settings.WorkingHours
import org.prauga.calendar.ui.Formats

private val HourHeight = 72.dp
private val LabelWidth = 64.dp

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
    onMove: ((Agenda, minutes: Int) -> Unit)? = null,
    workingHours: WorkingHours.Hours? = null,
) {
    val julianDay = JulianDay.of(date)
    val allDay = remember(items, julianDay) { items.filter { it.allDay && it.occursOn(julianDay) } }
    val slots = remember(items, julianDay) { TimelineLayout.layout(items, julianDay) }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val hourPx = with(density) { HourHeight.toPx() }
    val swipePx = with(density) { 72.dp.toPx() }

    LaunchedEffect(date, slots.isEmpty()) {
        val minute =
            when {
                date == today -> LocalTime.now(formats.zone).let { it.hour * 60 + it.minute } - 60
                slots.isNotEmpty() -> slots.minOf { it.startMinute } - 30
                else -> 8 * 60
            }.coerceAtLeast(0)
        scroll.scrollTo((minute / 60f * hourPx).toInt())
    }

    Column(
        modifier.fillMaxSize().pointerInput(date) {
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
                Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                allDay.forEach { AllDayChip(it, onClick = { onOpen(it) }) }
            }
        }
        Box(
            Modifier.fillMaxWidth()
                .weight(1f)
                .verticalScroll(scroll)
                .padding(top = 12.dp, bottom = contentPadding.calculateBottomPadding())
        ) {
            HourGrid(formats, onCreate, hourPx)
            BoxWithConstraints(
                Modifier.padding(start = LabelWidth, end = 16.dp)
                    .fillMaxWidth()
                    .height(HourHeight * 24)
            ) {
                val width = maxWidth
                if (workingHours != null) OffHours(date, workingHours)
                slots.forEach { slot ->
                    val columnWidth = width / slot.columns
                    TimelineCard(
                        agenda = slot.agenda,
                        time = formats.timeRange(slot.agenda),
                        onClick = { onOpen(slot.agenda) },
                        hourPx = hourPx,
                        onMove =
                            if (slot.agenda.isTask) null
                            else onMove?.let { move -> { minutes -> move(slot.agenda, minutes) } },
                        modifier =
                            Modifier.offset(
                                    x = columnWidth * slot.column,
                                    y = HourHeight * (slot.startMinute / 60f),
                                )
                                .width(columnWidth - 4.dp)
                                .height(
                                    HourHeight * ((slot.endMinute - slot.startMinute) / 60f) - 4.dp
                                ),
                    )
                }
                if (date == today) NowLine(formats)
            }
        }
    }
}

@Composable
private fun OffHours(date: LocalDate, hours: WorkingHours.Hours) {
    val shade = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
    val calendarDay = date.dayOfWeek.value % 7 + 1
    val spans =
        if (calendarDay !in hours.days) listOf(0 to TimelineLayout.MINUTES_PER_DAY)
        else listOf(0 to hours.startMinute, hours.endMinute to TimelineLayout.MINUTES_PER_DAY)
    spans
        .filter { it.second > it.first }
        .forEach { (from, to) ->
            Box(
                Modifier.offset(y = HourHeight * (from / 60f))
                    .fillMaxWidth()
                    .height(HourHeight * ((to - from) / 60f))
                    .background(shade, RoundedCornerShape(8.dp))
            )
        }
}

@Composable
private fun HourGrid(formats: Formats, onCreate: (LocalTime) -> Unit, hourPx: Float) {
    Column(
        Modifier.fillMaxWidth().pointerInput(Unit) {
            detectTapGestures { offset ->
                val hour = (offset.y / hourPx).toInt().coerceIn(0, 23)
                onCreate(LocalTime.of(hour, 0))
            }
        }
    ) {
        val small = MaterialTheme.typography.labelSmall.toSpanStyle()
        repeat(24) { hour ->
            val (number, suffix) = formats.hourParts(hour)
            Row(Modifier.height(HourHeight).fillMaxWidth()) {
                Text(
                    text =
                        buildAnnotatedString {
                            append(number)
                            if (suffix.isNotEmpty()) {
                                append(" ")
                                withStyle(small) { append(suffix) }
                            }
                        },
                    modifier = Modifier.width(LabelWidth).offset(y = (-9).dp).padding(end = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun NowLine(formats: Formats) {
    val minute by
        produceState(initialValue = minuteNow(formats)) {
            while (true) {
                delay(30_000)
                value = minuteNow(formats)
            }
        }
    val color = MaterialTheme.colorScheme.primary
    Row(
        Modifier.offset(x = (-6).dp, y = HourHeight * (minute / 60f) - 6.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Box(Modifier.weight(1f).height(2.dp).background(color))
    }
}

private fun minuteNow(formats: Formats) =
    LocalTime.now(formats.zone).let { it.hour * 60 + it.minute }

@Composable
private fun TimelineCard(
    agenda: Agenda,
    time: String,
    onClick: () -> Unit,
    hourPx: Float,
    onMove: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var drag by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val tint = eventTint(agenda.color)
    val snapPx = hourPx / 4
    Surface(
        modifier =
            modifier
                .zIndex(if (dragging) 1f else 0f)
                .graphicsLayer { translationY = (drag / snapPx).roundToInt() * snapPx }
                .let { base ->
                    if (onMove == null) base
                    else
                        base.pointerInput(agenda.eventId, agenda.begin) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    dragging = true
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    drag += amount.y
                                },
                                onDragEnd = {
                                    val steps = (drag / snapPx).roundToInt()
                                    dragging = false
                                    drag = 0f
                                    if (steps != 0) onMove(steps * 15)
                                },
                                onDragCancel = {
                                    dragging = false
                                    drag = 0f
                                },
                            )
                        }
                }
                .clip(RoundedCornerShape(8.dp))
                .clearAndSetSemantics {
                    contentDescription = "${agenda.title}, $time"
                    onClick {
                        onClick()
                        true
                    }
                }
                .clickable(onClick = onClick),
        color = tint.container(strong = dragging),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = if (dragging) 8.dp else 0.dp,
    ) {
        Row(Modifier.padding(start = 5.dp, top = 5.dp, bottom = 5.dp)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(tint.bar, RoundedCornerShape(2.dp)))
            Column(
                Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = agenda.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration =
                        if (agenda.title.startsWith("☑")) TextDecoration.LineThrough else null,
                    color = tint.text,
                )
                if (agenda.location.isNotBlank()) {
                    CardLine(R.drawable.ic_ui_place, agenda.location, tint.detail)
                }
                if (time.isNotEmpty()) CardLine(R.drawable.ic_ui_time, time, tint.detail)
            }
        }
    }
}

@Composable
private fun CardLine(icon: Int, text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = color,
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

class EventTint(
    val bar: Color,
    val text: Color,
    val detail: Color,
    private val base: Color,
    private val surface: Color,
    private val alpha: Float,
) {
    fun container(strong: Boolean = false): Color =
        base.copy(alpha = if (strong) alpha * 1.6f else alpha).compositeOver(surface)
}

@Composable
fun eventTint(color: Int): EventTint {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val base = Color(color)
    val text = lerp(base, if (dark) Color.White else Color.Black, if (dark) 0.35f else 0.3f)
    return EventTint(
        bar = base,
        text = text,
        detail = text.copy(alpha = 0.85f),
        base = base,
        surface = scheme.surfaceContainerLow,
        alpha = if (dark) 0.28f else 0.16f,
    )
}

@Composable
private fun AllDayChip(agenda: Agenda, onClick: () -> Unit) {
    val tint = eventTint(agenda.color)
    Row(
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(tint.container())
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(width = 4.dp, height = 16.dp)
                .background(tint.bar, RoundedCornerShape(2.dp))
        )
        Text(
            text = agenda.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = tint.text,
            maxLines = 1,
        )
    }
}
