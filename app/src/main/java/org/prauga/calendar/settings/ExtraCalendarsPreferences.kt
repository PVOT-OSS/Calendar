/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import org.prauga.calendar.AlternateCalendar
import androidx.preference.PreferenceFragmentCompat
import org.prauga.calendar.holidays.HolidaySync
import org.prauga.calendar.tasks.TasksRepository
import androidx.preference.SwitchPreferenceCompat
import org.prauga.calendar.birthdays.BirthdaySync
import org.prauga.calendar.birthdays.BirthdayWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.prauga.calendar.R

class ExtraCalendarsPreferences : PreferenceFragmentCompat() {

    private lateinit var birthdays: SwitchPreferenceCompat
    private lateinit var tasks: SwitchPreferenceCompat

    private val tasksPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) tasks.isChecked = true
    }

    private val contactsPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            birthdays.isChecked = true
            BirthdayWorker.schedule(requireContext())
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = GeneralPreferences.SHARED_PREFS_NAME
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)
        birthdays = SwitchPreferenceCompat(context).apply {
            key = BirthdayWorker.PREF_ENABLED
            title = getString(R.string.birthdays_pref_title)
            summary = getString(R.string.birthdays_pref_summary)
            setDefaultValue(false)
        }
        birthdays.setOnPreferenceChangeListener { _, value ->
            val app = requireContext().applicationContext
            if (value == true) {
                if (!BirthdaySync.hasPermission(app)) {
                    contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                    return@setOnPreferenceChangeListener false
                }
                birthdays.isChecked = true
                BirthdayWorker.schedule(app)
            } else {
                BirthdayWorker.cancel(app)
                CoroutineScope(Dispatchers.IO).launch { BirthdaySync.remove(app) }
            }
            true
        }
        screen.addPreference(birthdays)

        val codes = HolidaySync.availableCountries(context).sortedBy { HolidaySync.countryName(it) }
        val holidays = MultiSelectListPreference(context).apply {
            key = HolidaySync.PREF_COUNTRIES
            title = getString(R.string.holidays_pref_title)
            dialogTitle = title
            entries = codes.map { HolidaySync.countryName(it) }.toTypedArray()
            entryValues = codes.toTypedArray()
            setDefaultValue(emptySet<String>())
        }
        holidays.summaryProvider = Preference.SummaryProvider<MultiSelectListPreference> { pref ->
            pref.values.map { HolidaySync.countryName(it) }.sorted().joinToString(", ")
                .ifEmpty { getString(R.string.holidays_pref_summary) }
        }
        holidays.setOnPreferenceChangeListener { _, _ ->
            val app = requireContext().applicationContext
            requireView().post { CoroutineScope(Dispatchers.IO).launch { HolidaySync.sync(app) } }
            true
        }
        screen.addPreference(holidays)

        val types = listOf(AlternateCalendar.NONE) + AlternateCalendar.TYPES
        val alternate = ListPreference(context).apply {
            key = AlternateCalendar.PREF
            title = getString(R.string.alternate_calendar_pref_title)
            dialogTitle = title
            entries = types.map {
                if (it == AlternateCalendar.NONE) getString(R.string.alternate_calendar_none)
                else AlternateCalendar.displayName(it)
            }.toTypedArray()
            entryValues = types.toTypedArray()
            setDefaultValue(AlternateCalendar.NONE)
            summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
        }
        alternate.setOnPreferenceChangeListener { _, _ ->
            requireView().post { AlternateCalendar.invalidate() }
            true
        }
        screen.addPreference(alternate)

        tasks = SwitchPreferenceCompat(context).apply {
            key = TasksRepository.PREF_ENABLED
            title = getString(R.string.tasks_pref_title)
            setDefaultValue(false)
        }
        val installed = TasksRepository.installedProviders(context)
        tasks.summary = getString(if (installed.isEmpty()) R.string.tasks_pref_summary_none else R.string.tasks_pref_summary)
        tasks.isEnabled = installed.isNotEmpty() || tasks.isChecked
        tasks.setOnPreferenceChangeListener { _, value ->
            if (value == true) {
                val missing = installed.flatMap { listOf(it.readPermission, it.writePermission) }.filter {
                    ContextCompat.checkSelfPermission(requireContext(), it) !=
                        PackageManager.PERMISSION_GRANTED
                }
                if (missing.isNotEmpty()) {
                    tasksPermission.launch(missing.toTypedArray())
                    return@setOnPreferenceChangeListener false
                }
            }
            true
        }
        screen.addPreference(tasks)
        preferenceScreen = screen
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.extra_calendars_title)
    }
}
