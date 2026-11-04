/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.details

import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prauga.pvot.designsystem.platform.PvotBaseActivity
import com.prauga.pvot.designsystem.theme.PvotAppTheme
import org.prauga.calendar.DeleteEventHelper
import org.prauga.calendar.R
import org.prauga.calendar.event.EditEventActivity
import org.prauga.calendar.ui.Actions

class DetailsActivity : PvotBaseActivity() {

    private val viewModel: DetailsViewModel by viewModels()
    private var eventId = -1L
    private var begin = 0L
    private var end = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        eventId = intent.data?.let { runCatching { ContentUris.parseId(it) }.getOrNull() } ?: -1L
        begin = intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0L)
        end = intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0L)
        if (eventId < 0) {
            finish()
            return
        }
        viewModel.start(eventId, begin, end)
        setPvotContent {
            PvotAppTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                DetailsScreen(
                    state = state,
                    onBack = ::finish,
                    onEdit = ::edit,
                    onDuplicate = {
                        viewModel.duplicate { ok ->
                            Toast.makeText(this, if (ok) R.string.ui_duplicated else R.string.ui_save_failed, Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDelete = { DeleteEventHelper(this, this, true).delete(begin, end, eventId, -1) },
                    onJoin = { Actions.join(this, it) },
                    onMap = ::openMap,
                    onRespond = viewModel::respond,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    private fun edit() {
        startActivity(
            Intent(Intent.ACTION_EDIT, ContentUris.withAppendedId(Events.CONTENT_URI, eventId))
                .setClass(this, EditEventActivity::class.java)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
        )
    }

    private fun openMap(location: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(location))))
        } catch (_: ActivityNotFoundException) {
        }
    }
}
