/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.prauga.pvot.designsystem.components.PvotDialog
import com.prauga.pvot.designsystem.components.PvotDialogAction
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import com.prauga.pvot.designsystem.components.PvotSegmentedControl
import com.prauga.pvot.designsystem.components.PvotSnackbarHost
import com.prauga.pvot.designsystem.components.button.PvotIconButton
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.prauga.calendar.R
import org.prauga.calendar.data.Agenda
import org.prauga.calendar.event.EventMover
import org.prauga.calendar.settings.WorkingHours
import org.prauga.calendar.ui.Actions
import org.prauga.calendar.ui.Formats
import org.prauga.calendar.ui.components.Panel

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val formats = remember { Formats(context) }
    val workingHours = remember { WorkingHours.get(context) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var pending by remember { mutableStateOf<Pair<Agenda, Int>?>(null) }

    fun move(agenda: Agenda, minutes: Int, which: EventMover.Scope?) {
        scope.launch {
            val mover = EventMover(context.contentResolver)
            val info = withContext(Dispatchers.IO) { mover.info(agenda.eventId) }
            if (info == null || !info.canModify) {
                snackbar.showSnackbar(context.getString(R.string.move_failed))
                return@launch
            }
            if (info.recurring && which == null) {
                pending = agenda to minutes
                return@launch
            }
            val shift = minutes * 60_000L
            val undo =
                withContext(Dispatchers.IO) {
                    mover.move(
                        agenda.eventId,
                        agenda.begin,
                        agenda.end,
                        agenda.begin + shift,
                        agenda.end + shift,
                        which ?: EventMover.Scope.THIS,
                    )
                }
            if (undo == null) {
                snackbar.showSnackbar(context.getString(R.string.move_failed))
                return@launch
            }
            val result =
                snackbar.showSnackbar(
                    context.getString(R.string.move_done),
                    context.getString(R.string.move_undo),
                    duration = SnackbarDuration.Long,
                )
            if (result == SnackbarResult.ActionPerformed) withContext(Dispatchers.IO) { undo() }
        }
    }

    Box(modifier) {
        Column {
            PvotSegmentedControl(
                options = stringArrayResource(R.array.ui_calendar_modes).toList(),
                selectedIndex = state.mode.ordinal,
                onSelect = { viewModel.setMode(CalendarMode.entries[it]) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            )
            Panel {
                Column {
                    when (state.mode) {
                        CalendarMode.Week -> Unit
                        CalendarMode.Day ->
                            Navigator(
                                label = formats.dayLabel(state.selected, state.today),
                                onPrevious = { viewModel.step(false) },
                                onNext = { viewModel.step(true) },
                                onLabel = viewModel::goToday,
                            )
                        CalendarMode.Month ->
                            Navigator(
                                label = monthLabel(YearMonth.from(state.selected), state.today),
                                onPrevious = { viewModel.step(false) },
                                onNext = { viewModel.step(true) },
                                onLabel = viewModel::goToday,
                            )
                        CalendarMode.Year ->
                            Navigator(
                                label = state.selected.year.toString(),
                                onPrevious = { viewModel.step(false) },
                                onNext = { viewModel.step(true) },
                                onLabel = viewModel::goToday,
                            )
                    }
                    when (state.mode) {
                        CalendarMode.Week ->
                            WeekGrid(
                                days = (0L until 7L).map { state.span.first.plusDays(it) },
                                today = state.today,
                                agendas = state.agendas,
                                formats = formats,
                                onOpenDay = viewModel::openDay,
                                onOpen = { Actions.open(context, it) },
                                onCreate = { date, time ->
                                    Actions.create(context, date, time, formats.zone)
                                },
                                onSwipe = viewModel::step,
                                contentPadding = contentPadding,
                            )
                        CalendarMode.Day ->
                            Timeline(
                                date = state.selected,
                                items = state.on(state.selected),
                                today = state.today,
                                formats = formats,
                                onOpen = { Actions.open(context, it) },
                                onCreate = {
                                    Actions.create(context, state.selected, it, formats.zone)
                                },
                                onSwipe = { forward ->
                                    viewModel.select(
                                        state.selected.plusDays(if (forward) 1 else -1)
                                    )
                                },
                                contentPadding = contentPadding,
                                onMove = { agenda, minutes -> move(agenda, minutes, null) },
                                workingHours = workingHours,
                            )
                        CalendarMode.Month ->
                            MonthGrid(
                                month = YearMonth.from(state.selected),
                                selected = state.selected,
                                today = state.today,
                                firstDayOfWeek = state.firstDayOfWeek,
                                agendas = state.agendas,
                                formats = formats,
                                onOpenDay = viewModel::openDay,
                                onSwipe = viewModel::step,
                                contentPadding = contentPadding,
                            )
                        CalendarMode.Year ->
                            YearGrid(
                                year = state.selected.year,
                                focus = state.selected.month,
                                today = state.today,
                                firstDayOfWeek = state.firstDayOfWeek,
                                onOpenMonth = viewModel::openMonth,
                                onSwipe = viewModel::step,
                                contentPadding = contentPadding,
                            )
                    }
                }
            }
        }
        PvotSnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter)
                .padding(bottom = contentPadding.calculateBottomPadding()),
        )
        pending?.let { (agenda, minutes) ->
            PvotDialog(
                visible = true,
                onDismissRequest = { pending = null },
                title = stringResource(R.string.move_recurring_title),
            ) {
                PvotDialogAction(
                    stringResource(R.string.move_this_event),
                    onClick = {
                        pending = null
                        move(agenda, minutes, EventMover.Scope.THIS)
                    },
                    style = PvotDialogActionStyle.Primary,
                )
                PvotDialogAction(
                    stringResource(R.string.move_all_events),
                    onClick = {
                        pending = null
                        move(agenda, minutes, EventMover.Scope.ALL)
                    },
                )
            }
        }
    }
}

private fun monthLabel(month: YearMonth, today: LocalDate): String {
    val pattern = if (month.year == today.year) "LLLL" else "LLLL yyyy"
    return month.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
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
        modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        PvotIconButton(
            icon = painterResource(R.drawable.ic_ui_chevron_left),
            contentDescription = stringResource(R.string.ui_previous),
            onClick = onPrevious,
        )
        Surface(
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onLabel),
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
