/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.details

import android.app.Application
import android.provider.CalendarContract.Attendees
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.prauga.calendar.event.EventCopier

data class DetailsState(
    val loading: Boolean = true,
    val details: EventDetails? = null,
    val begin: Long = 0,
    val end: Long = 0,
)

class DetailsViewModel(application: Application) : AndroidViewModel(application) {

    private val resolver = application.contentResolver
    private val _state = MutableStateFlow(DetailsState())
    val state: StateFlow<DetailsState> = _state.asStateFlow()
    private var eventId = -1L

    fun start(eventId: Long, begin: Long, end: Long) {
        this.eventId = eventId
        _state.value = _state.value.copy(begin = begin, end = end)
        load()
    }

    private var pendingResponse = Attendees.ATTENDEE_STATUS_NONE

    fun respondOnLoad(status: Int) {
        pendingResponse = status
    }

    fun load() {
        if (eventId < 0) return
        viewModelScope.launch {
            val details = withContext(Dispatchers.IO) { EventDetailsLoader.load(resolver, eventId) }
            val s = _state.value
            _state.value = s.copy(
                loading = false,
                details = details,
                begin = if (s.begin > 0 || details == null) s.begin else details.dtStart,
                end = if (s.end > 0 || details == null) s.end else details.dtEnd,
            )
            if (details != null && details.canRespond && pendingResponse != Attendees.ATTENDEE_STATUS_NONE) {
                val status = pendingResponse
                pendingResponse = Attendees.ATTENDEE_STATUS_NONE
                if (details.self?.status != status) respond(status)
            }
        }
    }

    fun respond(status: Int, onlyThis: Boolean = false) {
        val s = _state.value
        val details = s.details ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                if (onlyThis && details.rrule != null) {
                    EventDetailsLoader.respondOnce(resolver, details, s.begin, s.end, status)
                } else {
                    EventDetailsLoader.respond(resolver, details, status)
                }
            }
            load()
        }
    }

    fun duplicate(onDone: (Boolean) -> Unit) {
        val details = _state.value.details ?: return
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                runCatching { EventCopier(resolver).copy(details.id, details.calendar) }.getOrNull()
            }
            onDone(id != null)
        }
    }
}
