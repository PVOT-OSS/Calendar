/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar

import android.Manifest
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.prauga.calendar.settings.GeneralPreferences
import org.prauga.calendar.settings.SettingsActivity
import org.prauga.calendar.theme.applyTheme
import org.prauga.calendar.R
import org.prauga.calendar.ui.MainActivity

class OnboardingActivity : AppCompatActivity() {

    private val calendarPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.onboarding_activity)
        findViewById<Button>(R.id.onboarding_calendar).setOnClickListener {
            calendarPermission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
        }
        findViewById<Button>(R.id.onboarding_notifications).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        findViewById<Button>(R.id.onboarding_alarms).setOnClickListener {
            if (Build.VERSION.SDK_INT >= 31) {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        }
        findViewById<Button>(R.id.onboarding_offline).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.onboarding_davx5).setOnClickListener { openDavx5() }
        findViewById<Button>(R.id.onboarding_done).setOnClickListener { finishOnboarding() }
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finishOnboarding()
        })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        fun mark(id: Int, label: Int, granted: Boolean, available: Boolean = true) {
            val button = findViewById<Button>(id)
            button.visibility = if (available) View.VISIBLE else View.GONE
            button.isEnabled = !granted
            button.text = if (granted) getString(R.string.onboarding_allowed, getString(label)) else getString(label)
        }
        mark(R.id.onboarding_calendar, R.string.onboarding_allow_calendar, hasCalendar(this))
        mark(
            R.id.onboarding_notifications, R.string.onboarding_allow_notifications,
            Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS), Build.VERSION.SDK_INT >= 33
        )
        val alarms = Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        mark(R.id.onboarding_alarms, R.string.onboarding_allow_alarms, alarms, Build.VERSION.SDK_INT >= 31)
        val calendarsReady = hasCalendar(this)
        findViewById<View>(R.id.onboarding_offline).isEnabled = calendarsReady
        findViewById<View>(R.id.onboarding_davx5).isEnabled = calendarsReady
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun openDavx5() {
        val launch = packageManager.getLaunchIntentForPackage(DAVX5)
        if (launch != null) {
            startActivity(launch)
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$DAVX5")))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/$DAVX5/")))
        }
    }

    private fun finishOnboarding() {
        GeneralPreferences.getSharedPreferences(this).edit()
            .putBoolean(PREF_DONE, true)
            .putBoolean(AllInOneActivity.PREF_EXACT_ALARM_ASKED, true)
            .apply()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }


    companion object {
        private const val PREF_DONE = "onboarding_done"
        private const val DAVX5 = "at.bitfire.davdroid"

        private fun hasCalendar(context: Context) =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

        @JvmStatic
        fun shouldShow(context: Context): Boolean {
            val prefs = GeneralPreferences.getSharedPreferences(context)
            if (prefs.getBoolean(PREF_DONE, false)) return false
            if (hasCalendar(context)) {
                prefs.edit().putBoolean(PREF_DONE, true).apply()
                return false
            }
            return true
        }
    }
}
