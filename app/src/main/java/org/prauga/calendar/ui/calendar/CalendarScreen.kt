/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.calendar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prauga.pvot.designsystem.components.PvotSegmentedControl
import org.prauga.calendar.R
import org.prauga.calendar.ui.components.Panel

@Composable
fun CalendarScreen(viewModel: CalendarViewModel, contentPadding: PaddingValues, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier) {
        PvotSegmentedControl(
            options = stringArrayResource(R.array.ui_calendar_modes).toList(),
            selectedIndex = state.mode.ordinal,
            onSelect = { viewModel.setMode(CalendarMode.entries[it]) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        )
        Panel {
            CalendarContent(state, viewModel, contentPadding)
        }
    }
}

@Composable
private fun CalendarContent(state: CalendarState, viewModel: CalendarViewModel, contentPadding: PaddingValues) {
}
