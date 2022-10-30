/*
 * Copyright (c) 2022  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.prefs

import android.content.Context
import android.os.Bundle
import android.util.AttributeSet
import android.view.*
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.annotation.Keep
import androidx.core.view.forEach
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.FragmentKeyTestBinding
import com.gaurav.avnc.databinding.FragmentLogsBinding
import com.gaurav.avnc.databinding.FragmentTouchTestBinding
import com.gaurav.avnc.util.Debugging
import com.gaurav.avnc.viewmodel.PrefsViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

abstract class DebugFragment : Fragment() {
    abstract fun title(): String

    override fun onResume() {
        super.onResume()
        activity?.title = title()
    }

    fun copyLogs(logs: String) {
        val wrapped = Debugging.wrapLogs(title(), logs)
        activityViewModels<PrefsViewModel>().value.setClipboardText(wrapped)
        snackbar(getString(R.string.msg_copied_to_clipboard))
    }

    fun snackbar(text: String) = Snackbar.make(requireView(), text, Snackbar.LENGTH_SHORT).show()
}


/**
 * Provides easy access to Logcat.
 */
@Keep
class LogsFragment : DebugFragment() {

    override fun title() = getString(R.string.pref_logs)

    private var logs = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentLogsBinding.inflate(inflater, container, false)

        lifecycleScope.launchWhenCreated {
            withContext(Dispatchers.IO) {
                logs = Debugging.logcat()
            }

            binding.text.text = logs
            delay(100)
            binding.vScroll.fullScroll(View.FOCUS_DOWN)
        }

        binding.clearBtn.setOnClickListener {
            Debugging.clearLogs()
            snackbar("Cleared!")
            parentFragmentManager.popBackStack()
        }

        binding.copyBtn.setOnClickListener { copyLogs(logs) }
        return binding.root
    }
}


/**
 * This fragment is used to record [MotionEvent]s generated for a gesture.
 * It can help in understanding why a gesture is not working correctly on
 * a particular device. It also helps in understanding how [MotionEvent]s
 * are generated for other devices like Stylus, Touchpad etc.
 */
@Keep
class TouchTestFragment : DebugFragment() {

    override fun title() = getString(R.string.pref_touch_test)

    private val eventLog = StringBuilder()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentTouchTestBinding.inflate(inflater, container, false)

        @Suppress("ClickableViewAccessibility")
        binding.gestureArea.setOnTouchListener { _, e -> eventLog.appendLine("OnTouch: $e"); true }
        binding.gestureArea.setOnGenericMotionListener { _, e -> eventLog.appendLine("OnGeneric: $e"); true }
        binding.gestureArea.setOnHoverListener { _, e -> eventLog.appendLine("OnHover: $e"); true }

        binding.gestures.forEach { view ->
            view.setOnClickListener {
                eventLog.appendLine("\n\nGestureStart: " + (view as TextView).text.toString())
            }
        }

        binding.resetBtn.setOnClickListener {
            binding.gestures.clearCheck()
            eventLog.setLength(0)
            snackbar("Reset")
        }

        binding.copyBtn.setOnClickListener { copyLogs(eventLog.toString()) }
        return binding.root
    }
}

/**
 * This fragment is used to record [KeyEvent]s generated for different key presses.
 * It can help in understanding key combination generates which character.
 */
@Keep
class KeyTestFragment : DebugFragment() {
    override fun title() = getString(R.string.pref_key_test)

    lateinit var binding: FragmentKeyTestBinding
    private val eventLog = StringBuilder()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentKeyTestBinding.inflate(inflater, container, false)

        binding.inputArea.setOnKeyListener { _, _, event ->
            eventLog.appendLine("$event")
            event.dispatch(binding.preview, binding.preview.keyDispatcherState, binding.preview)
        }

        binding.inputArea.setOnClickListener {
            with(requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager) {
                binding.inputArea.requestFocus()
                showSoftInput(binding.inputArea, 0)
            }
        }

        binding.resetBtn.setOnClickListener {
            eventLog.setLength(0)
            snackbar("Reset")
        }

        binding.copyBtn.setOnClickListener {
            eventLog.appendLine("\n\nPreview text: " + binding.preview.text)
            copyLogs(eventLog.toString())
        }

        binding.inputArea.requestFocus()
        return binding.root
    }

    override fun onStop() {
        super.onStop()
        if (binding.autoCopy.isChecked)
            binding.copyBtn.callOnClick()
    }
}

class KeyTestView(context: Context, attributeSet: AttributeSet?) : View(context, attributeSet) {
    override fun onCreateInputConnection(outAttrs: EditorInfo): BaseInputConnection {
        outAttrs.imeOptions = outAttrs.imeOptions or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        return BaseInputConnection(this, false)
    }
}
