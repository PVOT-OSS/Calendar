/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.settings

import android.Manifest
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import org.prauga.calendar.birthdays.BirthdaySync
import org.prauga.calendar.birthdays.BirthdayWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.prauga.calendar.R

class ExtraCalendarsPreferences : PreferenceFragmentCompat() {

    private lateinit var birthdays: SwitchPreferenceCompat

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
        preferenceScreen = screen
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.extra_calendars_title)
    }
}
