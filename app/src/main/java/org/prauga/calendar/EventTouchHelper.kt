/*
 * SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.prauga.calendar

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityEvent
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper

class EventTouchHelper(host: View, private val source: Source) : ExploreByTouchHelper(host) {

    interface Source {
        fun accessibleEventCount(): Int

        fun accessibleEventBounds(index: Int, out: Rect)

        fun describeAccessibleEvent(index: Int): CharSequence

        fun openAccessibleEvent(index: Int)
    }

    private val rect = Rect()

    override fun getVirtualViewAt(x: Float, y: Float): Int {
        for (i in source.accessibleEventCount() - 1 downTo 0) {
            source.accessibleEventBounds(i, rect)
            if (rect.contains(x.toInt(), y.toInt())) return i
        }
        return INVALID_ID
    }

    override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
        for (i in 0 until source.accessibleEventCount()) virtualViewIds.add(i)
    }

    override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
        if (virtualViewId >= source.accessibleEventCount()) {
            node.contentDescription = ""
            @Suppress("DEPRECATION")
            node.setBoundsInParent(Rect(0, 0, 1, 1))
            return
        }
        source.accessibleEventBounds(virtualViewId, rect)
        node.contentDescription = source.describeAccessibleEvent(virtualViewId)
        @Suppress("DEPRECATION")
        node.setBoundsInParent(rect)
        node.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK)
        node.isClickable = true
    }

    override fun onPerformActionForVirtualView(virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
        if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false
        if (virtualViewId >= source.accessibleEventCount()) return false
        source.openAccessibleEvent(virtualViewId)
        invalidateVirtualView(virtualViewId)
        sendEventForVirtualView(virtualViewId, AccessibilityEvent.TYPE_VIEW_CLICKED)
        return true
    }
}
