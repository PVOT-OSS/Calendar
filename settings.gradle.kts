/*
 * SPDX-FileCopyrightText: 2013 Dominik Schürmann
 * SPDX-FileCopyrightText: 2017-2025 Jochen Sprickerhof
 * SPDX-FileCopyrightText: 2020 Gitsaibot
 * SPDX-FileCopyrightText: 2022 Aayush Gupta
 * SPDX-FileCopyrightText: 2024 doomsdayrs
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    includeBuild("PvotLib/build-logic")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Pvot Calendar"

includeBuild("PvotLib") {
    dependencySubstitution {
        substitute(module("com.prauga.pvot:design-system")).using(project(":design-system"))
    }
}

include(":app")
