/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.birthdays

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.prauga.calendar.settings.GeneralPreferences
import java.util.concurrent.TimeUnit

class BirthdayWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        if (!isEnabled(applicationContext)) return Result.success()
        return if (BirthdaySync.sync(applicationContext)) Result.success() else Result.failure()
    }

    companion object {
        const val PREF_ENABLED = "preferences_birthdays"
        private const val PERIODIC = "birthday_sync"
        private const val NOW = "birthday_sync_now"

        fun isEnabled(context: Context) =
            GeneralPreferences.getSharedPreferences(context).getBoolean(PREF_ENABLED, false)

        fun schedule(context: Context) {
            if (!isEnabled(context)) return
            val work = WorkManager.getInstance(context)
            work.enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequest.Builder(BirthdayWorker::class.java, 1, TimeUnit.DAYS).build()
            )
            work.enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequest.from(BirthdayWorker::class.java))
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}
