/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.settings

import android.content.Context
import android.os.Bundle
import android.text.format.DateFormat
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import org.prauga.calendar.R
import java.text.DateFormatSymbols
import java.util.Calendar

object WorkingHours {
    const val PREF_ENABLED = "preferences_working_hours"
    const val PREF_START = "preferences_working_hours_start"
    const val PREF_END = "preferences_working_hours_end"
    const val PREF_DAYS = "preferences_working_days"

    val DEFAULT_DAYS = setOf("2", "3", "4", "5", "6")

    data class Hours(val startMinute: Int, val endMinute: Int, val days: Set<Int>)

    fun get(context: Context): Hours? {
        val prefs = GeneralPreferences.getSharedPreferences(context)
        if (!prefs.getBoolean(PREF_ENABLED, false)) return null
        val start = prefs.getString(PREF_START, "540")?.toIntOrNull() ?: 540
        val end = prefs.getString(PREF_END, "1020")?.toIntOrNull() ?: 1020
        val days = (prefs.getStringSet(PREF_DAYS, DEFAULT_DAYS) ?: DEFAULT_DAYS).mapNotNull { it.toIntOrNull() }.toSet()
        return Hours(start, maxOf(start, end), days)
    }
}

class WorkingHoursPreferences : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = GeneralPreferences.SHARED_PREFS_NAME
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        val enabled = SwitchPreferenceCompat(context).apply {
            key = WorkingHours.PREF_ENABLED
            title = getString(R.string.working_hours_enable)
            summary = getString(R.string.working_hours_enable_summary)
            setDefaultValue(false)
        }
        screen.addPreference(enabled)

        val times = (0..48).map { it * 30 }
        val labels = times.map { timeLabel(context, it) }
        fun timeList(prefKey: String, titleRes: Int, default: Int) = ListPreference(context).apply {
            key = prefKey
            title = getString(titleRes)
            dialogTitle = title
            entries = labels.toTypedArray()
            entryValues = times.map { it.toString() }.toTypedArray()
            setDefaultValue(default.toString())
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        }
        val start = timeList(WorkingHours.PREF_START, R.string.working_hours_start, 540)
        val end = timeList(WorkingHours.PREF_END, R.string.working_hours_end, 1020)

        val symbols = DateFormatSymbols.getInstance().weekdays
        val order = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY,
            Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)
        val days = MultiSelectListPreference(context).apply {
            key = WorkingHours.PREF_DAYS
            title = getString(R.string.working_hours_days)
            dialogTitle = title
            entries = order.map { symbols[it] }.toTypedArray()
            entryValues = order.map { it.toString() }.toTypedArray()
            setDefaultValue(WorkingHours.DEFAULT_DAYS)
        }
        days.summaryProvider = Preference.SummaryProvider<MultiSelectListPreference> { pref ->
            order.filter { it.toString() in pref.values }.joinToString(", ") { DateFormatSymbols.getInstance().shortWeekdays[it] }
        }
        for (pref in listOf(start, end, days)) screen.addPreference(pref)
        preferenceScreen = screen
        for (pref in listOf(start, end, days)) pref.dependency = WorkingHours.PREF_ENABLED
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.working_hours_title)
    }

    private fun timeLabel(context: Context, minutes: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            add(Calendar.MINUTE, minutes)
        }
        return if (minutes == 24 * 60) context.getString(R.string.working_hours_midnight)
        else DateFormat.getTimeFormat(context).format(cal.time)
    }
}
