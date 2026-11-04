/*
 * SPDX-FileCopyrightText: 2024-2025 Aayush Gupta
 * SPDX-FileCopyrightText: 2024-2026 Gitsaibot
 * SPDX-FileCopyrightText: 2024-2025 Jochen Sprickerhof
 * SPDX-FileCopyrightText: 2024 doomsdayrs
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ec4j.editorconfig) apply false
    alias(libs.plugins.jetbrains.idea.ext) apply true
}
