/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.edit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.prauga.calendar.EventRecurrenceFormatter
import org.prauga.calendar.calendarcommon2.EventRecurrence
import org.prauga.calendar.calendarcommon2.Time
import org.prauga.calendar.event.RepeatPresets
import org.prauga.calendar.persistence.CalendarTarget
import org.prauga.calendar.persistence.WritableCalendars
import org.prauga.calendar.settings.CalendarDefaults
import org.prauga.calendar.settings.GeneralPreferences
import org.prauga.calendar.ui.Formats

data class EditorState(
    val draft: EventDraft,
    val calendars: List<CalendarTarget> = emptyList(),
    val repeats: List<RepeatPresets.Preset> = emptyList(),
    val saving: Boolean = false,
    val reminderTouched: Boolean = false,
    val editing: Boolean = false,
    val recurring: Boolean = false,
) {
    val calendar: CalendarTarget?
        get() = calendars.firstOrNull { it.id == draft.calendarId }
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val formats = Formats(application)
    val zone = formats.zone

    private val _state =
        MutableStateFlow(
            EditorState(
                LocalDateTime.now(zone).withSecond(0).withNano(0).let {
                    EventDraft(start = it, end = it.plusHours(1))
                }
            )
        )
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var initialized = false
    private var keepRule = false

    data class Prefill(
        val description: String = "",
        val location: String = "",
        val rrule: String? = null,
        val guests: List<String> = emptyList(),
        val calendarId: Long = -1,
    )

    private var editingId = -1L
    private var original: EventDraft? = null
    private var occurrenceBegin = 0L
    private var occurrenceEnd = 0L

    fun startEdit(eventId: Long, begin: Long, end: Long) {
        if (initialized) return
        initialized = true
        editingId = eventId
        viewModelScope.launch {
            val context = getApplication<Application>()
            val (draft, calendars) =
                withContext(Dispatchers.IO) {
                    EventWriter.load(context.contentResolver, eventId, zone) to
                        WritableCalendars.list(context.contentResolver)
                }
            if (draft == null) return@launch
            val recurring = draft.rrule != null && begin > 0
            val shown =
                if (recurring) {
                    val at = if (draft.allDay) ZoneOffset.UTC else zone
                    val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(begin), at)
                    val finish =
                        LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(if (end > begin) end else begin),
                            at,
                        )
                    draft.copy(
                        start = start,
                        end = if (draft.allDay) maxOf(start, finish.minusDays(1)) else finish,
                    )
                } else {
                    draft
                }
            occurrenceBegin = begin
            occurrenceEnd = end
            original = shown
            _state.update {
                it.copy(
                    draft = shown,
                    calendars = calendars,
                    editing = true,
                    reminderTouched = true,
                    recurring = recurring,
                )
            }
            refreshRepeats()
        }
    }

    fun start(
        beginMillis: Long,
        endMillis: Long,
        allDay: Boolean,
        title: String?,
        prefill: Prefill = Prefill(),
    ) {
        if (initialized) return
        initialized = true
        val begin =
            if (beginMillis > 0) LocalDateTime.ofInstant(Instant.ofEpochMilli(beginMillis), zone)
            else LocalDateTime.now(zone).withMinute(0).withSecond(0).withNano(0).plusHours(1)
        val end =
            if (endMillis > beginMillis)
                LocalDateTime.ofInstant(Instant.ofEpochMilli(endMillis), zone)
            else begin.plusHours(1)
        keepRule = prefill.rrule != null
        _state.update {
            it.copy(
                draft =
                    it.draft.copy(
                        title = title.orEmpty(),
                        allDay = allDay,
                        start = begin,
                        end = end,
                        description = prefill.description,
                        location = prefill.location,
                        rrule = prefill.rrule,
                        guests = prefill.guests,
                    )
            )
        }
        viewModelScope.launch {
            val context = getApplication<Application>()
            val calendars =
                withContext(Dispatchers.IO) { WritableCalendars.list(context.contentResolver) }
            val last =
                GeneralPreferences.getSharedPreferences(context).getLong(PREF_LAST_CALENDAR, -1L)
            val chosen =
                calendars.firstOrNull { it.id == prefill.calendarId }
                    ?: calendars.firstOrNull { it.id == last }
                    ?: calendars.firstOrNull()
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
        val minutes =
            CalendarDefaults.reminderMinutes(getApplication(), s.draft.calendarId, s.draft.allDay)
        _state.update { it.copy(draft = it.draft.copy(reminder = minutes)) }
    }

    private fun refreshRepeats() {
        val draft = _state.value.draft
        val presets = RepeatPresets.presets(getApplication(), draft.startMillis(zone), zone.id)
        val known = draft.rrule == null || presets.any { it.rrule == draft.rrule }
        val keep = known || _state.value.editing || keepRule
        keepRule = false
        val list =
            if (!known && keep) presets + RepeatPresets.Preset(draft.rrule, describe(draft))
            else presets
        _state.update {
            it.copy(repeats = list, draft = if (keep) it.draft else it.draft.copy(rrule = null))
        }
    }

    private fun describe(draft: EventDraft): String {
        val context = getApplication<Application>()
        val recurrence =
            EventRecurrence().apply {
                parse(draft.rrule)
                setStartDate(
                    Time(if (draft.allDay) Time.TIMEZONE_UTC else zone.id).apply {
                        set(draft.startMillis(zone))
                    }
                )
            }
        return EventRecurrenceFormatter.getRepeatString(
            context,
            context.resources,
            recurrence,
            true,
        ) ?: draft.rrule.orEmpty()
    }

    fun save(scope: EventWriter.Scope?, onDone: (Boolean) -> Unit) {
        val s = _state.value
        if (!s.draft.valid || s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val context = getApplication<Application>()
            GeneralPreferences.getSharedPreferences(context)
                .edit()
                .putLong(PREF_LAST_CALENDAR, s.draft.calendarId)
                .apply()
            val ok =
                withContext(Dispatchers.IO) {
                    runCatching {
                            val before = original
                            if (editingId >= 0 && before != null && s.recurring && scope != null) {
                                EventWriter.updateSeries(
                                    context.contentResolver,
                                    editingId,
                                    occurrenceBegin,
                                    occurrenceEnd,
                                    s.draft,
                                    before,
                                    zone,
                                    scope,
                                )
                            } else if (editingId >= 0 && before != null) {
                                EventWriter.update(
                                    context.contentResolver,
                                    editingId,
                                    s.draft,
                                    before,
                                    zone,
                                )
                            } else {
                                EventWriter.insert(
                                    context.contentResolver,
                                    s.draft,
                                    zone,
                                    s.calendar?.accountName,
                                ) != null
                            }
                        }
                        .getOrDefault(false)
                }
            _state.update { it.copy(saving = false) }
            onDone(ok)
        }
    }
}

private const val PREF_LAST_CALENDAR = "quick_create_last_calendar"
