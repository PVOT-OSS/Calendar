/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.edit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.prauga.calendar.event.QuickCreateSheet
import org.prauga.calendar.event.RepeatPresets
import org.prauga.calendar.persistence.CalendarTarget
import org.prauga.calendar.persistence.WritableCalendars
import org.prauga.calendar.settings.CalendarDefaults
import org.prauga.calendar.settings.GeneralPreferences
import org.prauga.calendar.ui.Formats
import java.time.Instant
import java.time.LocalDateTime

data class EditorState(
    val draft: EventDraft,
    val calendars: List<CalendarTarget> = emptyList(),
    val repeats: List<RepeatPresets.Preset> = emptyList(),
    val saving: Boolean = false,
    val reminderTouched: Boolean = false,
) {
    val calendar: CalendarTarget? get() = calendars.firstOrNull { it.id == draft.calendarId }
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val formats = Formats(application)
    val zone = formats.zone

    private val _state = MutableStateFlow(
        EditorState(LocalDateTime.now(zone).withSecond(0).withNano(0).let { EventDraft(start = it, end = it.plusHours(1)) })
    )
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var initialized = false

    fun start(beginMillis: Long, endMillis: Long, allDay: Boolean, title: String?) {
        if (initialized) return
        initialized = true
        val begin = if (beginMillis > 0) LocalDateTime.ofInstant(Instant.ofEpochMilli(beginMillis), zone)
        else LocalDateTime.now(zone).withMinute(0).withSecond(0).withNano(0).plusHours(1)
        val end = if (endMillis > beginMillis) LocalDateTime.ofInstant(Instant.ofEpochMilli(endMillis), zone)
        else begin.plusHours(1)
        _state.update { it.copy(draft = it.draft.copy(title = title.orEmpty(), allDay = allDay, start = begin, end = end)) }
        viewModelScope.launch {
            val context = getApplication<Application>()
            val calendars = withContext(Dispatchers.IO) { WritableCalendars.list(context.contentResolver) }
            val last = GeneralPreferences.getSharedPreferences(context).getLong(QuickCreateSheet.PREF_LAST_CALENDAR, -1L)
            val chosen = calendars.firstOrNull { it.id == last } ?: calendars.firstOrNull()
            _state.update { it.copy(calendars = calendars) }
            chosen?.let { setCalendar(it.id) }
            refreshRepeats()
        }
    }

    fun edit(change: (EventDraft) -> EventDraft) {
        val before = _state.value.draft
        _state.update { it.copy(draft = change(it.draft)) }
        val after = _state.value.draft
        if (after.allDay != before.allDay) applyDefaultReminder()
        if (after.start != before.start) refreshRepeats()
    }

    fun setCalendar(id: Long) {
        _state.update { it.copy(draft = it.draft.copy(calendarId = id)) }
        applyDefaultReminder()
    }

    fun setReminder(minutes: Int) {
        _state.update { it.copy(draft = it.draft.copy(reminder = minutes), reminderTouched = true) }
    }

    private fun applyDefaultReminder() {
        val s = _state.value
        if (s.reminderTouched || s.draft.calendarId < 0) return
        val minutes = CalendarDefaults.reminderMinutes(getApplication(), s.draft.calendarId, s.draft.allDay)
        _state.update { it.copy(draft = it.draft.copy(reminder = minutes)) }
    }

    private fun refreshRepeats() {
        val draft = _state.value.draft
        val presets = RepeatPresets.presets(getApplication(), draft.startMillis(zone), zone.id)
        val keep = presets.any { it.rrule == draft.rrule }
        _state.update {
            it.copy(repeats = presets, draft = if (keep) it.draft else it.draft.copy(rrule = null))
        }
    }

    fun save(onDone: (Boolean) -> Unit) {
        val s = _state.value
        if (!s.draft.valid || s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val context = getApplication<Application>()
            GeneralPreferences.getSharedPreferences(context).edit()
                .putLong(QuickCreateSheet.PREF_LAST_CALENDAR, s.draft.calendarId).apply()
            val id = withContext(Dispatchers.IO) {
                runCatching { EventWriter.insert(context.contentResolver, s.draft, zone, s.calendar?.accountName) }
                    .getOrNull()
            }
            _state.update { it.copy(saving = false) }
            onDone(id != null)
        }
    }
}
