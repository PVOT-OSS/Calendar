/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.event

import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import org.prauga.calendar.Utils
import org.prauga.calendar.persistence.CalendarTarget
import org.prauga.calendar.persistence.WritableCalendars
import org.prauga.calendar.settings.CalendarDefaults
import org.prauga.calendar.settings.GeneralPreferences
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.prauga.calendar.R
import java.util.Formatter
import java.util.Locale

class QuickCreateSheet : BottomSheetDialogFragment() {

    private var startMillis = 0L
    private var endMillis = 0L
    private var allDay = false
    private var targets: List<CalendarTarget> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = savedInstanceState ?: requireArguments()
        startMillis = args.getLong(ARG_START)
        endMillis = args.getLong(ARG_END)
        allDay = args.getBoolean(ARG_ALL_DAY)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.quick_create_sheet, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val title = view.findViewById<EditText>(R.id.quick_title)
        val whenText = view.findViewById<TextView>(R.id.quick_when)
        val allDaySwitch = view.findViewById<MaterialSwitch>(R.id.quick_all_day)
        val spinner = view.findViewById<Spinner>(R.id.quick_calendar)
        val save = view.findViewById<View>(R.id.quick_save)

        allDaySwitch.isChecked = allDay
        whenText.text = describeWhen()
        allDaySwitch.setOnCheckedChangeListener { _, checked ->
            allDay = checked
            whenText.text = describeWhen()
        }
        save.isEnabled = false
        title.requestFocus()
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

        viewLifecycleOwner.lifecycleScope.launch {
            targets = withContext(Dispatchers.IO) { WritableCalendars.list(requireContext().contentResolver) }
            if (targets.isEmpty()) {
                openFullEditor(title.text.toString())
                return@launch
            }
            spinner.adapter = ArrayAdapter(
                requireContext(), android.R.layout.simple_spinner_dropdown_item,
                targets.map { if (it.displayName == it.accountName) it.displayName else "${it.displayName} (${it.accountName})" }
            )
            val last = GeneralPreferences.getSharedPreferences(requireContext()).getLong(PREF_LAST_CALENDAR, -1L)
            spinner.setSelection(targets.indexOfFirst { it.id == last }.coerceAtLeast(0))
            save.isEnabled = true
        }

        save.setOnClickListener {
            val target = targets.getOrNull(spinner.selectedItemPosition) ?: return@setOnClickListener
            saveEvent(title.text.toString().trim(), target)
        }
        view.findViewById<View>(R.id.quick_more).setOnClickListener { openFullEditor(title.text.toString()) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(ARG_START, startMillis)
        outState.putLong(ARG_END, endMillis)
        outState.putBoolean(ARG_ALL_DAY, allDay)
    }

    private fun times(): Pair<Long, Long> {
        if (!allDay) return startMillis to endMillis
        val tz = java.util.TimeZone.getTimeZone(Utils.getTimeZone(requireContext(), null))
        val local = java.util.Calendar.getInstance(tz).apply { timeInMillis = startMillis }
        val utc = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(java.util.Calendar.YEAR), local.get(java.util.Calendar.MONTH), local.get(java.util.Calendar.DAY_OF_MONTH))
        }
        return utc.timeInMillis to utc.timeInMillis + DateUtils.DAY_IN_MILLIS
    }

    private fun describeWhen(): String {
        val (start, end) = times()
        var flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_ALL
        val tz = if (allDay) "UTC" else Utils.getTimeZone(requireContext(), null)
        if (!allDay) {
            flags = flags or DateUtils.FORMAT_SHOW_TIME
            if (DateFormat.is24HourFormat(requireContext())) flags = flags or DateUtils.FORMAT_24HOUR
        }
        val shownEnd = if (allDay) end - 1 else end
        return DateUtils.formatDateRange(requireContext(), Formatter(StringBuilder(50), Locale.getDefault()), start, shownEnd, flags, tz).toString()
    }

    private fun saveEvent(title: String, target: CalendarTarget) {
        val context = requireContext().applicationContext
        val (start, end) = times()
        val tz = if (allDay) "UTC" else Utils.getTimeZone(context, null)
        val reminder = CalendarDefaults.reminderMinutes(context, target.id, allDay)
        GeneralPreferences.getSharedPreferences(context).edit().putLong(PREF_LAST_CALENDAR, target.id).apply()
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val uri = context.contentResolver.insert(Events.CONTENT_URI, ContentValues().apply {
                    put(Events.CALENDAR_ID, target.id)
                    put(Events.TITLE, title)
                    put(Events.DTSTART, start)
                    put(Events.DTEND, end)
                    put(Events.EVENT_TIMEZONE, tz)
                    put(Events.ALL_DAY, if (allDay) 1 else 0)
                    put(Events.HAS_ALARM, if (reminder != GeneralPreferences.NO_REMINDER) 1 else 0)
                }) ?: return@withContext false
                if (reminder != GeneralPreferences.NO_REMINDER) {
                    context.contentResolver.insert(Reminders.CONTENT_URI, ContentValues().apply {
                        put(Reminders.EVENT_ID, android.content.ContentUris.parseId(uri))
                        put(Reminders.MINUTES, reminder)
                        put(Reminders.METHOD, Reminders.METHOD_ALERT)
                    })
                }
                true
            }
            Toast.makeText(context, if (ok) R.string.quick_create_saved else R.string.move_failed, Toast.LENGTH_SHORT).show()
            dismissAllowingStateLoss()
        }
    }

    private fun openFullEditor(title: String) {
        val (start, end) = times()
        val intent = Intent(Intent.ACTION_INSERT).apply {
            setClass(requireContext(), EditEventActivity::class.java)
            type = "vnd.android.cursor.item/event"
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay)
            if (title.isNotBlank()) putExtra(Events.TITLE, title)
            targets.getOrNull(view?.findViewById<Spinner>(R.id.quick_calendar)?.selectedItemPosition ?: -1)
                ?.let { putExtra(Events.CALENDAR_ID, it.id) }
        }
        startActivity(intent)
        dismissAllowingStateLoss()
    }

    companion object {
        private const val ARG_START = "start"
        private const val ARG_END = "end"
        private const val ARG_ALL_DAY = "all_day"
        private const val TAG = "QuickCreateSheet"
        private const val PREF_LAST_CALENDAR = "quick_create_last_calendar"

        @JvmStatic
        fun show(manager: FragmentManager, context: android.content.Context, startMillis: Long, allDay: Boolean) {
            if (manager.isStateSaved || manager.findFragmentByTag(TAG) != null) return
            QuickCreateSheet().apply {
                arguments = Bundle().apply {
                    putLong(ARG_START, startMillis)
                    putLong(ARG_END, startMillis + Utils.getDefaultEventDurationInMillis(context))
                    putBoolean(ARG_ALL_DAY, allDay)
                }
            }.show(manager, TAG)
        }
    }
}
