/*
 * Copyright (c) 2026 Gaurav Ujjwal.
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.gaurav.avnc.ui.vnc.workspace

import android.graphics.Color
import android.view.Gravity
import android.view.KeyEvent
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.view.setPadding
import com.gaurav.avnc.ui.vnc.VncActivity
import com.gaurav.avnc.ui.vnc.input.Dispatcher
import com.gaurav.avnc.ui.vnc.input.InputHandler

/** Touchpad plus compact modifier/navigation controls for large-screen sessions. */
class WorkspaceInputPanel @JvmOverloads constructor(
    context: android.content.Context, attrs: android.util.AttributeSet? = null
) : LinearLayout(context, attrs) {
    private lateinit var inputHandler: InputHandler
    private val density get() = resources.displayMetrics.density
    private val modifiers = mutableListOf<Button>()
    private val lockedModifiers = mutableSetOf<Button>()

    fun initialize(activity: VncActivity, inputHandler: InputHandler, dispatcher: Dispatcher) {
        this.inputHandler = inputHandler
        orientation = VERTICAL
        setPadding((8 * density).toInt())
        setBackgroundColor(Color.argb(235, 30, 30, 30))

        val trackpad = TrackpadView(context).apply {
            initialize(dispatcher)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            contentDescription = context.getString(com.gaurav.avnc.R.string.desc_workspace_trackpad)
        }
        addView(trackpad)

        val firstRow = row()
        listOf(
            "⌘" to KeyEvent.KEYCODE_META_LEFT,
            "⌥" to KeyEvent.KEYCODE_ALT_LEFT,
            "Ctrl" to KeyEvent.KEYCODE_CTRL_LEFT,
            "Shift" to KeyEvent.KEYCODE_SHIFT_LEFT,
            "Esc" to KeyEvent.KEYCODE_ESCAPE
        ).forEach { (label, key) -> firstRow.addView(modifier(label, key)) }
        addView(firstRow)

        val secondRow = row()
        listOf(
            "Tab" to KeyEvent.KEYCODE_TAB,
            "←" to KeyEvent.KEYCODE_DPAD_LEFT,
            "↑" to KeyEvent.KEYCODE_DPAD_UP,
            "↓" to KeyEvent.KEYCODE_DPAD_DOWN,
            "→" to KeyEvent.KEYCODE_DPAD_RIGHT
        ).forEach { (label, key) -> secondRow.addView(keyButton(label, key)) }
        secondRow.addView(Button(context).apply {
            text = "⌨"
            contentDescription = "Open keyboard"
            setOnClickListener { activity.showKeyboard() }
            layoutParams = buttonParams()
        })
        addView(secondRow)
    }

    private fun row() = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }

    private fun buttonParams() = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
        setMargins((2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt(), (2 * density).toInt())
    }

    private fun keyButton(label: String, keyCode: Int) = Button(context).apply {
        text = label
        tag = keyCode
        setOnClickListener {
            sendKey(keyCode)
            modifiers.filter { it.isActivated && it !in lockedModifiers }
                .forEach { setModifierState(it, false) }
        }
        layoutParams = buttonParams()
    }

    private fun modifier(label: String, keyCode: Int) = Button(context).apply {
        text = label
        contentDescription = label
        setOnClickListener { setModifierState(this, !isActivated, keyCode) }
        setOnLongClickListener {
            setModifierState(this, !isActivated, keyCode)
            if (isActivated) lockedModifiers += this else lockedModifiers -= this
            true
        }
        layoutParams = buttonParams()
        modifiers += this
    }

    private fun setModifierState(button: Button, active: Boolean, keyCode: Int? = null) {
        if (button.isActivated == active) return
        button.isActivated = active
        button.alpha = if (active) .65f else 1f
        sendKey(keyCode ?: (button.tag as Int), active)
    }

    private fun sendKey(keyCode: Int, down: Boolean = true) {
        inputHandler.onVkKeyEvent(KeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, keyCode))
    }
}
