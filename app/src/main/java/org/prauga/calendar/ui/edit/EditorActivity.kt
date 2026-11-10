/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.edit

import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import android.util.Patterns
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prauga.pvot.designsystem.platform.PvotBaseActivity
import com.prauga.pvot.designsystem.theme.PvotAppTheme
import org.prauga.calendar.R
import org.prauga.calendar.theme.CalendarTypography

class EditorActivity : PvotBaseActivity() {

    private val viewModel: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val fromUri =
            if (intent.action == Intent.ACTION_EDIT) {
                intent.data?.pathSegments?.getOrNull(1)?.toLongOrNull()
            } else {
                null
            }
        val eventId = fromUri ?: intent.getLongExtra(EXTRA_EVENT_ID, -1L)
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
                prefill =
                    EditorViewModel.Prefill(
                        description = intent.getStringExtra(Events.DESCRIPTION).orEmpty(),
                        location = intent.getStringExtra(Events.EVENT_LOCATION).orEmpty(),
                        rrule = intent.getStringExtra(Events.RRULE)?.takeIf { it.isNotBlank() },
                        guests = guests(),
                        calendarId = intent.getLongExtra(Events.CALENDAR_ID, -1L),
                    ),
            )
        }
        setPvotContent {
            PvotAppTheme(typography = CalendarTypography) {
                val state by viewModel.state.collectAsStateWithLifecycle()
                EditorScreen(
                    state = state,
                    viewModel = viewModel,
                    onBack = ::finish,
                    onSave = { scope ->
                        viewModel.save(scope) { ok ->
                            Toast.makeText(
                                    this,
                                    if (ok) R.string.ui_saved else R.string.ui_save_failed,
                                    Toast.LENGTH_SHORT,
                                )
                                .show()
                            if (ok) finish()
                        }
                    },
                )
            }
        }
    }

    private fun guests(): List<String> {
        val raw =
            intent.getStringArrayExtra(Intent.EXTRA_EMAIL)?.toList()
                ?: intent.getStringExtra(Intent.EXTRA_EMAIL)?.split(',', ';')
                ?: return emptyList()
        return raw.map { it.trim() }
            .filter { Patterns.EMAIL_ADDRESS.matcher(it).matches() }
            .distinct()
    }

    companion object {
        const val EXTRA_EVENT_ID = "event_id"
    }
}
