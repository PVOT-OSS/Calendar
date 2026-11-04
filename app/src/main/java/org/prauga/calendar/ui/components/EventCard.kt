/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.components.PvotCardDefaults
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.components.list.PvotAvatar
import org.prauga.calendar.R

@Composable
fun EventCard(
    title: String,
    time: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    guests: List<String> = emptyList(),
    onJoin: (() -> Unit)? = null,
    done: Boolean = false,
    dense: Boolean = false,
) {
    PvotCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        colors = PvotCardDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(color, RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = if (dense) 8.dp else 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = if (dense) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (guests.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        AvatarStack(guests)
                    }
                }
                if (time.isNotEmpty()) {
                    Text(
                        text = time,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (onJoin != null) {
                    Row(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        PvotButton(
                            text = stringResource(R.string.ui_join),
                            onClick = onJoin,
                            style = PvotButtonStyle.Tonal,
                            icon = painterResource(R.drawable.ic_ui_video),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AvatarStack(names: List<String>, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val shown = names.take(3)
    val more = names.size - shown.size
    val spoken = shown.joinToString(", ") +
        if (more > 0) " " + pluralStringResource(R.plurals.ui_more_guests, more, more) else ""
    val step = size * 2 / 3
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = spoken },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            shown.forEachIndexed { index, name ->
                PvotAvatar(
                    name = name,
                    size = size,
                    ring = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(start = step * index),
                )
            }
        }
        if (more > 0) {
            Text(
                text = "+$more",
                modifier = Modifier.padding(start = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
