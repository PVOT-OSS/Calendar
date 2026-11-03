/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.event

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import org.prauga.calendar.R
import java.util.concurrent.Executors

object EventMoveUi {

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun move(anchor: View, eventId: Long, oldBegin: Long, oldEnd: Long, newBegin: Long, newEnd: Long, recurring: Boolean) {
        if (!recurring) {
            run(anchor, eventId, oldBegin, oldEnd, newBegin, newEnd, EventMover.Scope.THIS)
            return
        }
        val context = anchor.context
        val scopes = arrayOf(context.getString(R.string.move_this_event), context.getString(R.string.move_all_events))
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.move_recurring_title)
            .setItems(scopes) { _, which ->
                run(
                    anchor, eventId, oldBegin, oldEnd, newBegin, newEnd,
                    if (which == 0) EventMover.Scope.THIS else EventMover.Scope.ALL
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun run(
        anchor: View, eventId: Long, oldBegin: Long, oldEnd: Long, newBegin: Long, newEnd: Long,
        scope: EventMover.Scope
    ) {
        val context = anchor.context.applicationContext
        val mover = EventMover(context.contentResolver)
        executor.execute {
            val undo = mover.move(eventId, oldBegin, oldEnd, newBegin, newEnd, scope)
            main.post {
                if (undo == null) {
                    Toast.makeText(context, R.string.move_failed, Toast.LENGTH_SHORT).show()
                    return@post
                }
                if (!anchor.isAttachedToWindow) return@post
                Snackbar.make(anchor, R.string.move_done, Snackbar.LENGTH_LONG)
                    .setAction(R.string.move_undo) { executor.execute { undo() } }
                    .show()
            }
        }
    }
}
