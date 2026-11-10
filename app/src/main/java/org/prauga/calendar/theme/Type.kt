/*
 * SPDX-FileCopyrightText: 2026 Shivansh Pandey <shivanshp987@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.prauga.pvot.designsystem.theme.PvotTypography
import org.prauga.calendar.R

/** Lenia Sans in the four weights the Material type scale uses. */
val LeniaSans: FontFamily =
    FontFamily(
        Font(R.font.lenia_sans_regular, FontWeight.Normal),
        Font(R.font.lenia_sans_medium, FontWeight.Medium),
        Font(R.font.lenia_sans_semibold, FontWeight.SemiBold),
        Font(R.font.lenia_sans_bold, FontWeight.Bold),
    )

private fun TextStyle.inLenia(): TextStyle = copy(fontFamily = LeniaSans)

/** The Pvot type scale with every style set in Lenia Sans; sizes and weights are unchanged. */
val CalendarTypography: Typography =
    with(PvotTypography) {
        Typography(
            displayLarge = displayLarge.inLenia(),
            displayMedium = displayMedium.inLenia(),
            displaySmall = displaySmall.inLenia(),
            headlineLarge = headlineLarge.inLenia(),
            headlineMedium = headlineMedium.inLenia(),
            headlineSmall = headlineSmall.inLenia(),
            titleLarge = titleLarge.inLenia(),
            titleMedium = titleMedium.inLenia(),
            titleSmall = titleSmall.inLenia(),
            bodyLarge = bodyLarge.inLenia(),
            bodyMedium = bodyMedium.inLenia(),
            bodySmall = bodySmall.inLenia(),
            labelLarge = labelLarge.inLenia(),
            labelMedium = labelMedium.inLenia(),
            labelSmall = labelSmall.inLenia(),
        )
    }
