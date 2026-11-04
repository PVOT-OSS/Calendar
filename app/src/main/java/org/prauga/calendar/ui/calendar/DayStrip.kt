/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.prauga.calendar.data.Agenda
import org.prauga.calendar.data.JulianDay
import org.prauga.calendar.ui.Formats
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable
fun DayStrip(
    days: List<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    agendas: List<Agenda>,
    formats: Formats,
    onSelect: (LocalDate) -> Unit,
    onSwipe: (forward: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weekday = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
    val swipePx = with(LocalDensity.current) { 48.dp.toPx() }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .pointerInput(days.first()) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (abs(total) > swipePx) onSwipe(total < 0) },
                    onHorizontalDrag = { _, delta -> total += delta },
                )
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        days.forEach { date ->
            val isSelected = date == selected
            val busy = agendas.any { it.occursOn(JulianDay.of(date)) }
            val colors = MaterialTheme.colorScheme
            val container = if (isSelected) colors.primary else Color.Transparent
            val content = if (isSelected) colors.onPrimary else colors.onSurface
            val border = when {
                isSelected -> null
                date == today -> BorderStroke(1.5.dp, colors.primary)
                else -> BorderStroke(1.dp, colors.outlineVariant)
            }
            Column(
                Modifier
                    .weight(1f)
                    .height(76.dp)
                    .clip(RoundedCornerShape(50))
                    .background(container)
                    .let { if (border != null) it.border(border, RoundedCornerShape(50)) else it }
                    .clearAndSetSemantics {
                        contentDescription = formats.fullDate(date)
                        this.selected = isSelected
                        onClick { onSelect(date); true }
                    }
                    .clickable { onSelect(date) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "%02d".format(date.dayOfMonth),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = content,
                )
                Text(
                    text = date.format(weekday),
                    style = MaterialTheme.typography.labelSmall,
                    color = content,
                )
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .size(4.dp)
                        .background(if (busy) content else Color.Transparent, CircleShape)
                )
            }
        }
    }
}
