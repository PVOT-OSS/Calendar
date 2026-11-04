/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.edit

import android.text.format.DateFormat
import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.components.PvotBottomSheet
import com.prauga.pvot.designsystem.components.PvotDialog
import com.prauga.pvot.designsystem.components.PvotDialogAction
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import com.prauga.pvot.designsystem.components.PvotSheetItem
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotIconButton
import com.prauga.pvot.designsystem.components.list.PvotAvatar
import com.prauga.pvot.designsystem.components.list.PvotListGroup
import com.prauga.pvot.designsystem.components.list.PvotPreferenceRow
import com.prauga.pvot.designsystem.components.list.PvotRowIconStyle
import com.prauga.pvot.designsystem.components.list.PvotSwitchRow
import com.prauga.pvot.designsystem.components.picker.PvotClockPicker
import com.prauga.pvot.designsystem.components.textfield.PvotTextField
import org.prauga.calendar.R
import org.prauga.calendar.event.EventViewUtils
import org.prauga.calendar.settings.GeneralPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val EventColors = listOf(
    0xFFD50000, 0xFFE67C73, 0xFFF4511E, 0xFFF6BF26, 0xFF33B679, 0xFF0B8043,
    0xFF039BE5, 0xFF3F51B5, 0xFF7986CB, 0xFF8E24AA, 0xFF616161, 0xFF795548,
).map { it.toInt() }

private val ReminderChoices = listOf(0, 5, 10, 15, 30, 60, 120, 1440, 2880, 10080)

private enum class Sheet { None, Color, Calendar, Repeat, Reminder }

private enum class Picker { None, StartDate, StartTime, EndDate, EndTime }

@Composable
fun EditorScreen(state: EditorState, viewModel: EditorViewModel, onBack: () -> Unit, onSave: (EventWriter.Scope?) -> Unit) {
    val context = LocalContext.current
    val draft = state.draft
    var sheet by remember { mutableStateOf(Sheet.None) }
    var picker by remember { mutableStateOf(Picker.None) }
    var guestDialog by remember { mutableStateOf(false) }
    var scopeDialog by remember { mutableStateOf(false) }
    val locale = Locale.getDefault()
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEE, d MMM yyyy", locale) }
    val timeFormat = remember {
        DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "hh:mm a", locale)
    }
    val color = (draft.color ?: state.calendar?.color)?.let { Color(it) } ?: MaterialTheme.colorScheme.primary

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PvotIconButton(
                icon = painterResource(R.drawable.ic_ui_back),
                contentDescription = stringResource(R.string.ui_back),
                onClick = onBack,
            )
            Text(
                text = stringResource(if (state.editing) R.string.ui_edit_event else R.string.ui_new_event),
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (draft.title.isEmpty()) {
                        Text(
                            text = stringResource(R.string.ui_title_hint),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    BasicTextField(
                        value = draft.title,
                        onValueChange = { title -> viewModel.edit { it.copy(title = title) } },
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = context.getString(R.string.ui_title_hint) },
                        textStyle = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                }
                Box(
                    Modifier
                        .padding(start = 12.dp)
                        .size(28.dp)
                        .background(color, CircleShape)
                        .semantics { contentDescription = context.getString(R.string.ui_color) }
                        .clickable { sheet = Sheet.Color }
                )
            }

            PvotListGroup {
                item {
                    PvotSwitchRow(
                        title = stringResource(R.string.edit_event_all_day_label),
                        checked = draft.allDay,
                        onCheckedChange = { allDay -> viewModel.edit { it.copy(allDay = allDay) } },
                        icon = { Icon(painterResource(R.drawable.ic_ui_sunny), null) },
                        iconStyle = PvotRowIconStyle.Plain,
                    )
                }
                item {
                    WhenRow(
                        label = stringResource(R.string.ui_starts),
                        date = draft.start.format(dateFormat),
                        time = if (draft.allDay) null else draft.start.format(timeFormat),
                        onDate = { picker = Picker.StartDate },
                        onTime = { picker = Picker.StartTime },
                    )
                }
                item {
                    WhenRow(
                        label = stringResource(R.string.ui_ends),
                        date = draft.end.format(dateFormat),
                        time = if (draft.allDay) null else draft.end.format(timeFormat),
                        onDate = { picker = Picker.EndDate },
                        onTime = { picker = Picker.EndTime },
                        error = draft.end.isBefore(draft.start),
                    )
                }
                item {
                    PvotPreferenceRow(
                        title = stringResource(R.string.ui_repeat),
                        value = state.repeats.firstOrNull { it.rrule == draft.rrule }?.label
                            ?: stringResource(R.string.does_not_repeat),
                        onClick = { sheet = Sheet.Repeat },
                        icon = { Icon(painterResource(R.drawable.ic_ui_repeat), null) },
                        iconStyle = PvotRowIconStyle.Plain,
                    )
                }
                item {
                    PvotPreferenceRow(
                        title = stringResource(R.string.ui_reminder),
                        value = reminderLabel(draft.reminder),
                        onClick = { sheet = Sheet.Reminder },
                        icon = { Icon(painterResource(R.drawable.ic_ui_bell), null) },
                        iconStyle = PvotRowIconStyle.Plain,
                    )
                }
                item {
                    PvotPreferenceRow(
                        title = stringResource(R.string.ui_calendar),
                        value = state.calendar?.label ?: stringResource(R.string.ui_no_calendar),
                        onClick = { sheet = Sheet.Calendar },
                        enabled = !state.editing,
                        icon = { Icon(painterResource(R.drawable.ic_ui_calendar), null) },
                        iconStyle = PvotRowIconStyle.Plain,
                    )
                }
            }

            PvotTextField(
                value = draft.location,
                onValueChange = { location -> viewModel.edit { it.copy(location = location) } },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.hint_where),
                singleLine = true,
                leadingIcon = { Icon(painterResource(R.drawable.ic_ui_place), null) },
            )
            PvotTextField(
                value = draft.description,
                onValueChange = { text -> viewModel.edit { it.copy(description = text) } },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.hint_description),
                minLines = 3,
                leadingIcon = { Icon(painterResource(R.drawable.ic_ui_notes), null) },
            )
            Guests(
                guests = draft.guests,
                onAdd = { guestDialog = true },
                onRemove = { email -> viewModel.edit { it.copy(guests = it.guests - email) } },
            )
            Spacer(Modifier.height(8.dp))
        }
        PvotButton(
            text = stringResource(if (state.editing) R.string.ui_save else R.string.ui_create_event),
            onClick = { if (state.recurring) scopeDialog = true else onSave(null) },
            enabled = draft.valid && !state.saving,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }

    PvotBottomSheet(visible = sheet == Sheet.Color, onDismissRequest = { sheet = Sheet.None }, title = stringResource(R.string.ui_color)) {
        FlowRow(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EventColors.forEach { c ->
                Box(
                    Modifier
                        .size(44.dp)
                        .background(Color(c), CircleShape)
                        .let { if (draft.color == c) it.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else it }
                        .clickable {
                            viewModel.edit { it.copy(color = c) }
                            sheet = Sheet.None
                        }
                )
            }
        }
        PvotSheetItem(title = stringResource(R.string.ui_calendar_color), onClick = {
            viewModel.edit { it.copy(color = null) }
            sheet = Sheet.None
        })
    }
    PvotBottomSheet(visible = sheet == Sheet.Calendar, onDismissRequest = { sheet = Sheet.None }, title = stringResource(R.string.ui_calendar)) {
        state.calendars.forEach { target ->
            PvotSheetItem(
                title = target.label,
                icon = { Box(Modifier.size(16.dp).background(Color(target.color), CircleShape)) },
                onClick = {
                    viewModel.setCalendar(target.id)
                    sheet = Sheet.None
                },
            )
        }
    }
    PvotBottomSheet(visible = sheet == Sheet.Repeat, onDismissRequest = { sheet = Sheet.None }, title = stringResource(R.string.ui_repeat)) {
        state.repeats.forEach { preset ->
            PvotSheetItem(title = preset.label, onClick = {
                viewModel.edit { it.copy(rrule = preset.rrule) }
                sheet = Sheet.None
            })
        }
    }
    PvotBottomSheet(visible = sheet == Sheet.Reminder, onDismissRequest = { sheet = Sheet.None }, title = stringResource(R.string.ui_reminder)) {
        (listOf(GeneralPreferences.NO_REMINDER) + ReminderChoices).forEach { minutes ->
            PvotSheetItem(title = reminderLabel(minutes), onClick = {
                viewModel.setReminder(minutes)
                sheet = Sheet.None
            })
        }
    }

    when (picker) {
        Picker.StartDate, Picker.EndDate -> DateDialog(
            initial = if (picker == Picker.StartDate) draft.start.toLocalDate() else draft.end.toLocalDate(),
            onDismiss = { picker = Picker.None },
            onPick = { date ->
                val which = picker
                viewModel.edit {
                    if (which == Picker.StartDate) it.withStartDate(date)
                    else it.copy(end = date.atTime(it.end.toLocalTime()))
                }
                picker = Picker.None
            },
        )
        Picker.StartTime, Picker.EndTime -> TimeDialog(
            initial = if (picker == Picker.StartTime) draft.start.toLocalTime() else draft.end.toLocalTime(),
            onDismiss = { picker = Picker.None },
            onPick = { time ->
                val which = picker
                viewModel.edit {
                    if (which == Picker.StartTime) it.withStartTime(time)
                    else it.copy(end = it.end.toLocalDate().atTime(time))
                }
                picker = Picker.None
            },
        )
        Picker.None -> {}
    }
    if (scopeDialog) {
        PvotDialog(visible = true, onDismissRequest = { scopeDialog = false }, title = stringResource(R.string.move_recurring_title)) {
            listOf(
                EventWriter.Scope.THIS to R.string.modify_event,
                EventWriter.Scope.FOLLOWING to R.string.modify_all_following,
                EventWriter.Scope.ALL to R.string.modify_all,
            ).forEachIndexed { index, (scope, label) ->
                PvotDialogAction(stringResource(label), onClick = {
                    scopeDialog = false
                    onSave(scope)
                }, style = if (index == 0) PvotDialogActionStyle.Primary else PvotDialogActionStyle.Neutral)
            }
        }
    }
    if (guestDialog) GuestDialog(
        onDismiss = { guestDialog = false },
        onAdd = { email ->
            viewModel.edit { if (email in it.guests) it else it.copy(guests = it.guests + email) }
            guestDialog = false
        },
    )
}

@Composable
private fun reminderLabel(minutes: Int): String {
    val context = LocalContext.current
    return if (minutes == GeneralPreferences.NO_REMINDER) stringResource(R.string.ui_no_reminder)
    else EventViewUtils.constructReminderLabel(context, minutes, false)
}

@Composable
private fun WhenRow(
    label: String,
    date: String,
    time: String?,
    onDate: () -> Unit,
    onTime: () -> Unit,
    error: Boolean = false,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_ui_time), null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            modifier = Modifier.padding(start = 16.dp).weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        val tint = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        TextButton(onClick = onDate) { Text(date, color = tint) }
        if (time != null) TextButton(onClick = onTime) { Text(time, color = tint) }
    }
}

@Composable
private fun Guests(guests: List<String>, onAdd: () -> Unit, onRemove: (String) -> Unit) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_ui_people), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = stringResource(R.string.ui_guests),
                modifier = Modifier.padding(start = 16.dp).weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            PvotIconButton(
                icon = painterResource(R.drawable.ic_ui_add),
                contentDescription = stringResource(R.string.ui_add_guest),
                onClick = onAdd,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            guests.forEach { email ->
                Row(
                    Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                        .semantics { contentDescription = context.getString(R.string.ui_remove_guest, email) }
                        .clickable { onRemove(email) }
                        .padding(4.dp)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PvotAvatar(name = email, size = 28.dp)
                    Text(email, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onPick(LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC).toLocalDate())
                } ?: onDismiss()
            }) { Text(stringResource(R.string.ui_done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun TimeDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (LocalTime) -> Unit) {
    var time by remember { mutableStateOf(initial) }
    PvotDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = null,
        content = { PvotClockPicker(time = time, onTimeChange = { time = it }) },
    ) {
        PvotDialogAction(stringResource(R.string.ui_done), onClick = { onPick(time) }, style = PvotDialogActionStyle.Primary)
        PvotDialogAction(stringResource(R.string.ui_cancel), onClick = onDismiss)
    }
}

@Composable
private fun GuestDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    val valid = Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    PvotDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.ui_add_guest),
        content = {
            PvotTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = stringResource(R.string.ui_guest_email),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
        },
    ) {
        PvotDialogAction(stringResource(R.string.ui_add_guest), onClick = { onAdd(email.trim()) }, enabled = valid,
            style = PvotDialogActionStyle.Primary)
        PvotDialogAction(stringResource(R.string.ui_cancel), onClick = onDismiss)
    }
}
