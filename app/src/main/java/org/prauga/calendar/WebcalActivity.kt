/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.prauga.calendar.R

class WebcalActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri == null) {
            finish()
            return
        }
        val forward = Intent(Intent.ACTION_VIEW, uri).setPackage(ICSX5)
        if (forward.resolveActivity(packageManager) != null) {
            startActivity(forward)
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.webcal_title)
            .setMessage(getString(R.string.webcal_message, uri.toString()))
            .setPositiveButton(R.string.webcal_get_icsx5) { _, _ ->
                val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$ICSX5"))
                try {
                    startActivity(market)
                } catch (e: ActivityNotFoundException) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/$ICSX5/")))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener { finish() }
            .show()
    }

    companion object {
        private const val ICSX5 = "at.bitfire.icsdroid"
    }
}
