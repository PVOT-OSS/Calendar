/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prauga.pvot.designsystem.components.PvotSegmentedControl
import com.prauga.pvot.designsystem.components.button.PvotIconButton
import org.prauga.calendar.R
import org.prauga.calendar.ui.Actions
import org.prauga.calendar.ui.Formats
import org.prauga.calendar.ui.components.Panel

@Composable
fun CalendarScreen(viewModel: CalendarViewModel, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val formats = remember { Formats(context) }
    Column(modifier) {
        PvotSegmentedControl(
            options = stringArrayResource(R.array.ui_calendar_modes).toList(),
            selectedIndex = state.mode.ordinal,
            onSelect = { viewModel.setMode(CalendarMode.entries[it]) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        )
        Panel {
            Column {
                when (state.mode) {
                    CalendarMode.Week -> DayStrip(
                        days = (0L until 7L).map { state.span.first.plusDays(it) },
                        selected = state.selected,
                        today = state.today,
                        agendas = state.agendas,
                        formats = formats,
                        onSelect = viewModel::select,
                        onSwipe = viewModel::step,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
                    )
                    CalendarMode.Day -> Navigator(
                        label = formats.dayLabel(state.selected, state.today),
                        onPrevious = { viewModel.step(false) },
                        onNext = { viewModel.step(true) },
                        onLabel = viewModel::goToday,
                    )
                    else -> {}
                }
                when (state.mode) {
                    CalendarMode.Day, CalendarMode.Week -> Timeline(
                        date = state.selected,
                        items = state.on(state.selected),
                        today = state.today,
                        formats = formats,
                        onOpen = { Actions.open(context, it) },
                        onCreate = { Actions.create(context, state.selected, it, formats.zone) },
                        onSwipe = { forward -> viewModel.select(state.selected.plusDays(if (forward) 1 else -1)) },
                        contentPadding = contentPadding,
                    )
                    else -> {}
                }
            }
        }
    }
}

@Composable
fun Navigator(
    label: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onLabel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        PvotIconButton(
            icon = painterResource(R.drawable.ic_ui_chevron_left),
            contentDescription = stringResource(R.string.ui_previous),
            onClick = onPrevious,
        )
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onLabel),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(50),
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        PvotIconButton(
            icon = painterResource(R.drawable.ic_ui_chevron_right),
            contentDescription = stringResource(R.string.ui_next),
            onClick = onNext,
        )
    }
}
