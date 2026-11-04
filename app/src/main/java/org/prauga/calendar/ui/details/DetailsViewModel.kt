/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.details

import android.app.Application
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

    fun load() {
        if (eventId < 0) return
        viewModelScope.launch {
            val details = withContext(Dispatchers.IO) { EventDetailsLoader.load(resolver, eventId) }
            _state.value = _state.value.copy(loading = false, details = details)
        }
    }

    fun respond(status: Int) {
        val details = _state.value.details ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { EventDetailsLoader.respond(resolver, details, status) }
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
