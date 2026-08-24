/*
 * Copyright (c) 2026 Gaurav Ujjwal.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.gaurav.avnc.ui.vnc.workspace

import android.content.Context
import android.util.AttributeSet
import android.view.accessibility.AccessibilityEvent
import android.widget.Checkable
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/** Centered image button that exposes its workspace state as a checkable control. */
class WorkspaceToggleButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageButton(context, attrs), Checkable {
    private var checked = false

    init {
        ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(
                host: android.view.View,
                info: AccessibilityNodeInfoCompat
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.ToggleButton"
                info.isCheckable = true
                info.isChecked = checked
            }

            override fun onInitializeAccessibilityEvent(
                host: android.view.View,
                event: AccessibilityEvent
            ) {
                super.onInitializeAccessibilityEvent(host, event)
                event.className = "android.widget.ToggleButton"
                event.isChecked = checked
            }
        })
    }

    override fun isChecked() = checked

    override fun setChecked(value: Boolean) {
        if (checked == value) return
        checked = value
        isSelected = value
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
    }

    override fun toggle() = setChecked(!checked)
}
