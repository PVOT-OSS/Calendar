/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar.event

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.provider.CalendarContract.Attendees
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import org.prauga.calendar.R
import kotlin.math.abs
import kotlin.math.min

object GuestAvatar {

    private val TILE_COLORS = intArrayOf(
        0xFF1A73E8.toInt(), 0xFFD93025.toInt(), 0xFF188038.toInt(), 0xFFE37400.toInt(),
        0xFF9334E6.toInt(), 0xFF007B83.toInt(), 0xFFC5221F.toInt(), 0xFF5F6368.toInt()
    )

    @JvmStatic
    fun letterTile(name: String?, email: String?): Drawable {
        val label = name?.takeIf { it.isNotBlank() } ?: email.orEmpty()
        val letter = label.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
        val key = (email?.takeIf { it.isNotBlank() } ?: label).lowercase()
        return LetterTile(letter, TILE_COLORS[abs(key.hashCode() % TILE_COLORS.size)])
    }

    @JvmStatic
    fun withStatus(context: Context, picture: Drawable, status: Int): Drawable {
        val (icon, color) = when (status) {
            Attendees.ATTENDEE_STATUS_ACCEPTED -> R.drawable.ic_rsvp_yes to 0xFF188038.toInt()
            Attendees.ATTENDEE_STATUS_DECLINED -> R.drawable.ic_rsvp_no to 0xFFD93025.toInt()
            Attendees.ATTENDEE_STATUS_TENTATIVE -> R.drawable.ic_rsvp_maybe to 0xFF5F6368.toInt()
            else -> 0 to 0
        }
        val badge = if (icon != 0) ContextCompat.getDrawable(context, icon)?.mutate() else null
        val ring = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurface, Color.WHITE)
        return Avatar(picture, badge, color, ring)
    }

    private class LetterTile(private val letter: String, color: Int) : Drawable() {
        private val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        override fun draw(canvas: Canvas) {
            val b = bounds
            val r = min(b.width(), b.height()) / 2f
            canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), r, circle)
            text.textSize = r
            canvas.drawText(letter, b.exactCenterX(), b.exactCenterY() - (text.descent() + text.ascent()) / 2, text)
        }

        override fun setAlpha(alpha: Int) {
            circle.alpha = alpha
            text.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            circle.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }

    private class Avatar(
        private val picture: Drawable,
        private val badge: Drawable?,
        badgeColor: Int,
        ringColor: Int
    ) : Drawable() {
        private val clip = Path()
        private val badgeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = badgeColor }
        private val badgeRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ringColor }

        override fun onBoundsChange(bounds: Rect) {
            val size = min(bounds.width(), bounds.height())
            val left = bounds.centerX() - size / 2
            val top = bounds.centerY() - size / 2
            picture.setBounds(left, top, left + size, top + size)
            clip.reset()
            clip.addOval(RectF(picture.bounds), Path.Direction.CW)
        }

        override fun draw(canvas: Canvas) {
            canvas.save()
            canvas.clipPath(clip)
            picture.draw(canvas)
            canvas.restore()
            val icon = badge ?: return
            val p = picture.bounds
            val r = p.width() * 0.2f
            val cx = p.right - r
            val cy = p.bottom - r
            canvas.drawCircle(cx, cy, r + p.width() * 0.04f, badgeRing)
            canvas.drawCircle(cx, cy, r, badgeFill)
            val i = (r * 0.75f).toInt()
            icon.setBounds((cx - i).toInt(), (cy - i).toInt(), (cx + i).toInt(), (cy + i).toInt())
            icon.setTint(Color.WHITE)
            icon.draw(canvas)
        }

        override fun getIntrinsicWidth() = picture.intrinsicWidth
        override fun getIntrinsicHeight() = picture.intrinsicHeight

        override fun setAlpha(alpha: Int) = picture.setAlpha(alpha)

        override fun setColorFilter(colorFilter: ColorFilter?) {
            picture.colorFilter = colorFilter
        }

        @Deprecated("Deprecated in Java")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
