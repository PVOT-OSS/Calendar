/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.edit

import android.os.Bundle
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prauga.pvot.designsystem.platform.PvotBaseActivity
import com.prauga.pvot.designsystem.theme.PvotAppTheme
import org.prauga.calendar.R

class EditorActivity : PvotBaseActivity() {

    private val viewModel: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        if (eventId >= 0) {
            viewModel.startEdit(
                eventId,
                intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0L),
                intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0L),
            )
        } else {
            viewModel.start(
                beginMillis = intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0L),
                endMillis = intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0L),
                allDay = intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false),
                title = intent.getStringExtra(Events.TITLE),
            )
        }
        setPvotContent {
            PvotAppTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                EditorScreen(
                    state = state,
                    viewModel = viewModel,
                    onBack = ::finish,
                    onSave = { scope ->
                        viewModel.save(scope) { ok ->
                            Toast.makeText(this, if (ok) R.string.ui_saved else R.string.ui_save_failed, Toast.LENGTH_SHORT).show()
                            if (ok) finish()
                        }
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_EVENT_ID = "event_id"
    }
}
