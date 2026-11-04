/*
 * SPDX-FileCopyrightText: 2020 Dominik Schürmann
 * SPDX-FileCopyrightText: 2020-2025 Gitsaibot
 * SPDX-FileCopyrightText: 2022-2024 Aayush Gupta
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.prauga.calendar.databinding.SimpleFrameLayoutMaterialBinding
import org.prauga.calendar.theme.applyTheme

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme()
        super.onCreate(savedInstanceState)

        val binding: SimpleFrameLayoutMaterialBinding =
            SimpleFrameLayoutMaterialBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.include.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.preferences_menu_about)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().add(R.id.body_frame, AboutFragment()).commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
