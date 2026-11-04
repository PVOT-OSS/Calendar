/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ContentUris
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.provider.CalendarContract
import android.provider.CalendarContract.Instances
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.text.format.DateUtils
import org.prauga.calendar.R
import java.util.Formatter
import java.util.Locale
import java.util.concurrent.Executors

class NextEventTileService : TileService() {

    private data class Next(val eventId: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean)

    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var next: Next? = null

    override fun onStartListening() {
        super.onStartListening()
        executor.execute {
            next = if (Utils.isCalendarPermissionGranted(this, false)) query() else null
            qsTile?.let { tile -> Handler(mainLooper).post { update(tile) } }
        }
    }

    private fun query(): Next? {
        val now = System.currentTimeMillis()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now)
            ContentUris.appendId(it, now + 7 * DateUtils.DAY_IN_MILLIS)
        }.build()
        return try {
            contentResolver.query(
                uri, arrayOf(Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY),
                "${Instances.VISIBLE}=1 AND ${Instances.STATUS}!=${CalendarContract.Events.STATUS_CANCELED} AND ${Instances.END}>?",
                arrayOf(now.toString()), "${Instances.ALL_DAY} ASC, ${Instances.BEGIN} ASC"
            )?.use {
                var allDay: Next? = null
                while (it.moveToNext()) {
                    val n = Next(it.getLong(0), it.getString(1) ?: "", it.getLong(2), it.getLong(3), it.getInt(4) != 0)
                    if (!n.allDay) return n
                    if (allDay == null) allDay = n
                }
                allDay
            }
        } catch (e: SecurityException) {
            null
        }
    }

    private fun update(tile: Tile) {
        val n = next
        tile.icon = Icon.createWithResource(this, R.drawable.stat_notify_calendar_events)
        if (n == null) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.tile_next_event)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = getString(R.string.tile_no_events)
        } else {
            tile.state = Tile.STATE_ACTIVE
            tile.label = n.title.ifBlank { getString(R.string.no_title_label) }
            val flags = if (n.allDay) DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL
            else DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL
            val tz = if (n.allDay) "UTC" else Utils.getTimeZone(this, null)
            val whenText = DateUtils.formatDateRange(this, Formatter(StringBuilder(), Locale.getDefault()),
                n.begin, n.begin, flags, tz).toString()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = whenText else tile.label = "${tile.label} · $whenText"
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val n = next
        val intent = if (n == null) {
            Intent(this, AllInOneActivity::class.java)
        } else {
            Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, n.eventId))
                .setClass(this, EventInfoActivity::class.java)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, n.begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, n.end)
                .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, n.allDay)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
