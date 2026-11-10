/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.home

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
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
import com.prauga.pvot.designsystem.effects.PvotGlassBackdrop
import com.prauga.pvot.designsystem.effects.PvotGlassStyle
import com.prauga.pvot.designsystem.effects.pvotGlass
import com.prauga.pvot.designsystem.effects.pvotGlassBackdrop
import com.prauga.pvot.designsystem.effects.rememberPvotGlassBackdrop
import java.time.LocalTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(refreshing) {
        if (!refreshing) return@LaunchedEffect
        delay(1_000)
        var waited = 1_000L
        while (waited < 30_000 && withContext(Dispatchers.IO) { Actions.syncing(context) }) {
            delay(500)
            waited += 500
        }
        refreshing = false
    }

    Column(modifier) {
        Greeting(state, Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
        Panel {
            val backdrop = rememberPvotGlassBackdrop()
            val pull = rememberPullToRefreshState()
            val layout = LocalLayoutDirection.current
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    Actions.sync(context)
                },
                state = pull,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pull,
                        isRefreshing = refreshing,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = HeaderHeight),
                    )
                },
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().pvotGlassBackdrop(backdrop),
                    contentPadding =
                        PaddingValues(
                            start = contentPadding.calculateStartPadding(layout),
                            end = contentPadding.calculateEndPadding(layout),
                            top = contentPadding.calculateTopPadding() + HeaderHeight,
                            bottom = contentPadding.calculateBottomPadding(),
                        ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    upcoming(state, formats, context)
                }
                UpcomingHeader(backdrop, Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

private val HeaderHeight = 52.dp

@Composable
private fun UpcomingHeader(backdrop: PvotGlassBackdrop, modifier: Modifier = Modifier) {
    val fill = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.72f)
    Box(
        modifier
            .fillMaxWidth()
            .height(HeaderHeight)
            .pvotGlass(
                backdrop,
                cornerRadius = 0.dp,
                style =
                    PvotGlassStyle(
                        strength = 0.dp,
                        aberration = 0.dp,
                        edgeBlur = 0.dp,
                        blur = 10.dp,
                        reflection = 0f,
                        glow = 0f,
                        darkGlow = 0f,
                        vividSpread = 0.dp,
                        vividMix = 0f,
                        fillColor = fill,
                    ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.ui_upcoming),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

private fun LazyListScope.upcoming(state: HomeState, formats: Formats, context: Context) {
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

    Column(modifier.fillMaxWidth()) {
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
}
