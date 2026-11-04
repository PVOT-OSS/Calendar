/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import org.prauga.calendar.event.EditEventActivity
import org.prauga.calendar.R
import org.prauga.calendar.ui.MainActivity

object AppShortcuts {

    fun publish(context: Context) {
        val shortcuts = listOf(
            ShortcutInfoCompat.Builder(context, "newevent")
                .setShortLabel(context.getString(R.string.new_event_dialog_label))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_add_event))
                .setIntent(
                    Intent(Intent.ACTION_INSERT).setClass(context, EditEventActivity::class.java)
                        .setType("vnd.android.cursor.item/event")
                )
                .setRank(0)
                .build(),
            ShortcutInfoCompat.Builder(context, "today")
                .setShortLabel(context.getString(R.string.shortcut_today))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_baseline_calendar_today))
                .setIntent(
                    Intent(Intent.ACTION_VIEW).setClass(context, MainActivity::class.java)
                        .putExtra(Utils.INTENT_KEY_VIEW_TYPE, Utils.INTENT_VALUE_VIEW_TYPE_DAY)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
                .setRank(1)
                .build(),
            ShortcutInfoCompat.Builder(context, "search")
                .setShortLabel(context.getString(R.string.search))
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_menu_search))
                .setIntent(Intent(Intent.ACTION_MAIN).setClass(context, SearchActivity::class.java))
                .setRank(2)
                .build()
        )
        try {
            ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
        } catch (e: RuntimeException) {
        }
    }
}
