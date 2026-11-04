/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.data

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import org.prauga.calendar.Event
import org.prauga.calendar.MeetingLinks
import org.prauga.calendar.Utils
import org.prauga.calendar.tasks.TasksRepository
import java.util.concurrent.atomic.AtomicInteger

class AgendaRepository(context: Context) {

    private val context = context.applicationContext
    private val resolver = this.context.contentResolver
    private val manualRefresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }

    fun refresh() {
        manualRefresh.tryEmit(Unit)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun agendas(startDay: Int, days: Int): Flow<List<Agenda>> =
        merge(changes(), manualRefresh)
            .onStart { emit(Unit) }
            .conflate()
            .mapLatest { load(startDay, days) }

    suspend fun load(startDay: Int, days: Int): List<Agenda> = withContext(Dispatchers.IO) {
        if (!Utils.isCalendarPermissionGranted(context, false)) return@withContext emptyList()
        val events = ArrayList<Event>()
        Event.loadEvents(context, events, startDay, days, 0, AtomicInteger(0))
        events.map { Agenda.from(it, TasksRepository.isTask(it.id)) }
            .sortedWith(compareBy<Agenda>({ it.startDay }, { !it.allDay }, { it.begin }, { it.title }))
    }

    suspend fun details(eventIds: Collection<Long>): Map<Long, AgendaDetails> =
        withContext(Dispatchers.IO) {
            val ids = eventIds.filter { it > 0 }.distinct()
            if (ids.isEmpty() || !Utils.isCalendarPermissionGranted(context, false)) {
                return@withContext emptyMap()
            }
            val inList = ids.joinToString(",")
            val guests = HashMap<Long, MutableList<String>>()
            resolver.query(
                Attendees.CONTENT_URI,
                arrayOf(Attendees.EVENT_ID, Attendees.ATTENDEE_NAME, Attendees.ATTENDEE_EMAIL),
                "${Attendees.EVENT_ID} IN ($inList)", null, null
            )?.use {
                while (it.moveToNext()) {
                    val name = it.getString(1)?.takeIf { n -> n.isNotBlank() } ?: it.getString(2) ?: continue
                    guests.getOrPut(it.getLong(0)) { ArrayList() }.add(name)
                }
            }
            val result = HashMap<Long, AgendaDetails>()
            resolver.query(
                Events.CONTENT_URI,
                arrayOf(Events._ID, Events.EVENT_LOCATION, Events.DESCRIPTION),
                "${Events._ID} IN ($inList)", null, null
            )?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    result[id] = AgendaDetails(
                        guests = guests[id].orEmpty(),
                        meetingUrl = MeetingLinks.find(it.getString(1), it.getString(2))?.url,
                    )
                }
            }
            result
        }

    suspend fun search(query: String, from: Long, to: Long): List<Agenda> = withContext(Dispatchers.IO) {
        if (query.isBlank() || !Utils.isCalendarPermissionGranted(context, false)) return@withContext emptyList()
        val uri = Instances.CONTENT_SEARCH_URI.buildUpon()
            .appendPath(from.toString()).appendPath(to.toString()).appendPath(query.trim()).build()
        val result = ArrayList<Agenda>()
        resolver.query(
            uri,
            arrayOf(
                Instances.EVENT_ID, Instances.TITLE, Instances.EVENT_LOCATION, Instances.BEGIN, Instances.END,
                Instances.ALL_DAY, Instances.DISPLAY_COLOR, Instances.START_DAY, Instances.END_DAY,
                Instances.START_MINUTE, Instances.END_MINUTE, Instances.SELF_ATTENDEE_STATUS,
            ),
            "${Instances.VISIBLE}=1", null, "${Instances.BEGIN} ASC",
        )?.use { c ->
            while (c.moveToNext() && result.size < SEARCH_LIMIT) {
                result += Agenda(
                    eventId = c.getLong(0),
                    title = c.getString(1).orEmpty(),
                    location = c.getString(2).orEmpty(),
                    begin = c.getLong(3),
                    end = c.getLong(4),
                    allDay = c.getInt(5) != 0,
                    color = Utils.getDisplayColorFromColor(context, c.getInt(6)),
                    startDay = c.getInt(7),
                    endDay = c.getInt(8),
                    startMinute = c.getInt(9),
                    endMinute = c.getInt(10),
                    isTask = false,
                    declined = c.getInt(11) == Attendees.ATTENDEE_STATUS_DECLINED,
                )
            }
        }
        result
    }

    private companion object {
        const val SEARCH_LIMIT = 500
    }
}
