/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.prauga.pvot.designsystem.components.PvotCapsule
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotIconButton
import com.prauga.pvot.designsystem.components.calendar.PvotDateHeader
import com.prauga.pvot.designsystem.effects.LocalPvotGlassBackdrop
import com.prauga.pvot.designsystem.effects.pvotGlassBackdrop
import com.prauga.pvot.designsystem.effects.rememberPvotGlassBackdrop
import org.prauga.calendar.AlternateCalendar
import org.prauga.calendar.ImportActivity
import org.prauga.calendar.R
import org.prauga.calendar.ui.calendar.CalendarMode
import org.prauga.calendar.ui.calendar.CalendarScreen
import org.prauga.calendar.ui.calendar.CalendarViewModel
import org.prauga.calendar.ui.home.HomeScreen

enum class Destination { Home, Calendar }

@Composable
fun CalendarApp(launch: LaunchTarget? = null, onLaunched: () -> Unit = {}) {
    val context = LocalContext.current
    val formats = remember { Formats(context) }
    var destination by rememberSaveable { mutableStateOf(Destination.Home) }
    val calendarViewModel: CalendarViewModel = viewModel()
    LaunchedEffect(launch) {
        launch ?: return@LaunchedEffect
        destination = Destination.Calendar
        if (launch.mode == CalendarMode.Day) calendarViewModel.openDay(launch.date)
        else calendarViewModel.select(launch.date)
        onLaunched()
    }
    val selected by calendarViewModel.selected.collectAsStateWithLifecycle()
    val headerDate = if (destination == Destination.Home) formats.today() else selected
    val backdrop = rememberPvotGlassBackdrop()
    val contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp)

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .pvotGlassBackdrop(backdrop)
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            TopRow(
                title = stringResource(if (destination == Destination.Home) R.string.ui_home else R.string.ui_calendar),
            )
            PvotDateHeader(
                headerDate,
                Modifier.padding(vertical = 8.dp),
                secondary = AlternateCalendar.label(context, headerDate.year, headerDate.monthValue - 1, headerDate.dayOfMonth, false),
            )
            when (destination) {
                Destination.Home -> HomeScreen(contentPadding)
                Destination.Calendar -> CalendarScreen(calendarViewModel, contentPadding)
            }
        }
        CompositionLocalProvider(LocalPvotGlassBackdrop provides backdrop) {
            BottomBar(
                destination = destination,
                onSwitch = {
                    destination = if (destination == Destination.Home) Destination.Calendar else Destination.Home
                },
                onAdd = { Actions.create(context, headerDate, formats.zone) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun TopRow(title: String) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) context.startActivity(Intent(context, ImportActivity::class.java).setData(uri))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 12.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Box {
            PvotIconButton(
                icon = painterResource(R.drawable.ic_ui_menu),
                contentDescription = stringResource(R.string.ui_more),
                onClick = { menu = true },
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                MenuItem(R.string.search) { Actions.search(context) }
                MenuItem(R.string.cal_import_menu_title) {
                    importLauncher.launch(arrayOf("text/calendar", "text/x-vcalendar", "application/ics"))
                }
                MenuItem(R.string.menu_preferences) { Actions.settings(context) }
                MenuItem(R.string.ui_about) { Actions.about(context) }
            }
        }
    }
}

@Composable
private fun MenuItem(label: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = onClick)
}

@Composable
private fun BottomBar(destination: Destination, onSwitch: () -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    PvotCapsule(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val add: @Composable () -> Unit = {
                PvotButton(
                    text = stringResource(R.string.ui_add),
                    onClick = onAdd,
                    icon = painterResource(R.drawable.ic_ui_add),
                )
            }
            if (destination == Destination.Home) {
                add()
                PvotIconButton(
                    icon = painterResource(R.drawable.ic_ui_calendar),
                    contentDescription = stringResource(R.string.ui_calendar),
                    onClick = onSwitch,
                )
            } else {
                PvotIconButton(
                    icon = painterResource(R.drawable.ic_ui_home),
                    contentDescription = stringResource(R.string.ui_home),
                    onClick = onSwitch,
                )
                add()
            }
        }
    }
}
