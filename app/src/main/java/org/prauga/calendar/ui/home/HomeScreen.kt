/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.home

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.components.calendar.PvotEventCard
import java.time.LocalTime
import org.prauga.calendar.R
import org.prauga.calendar.ui.Actions
import org.prauga.calendar.ui.Formats
import org.prauga.calendar.ui.components.Panel

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val formats = remember { Formats(context) }

    Column(modifier) {
        Greeting(state, Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
        Panel {
            LazyColumn(
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                upcoming(state, formats, context)
            }
        }
    }
}

private fun LazyListScope.upcoming(state: HomeState, formats: Formats, context: Context) {
    item {
        Text(
            text = stringResource(R.string.ui_upcoming),
            modifier =
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp).semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
    if (!state.loading && state.days.isEmpty()) {
        item {
            Text(
                text = stringResource(R.string.ui_nothing_upcoming),
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
    state.days.forEach { day ->
        item(key = "day-${day.date}") {
            Text(
                text = formats.dayLabel(day.date, state.today),
                modifier = Modifier.padding(start = 4.dp, top = 8.dp).semantics { heading() },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(day.items, key = { "${day.date}-${it.eventId}-${it.begin}" }) { agenda ->
            val details = state.details[agenda.eventId]
            PvotEventCard(
                title = agenda.title,
                onClick = { Actions.open(context, agenda) },
                accent = Color(agenda.color),
                supportingText = formats.timeRange(agenda),
                guests = details?.guests.orEmpty(),
                action =
                    details?.meetingUrl?.let { url ->
                        {
                            PvotButton(
                                text = stringResource(R.string.ui_join),
                                onClick = { Actions.join(context, url) },
                                style = PvotButtonStyle.Tonal,
                                icon = painterResource(R.drawable.ic_ui_video),
                            )
                        }
                    },
            )
        }
    }
}

@Composable
private fun Greeting(state: HomeState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val hour = LocalTime.now().hour
    val hello =
        stringResource(
            when {
                hour < 12 -> R.string.ui_good_morning
                hour < 18 -> R.string.ui_good_afternoon
                else -> R.string.ui_good_evening
            }
        )
    val events =
        pluralStringResource(R.plurals.ui_meetings, state.eventsThisWeek, state.eventsThisWeek)
    val tasks = pluralStringResource(R.plurals.ui_tasks, state.tasksThisWeek, state.tasksThisWeek)
    val week =
        when {
            state.eventsThisWeek == 0 && state.tasksThisWeek == 0 ->
                stringResource(R.string.ui_summary_free)
            state.tasksThisWeek == 0 -> stringResource(R.string.ui_summary_week_events, events)
            else -> stringResource(R.string.ui_summary_week, events, tasks)
        }
    val weekend =
        state.weekendPlan?.let { " " + stringResource(R.string.ui_summary_weekend, it) }.orEmpty()

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "$hello,",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (!state.loading) {
                Text(
                    text = week + weekend,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        PvotButton(
            text = stringResource(R.string.ui_sync),
            onClick = {
                Actions.sync(context)
                Toast.makeText(context, R.string.ui_syncing, Toast.LENGTH_SHORT).show()
            },
            style = PvotButtonStyle.Tonal,
            icon = painterResource(R.drawable.ic_ui_sync),
        )
    }
}
