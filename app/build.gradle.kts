/*
 * SPDX-FileCopyrightText: 2024-2025 Aayush Gupta
 * SPDX-FileCopyrightText: 2024-2026 Gitsaibot
 * SPDX-FileCopyrightText: 2024-2025 Jochen Sprickerhof
 * SPDX-FileCopyrightText: 2024 doomsdayrs
 * SPDX-FileCopyrightText: 2026 Alexandre Bonneau
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ec4j.editorconfig)
    alias(libs.plugins.pvot.android.compose)
}

editorconfig { excludes = listOf("metadata/**", "PvotLib/**", "**/*.xml", "**/*.webp") }

val versionMajor = rootProject.extra["versionMajor"] as Int
val versionMinor = rootProject.extra["versionMinor"] as Int
val versionPatch = rootProject.extra["versionPatch"] as Int
val computedVersionCode = rootProject.extra["computedVersionCode"] as Int
val computedVersionName = rootProject.extra["computedVersionName"] as String

kotlin { jvmToolchain(21) }

android {
    namespace = "org.prauga.calendar"
    testNamespace = "org.prauga.calendar.tests"
    compileSdk { version = release(37) { minorApiLevel = 1 } }

    defaultConfig {
        minSdk = 27
        targetSdk = 37
        versionCode = computedVersionCode
        versionName = computedVersionName

        buildConfigField("int", "VERSION_MAJOR", "$versionMajor")
        buildConfigField("int", "VERSION_MINOR", "$versionMinor")
        buildConfigField("int", "VERSION_PATCH", "$versionPatch")
        applicationId = "org.prauga.calendar"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // TODO: could be enabled for ProGuard minimization
            isMinifyEnabled = false
            resValue(
                "string",
                "search_authority",
                defaultConfig.applicationId + ".CalendarRecentSuggestionsProvider",
            )
        }

        debug {
            isMinifyEnabled = false

            applicationIdSuffix = ".dev"
            resValue(
                "string",
                "search_authority",
                defaultConfig.applicationId + ".dev.CalendarRecentSuggestionsProvider",
            )
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
        resValues = true
    }

    /*
     * To sign release build, create file gradle.properties in ~/.gradle/ with this content:
     *
     * signingStoreLocation=/home/key.store
     * signingStorePassword=xxx
     * signingKeyAlias=alias
     * signingKeyPassword=xxx
     */
    val signingStoreLocation: String? by project
    val signingStorePassword: String? by project
    val signingKeyAlias: String? by project
    val signingKeyPassword: String? by project

    if (
        signingStoreLocation != null &&
            signingStorePassword != null &&
            signingKeyAlias != null &&
            signingKeyPassword != null
    ) {
        println("Found sign properties in gradle.properties! Signing build…")

        signingConfigs {
            register("release") {
                storeFile = File(signingStoreLocation!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }

        buildTypes.named("release") { signingConfig = signingConfigs.getByName("release") }
    } else {
        buildTypes.named("release") { signingConfig = null }
    }

    lint {
        lintConfig = file("lint.xml")
        // TODO: Resolve lint errors due to 363aa9c237a33e9e1a40bdfd9039dcaaa855a5a0
        abortOnError = false
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true

        sourceCompatibility(JavaVersion.VERSION_21)
        targetCompatibility(JavaVersion.VERSION_21)
    }

    useLibrary("android.test.base")
    useLibrary("android.test.mock")

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
                it.systemProperty("user.timezone", "America/Los_Angeles")
            }
        }
    }

    androidResources { generateLocaleConfig = true }
}

dependencies {

    // Core
    implementation(libs.androidx.core)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.google.android.material)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.concurrent.futures)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.robolectric)

    coreLibraryDesugaring(libs.android.tools.desugar)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // https://mvnrepository.com/artifact/org.dmfs/lib-recur
    implementation(libs.dmfs.lib.recur)

    // lifecycle
    implementation(libs.androidx.lifecycle.livedata)

    implementation(libs.pvot.design.system)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
