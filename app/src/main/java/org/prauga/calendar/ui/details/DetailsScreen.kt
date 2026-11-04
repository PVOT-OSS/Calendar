/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.details

import android.provider.CalendarContract.Attendees
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.text.util.Linkify
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.prauga.pvot.designsystem.components.PvotDialog
import com.prauga.pvot.designsystem.components.PvotDialogAction
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.components.button.PvotIconButton
import com.prauga.pvot.designsystem.components.list.PvotAvatar
import com.prauga.pvot.designsystem.components.list.PvotListGroup
import com.prauga.pvot.designsystem.components.list.PvotListRow
import org.prauga.calendar.EventRecurrenceFormatter
import org.prauga.calendar.R
import org.prauga.calendar.Utils
import org.prauga.calendar.calendarcommon2.EventRecurrence
import org.prauga.calendar.calendarcommon2.Time
import org.prauga.calendar.event.EventViewUtils

@Composable
fun DetailsScreen(
    state: DetailsState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onJoin: (String) -> Unit,
    onMap: (String) -> Unit,
    onRespond: (Int, Boolean) -> Unit,
) {
    val details = state.details
    var pendingResponse by remember { mutableStateOf<Int?>(null) }
    Column(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PvotIconButton(
                painterResource(R.drawable.ic_ui_back),
                stringResource(R.string.ui_back),
                onBack,
            )
            Spacer(Modifier.weight(1f))
            if (details != null) {
                if (details.canModify) {
                    PvotIconButton(
                        painterResource(R.drawable.ic_ui_edit),
                        stringResource(R.string.ui_edit),
                        onEdit,
                    )
                }
                PvotIconButton(
                    painterResource(R.drawable.ic_ui_copy),
                    stringResource(R.string.ui_duplicate),
                    onDuplicate,
                )
                if (details.canModify) {
                    PvotIconButton(
                        painterResource(R.drawable.ic_ui_delete),
                        stringResource(R.string.ui_delete),
                        onDelete,
                    )
                }
            }
        }
        if (details == null) {
            if (!state.loading) {
                Text(
                    text = stringResource(R.string.ui_event_gone),
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = details.title.ifBlank { stringResource(R.string.no_title_label) },
                    modifier = Modifier.weight(1f).semantics { heading() },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Box(
                    Modifier.padding(start = 12.dp)
                        .size(24.dp)
                        .background(Color(details.color), CircleShape)
                )
            }
            When(details, state.begin, state.end)
            if (details.meetingUrl != null) {
                PvotButton(
                    text = stringResource(R.string.ui_join),
                    onClick = { onJoin(details.meetingUrl) },
                    icon = painterResource(R.drawable.ic_ui_video),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PvotListGroup {
                if (details.location.isNotBlank()) {
                    item {
                        PvotListRow(
                            title = details.location,
                            leading = {
                                Icon(
                                    painterResource(R.drawable.ic_ui_place),
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                            onClick = { onMap(details.location) },
                        )
                    }
                }
                item {
                    PvotListRow(
                        title = details.calendar.label,
                        leading = {
                            Icon(
                                painterResource(R.drawable.ic_ui_calendar),
                                null,
                                tint = Color(details.calendar.color),
                            )
                        },
                    )
                }
                if (details.reminders.isNotEmpty()) {
                    item {
                        val context = LocalContext.current
                        PvotListRow(
                            title =
                                details.reminders.joinToString(", ") {
                                    EventViewUtils.constructReminderLabel(context, it, false)
                                },
                            leading = {
                                Icon(
                                    painterResource(R.drawable.ic_ui_bell),
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            },
                        )
                    }
                }
            }
            if (details.canRespond) {
                Respond(details.self?.status ?: Attendees.ATTENDEE_STATUS_NONE) { status ->
                    if (details.rrule != null) pendingResponse = status
                    else onRespond(status, false)
                }
            }
            if (details.description.isNotBlank()) Description(details.description)
            if (details.guests.isNotEmpty()) Guests(details.guests)
            Spacer(Modifier.height(16.dp))
        }
    }
    pendingResponse?.let { status ->
        PvotDialog(
            visible = true,
            onDismissRequest = { pendingResponse = null },
            title = stringResource(R.string.move_recurring_title),
        ) {
            PvotDialogAction(
                stringResource(R.string.move_this_event),
                onClick = {
                    pendingResponse = null
                    onRespond(status, true)
                },
                style = PvotDialogActionStyle.Primary,
            )
            PvotDialogAction(
                stringResource(R.string.move_all_events),
                onClick = {
                    pendingResponse = null
                    onRespond(status, false)
                },
            )
        }
    }
}

@Composable
private fun When(details: EventDetails, begin: Long, end: Long) {
    val context = LocalContext.current
    val range =
        remember(details, begin, end) {
            var flags =
                DateUtils.FORMAT_SHOW_DATE or
                    DateUtils.FORMAT_SHOW_WEEKDAY or
                    DateUtils.FORMAT_SHOW_YEAR
            if (details.allDay) flags = flags or DateUtils.FORMAT_UTC
            else {
                flags = flags or DateUtils.FORMAT_SHOW_TIME
                if (DateFormat.is24HourFormat(context)) flags = flags or DateUtils.FORMAT_24HOUR
            }
            Utils.formatDateRange(context, begin, if (details.allDay) end - 1 else end, flags)
        }
    val repeat =
        remember(details, begin) {
            details.rrule?.let { rule ->
                val recurrence =
                    EventRecurrence().apply {
                        parse(rule)
                        setStartDate(
                            Time(
                                    if (details.allDay) Time.TIMEZONE_UTC
                                    else Utils.getTimeZone(context, null)
                                )
                                .apply { set(begin) }
                        )
                    }
                EventRecurrenceFormatter.getRepeatString(
                    context,
                    context.resources,
                    recurrence,
                    true,
                )
            }
        }
    Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            range,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (repeat != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_ui_repeat),
                    null,
                    Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    repeat,
                    Modifier.padding(start = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Respond(status: Int, onRespond: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.view_event_response_label),
            Modifier.padding(horizontal = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                    Attendees.ATTENDEE_STATUS_ACCEPTED to R.string.response_yes,
                    Attendees.ATTENDEE_STATUS_TENTATIVE to R.string.response_maybe,
                    Attendees.ATTENDEE_STATUS_DECLINED to R.string.response_no,
                )
                .forEach { (value, label) ->
                    PvotButton(
                        text = stringResource(label),
                        onClick = { onRespond(value) },
                        style =
                            if (status == value) PvotButtonStyle.Filled else PvotButtonStyle.Tonal,
                        modifier = Modifier.weight(1f),
                    )
                }
        }
    }
}

@Composable
private fun Description(text: String) {
    val color = MaterialTheme.colorScheme.onSurface.toArgb()
    val link = MaterialTheme.colorScheme.primary.toArgb()
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                setTextIsSelectable(true)
                textSize = 16f
            }
        },
        update = { view ->
            view.setTextColor(color)
            view.setLinkTextColor(link)
            view.text = text
            Linkify.addLinks(
                view,
                Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES or Linkify.PHONE_NUMBERS,
            )
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
    )
}

@Composable
private fun Guests(guests: List<Guest>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.ui_guests),
            Modifier.padding(horizontal = 8.dp).semantics { heading() },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PvotListGroup {
            guests.forEach { guest ->
                item {
                    PvotListRow(
                        title = guest.name,
                        supportingText =
                            listOfNotNull(
                                    stringResource(statusLabel(guest.status)),
                                    if (guest.organizer) stringResource(R.string.ui_organizer)
                                    else null,
                                    if (guest.optional) stringResource(R.string.ui_optional)
                                    else null,
                                )
                                .joinToString(" · "),
                        leading = { PvotAvatar(name = guest.name, colorKey = guest.email) },
                    )
                }
            }
        }
    }
}

private fun statusLabel(status: Int) =
    when (status) {
        Attendees.ATTENDEE_STATUS_ACCEPTED -> R.string.ui_going
        Attendees.ATTENDEE_STATUS_DECLINED -> R.string.ui_not_going
        Attendees.ATTENDEE_STATUS_TENTATIVE -> R.string.ui_maybe_going
        else -> R.string.ui_awaiting
    }
