/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ToggleButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.gaurav.avnc.databinding.VirtualKeysBinding
import kotlin.math.min
import kotlin.math.sign


/**
 * Virtual keys allow the user to input keys which are not normally found on
 * keyboards but can be useful for controlling remote server.
 *
 * This class manages the inflation & visibility of virtual keys.
 */
class VirtualKeys(activity: VncActivity) {

    private val viewModel = activity.viewModel
    private val pref = activity.viewModel.pref
    private val keyHandler = activity.keyHandler
    private val frameView = activity.binding.frameView
    private val stub = activity.binding.virtualKeysStub
    private val toggleKeys = mutableSetOf<ToggleButton>()
    private val lockedToggleKeys = mutableSetOf<ToggleButton>()
    private val keyCharMap by lazy { KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD) }
    private var openedWithKb = false

    val container: View? get() = stub.root

    fun show(saveVisibility: Boolean = false) {
        init()
        container?.visibility = View.VISIBLE
        if (saveVisibility) pref.runInfo.showVirtualKeys = true
    }

    fun hide(saveVisibility: Boolean = false) {
        container?.visibility = View.GONE
        openedWithKb = false //Reset flag
        if (saveVisibility) pref.runInfo.showVirtualKeys = false
    }

    fun onKeyboardOpen() {
        if (pref.input.vkOpenWithKeyboard && container?.visibility != View.VISIBLE) {
            show()
            openedWithKb = true
        }
    }

    fun onKeyboardClose() {
        if (openedWithKb) {
            hide()
            openedWithKb = false
        }
    }

    fun onConnected(inPiP: Boolean) {
        if (!inPiP && pref.runInfo.showVirtualKeys)
            show()
    }

    fun releaseMetaKeys() {
        toggleKeys.forEach {
            if (it.isChecked)
                it.isChecked = false
        }
    }

    private fun releaseUnlockedMetaKeys() {
        toggleKeys.forEach {
            if (it.isChecked && !lockedToggleKeys.contains(it))
                it.isChecked = false
        }
    }

    private fun onAfterKeyEvent(event: KeyEvent) {
        if (event.action == KeyEvent.ACTION_UP && !KeyEvent.isModifierKey(event.keyCode))
            releaseUnlockedMetaKeys()
    }

    private fun init() {
        if (stub.isInflated)
            return

        stub.viewStub?.inflate()
        val binding = stub.binding as VirtualKeysBinding
        initControls(binding)
        initKeys(binding)
        initPager(binding)
        keyHandler.processedEventObserver = ::onAfterKeyEvent
    }

    /**
     * To keep everything in single XML layout file, things are done in a slightly weird way.
     * Both keys & text pages are initially attached to temporary View. After inflation, they
     * are detached and passed onto ViewPager adapter. Adapter will insert them at proper place.
     */
    private fun initPager(binding: VirtualKeysBinding) {
        val root = binding.root
        val keys = binding.keys
        val pager = binding.pager
        val pages = listOf(binding.keysPage, binding.textPage)

        if (!pref.input.vkShowAll)
            keys.removeViews(16, 14)

        binding.tmpPageHost.apply {
            removeAllViews()
            (parent as ViewGroup).removeView(this)
        }

        // Setup pager
        pager.offscreenPageLimit = pages.size
        pager.adapter = object : PagerAdapter() {
            override fun getCount() = pages.size
            override fun isViewFromObject(view: View, obj: Any) = (view === obj)
            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                pages[position].let {
                    container.addView(it)
                    return it
                }
            }

            override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
                container.removeView(obj as View)
            }
        }
        pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
            val textPageIndex = pages.indexOf(binding.textPage)
            override fun onPageSelected(position: Int) {
                if (position == textPageIndex) binding.textBox.requestFocus()
                else frameView.requestFocus()
            }
        })

        // Setup Layout. Keys grid is the primary View used for deciding size of Virtual keys.
        // All keys are shown if screen is wide enough. Otherwise width is limited to FrameView,
        // and HorizontalScrollView is relied upon to access all keys.
        // NOTE: Paddings in root/pager view is NOT handled by this code.

        // Start with something sane
        MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED).let { keys.measure(it, it) }
        root.layoutParams = root.layoutParams.apply { width = keys.measuredWidth; height = keys.measuredHeight }

        // Update size after layout changes
        keys.viewTreeObserver.addOnGlobalLayoutListener {
            val w = min(keys.width, frameView.width)
            val h = keys.height
            if (w > 0 && h > 0 && (root.width != w || root.height != h))
                root.layoutParams = root.layoutParams.apply { width = w; height = h }
        }
    }


    private fun initControls(binding: VirtualKeysBinding) {
        binding.toggleKeyboard.setOnClickListener {
            @Suppress("DEPRECATION")
            ContextCompat.getSystemService(frameView.context, InputMethodManager::class.java)
                    ?.toggleSoftInput(0, 0)
        }
        binding.textPageBackBtn.setOnClickListener {
            binding.pager.setCurrentItem(0, true)
        }
        binding.textBox.setOnEditorActionListener { _, _, _ ->
            handleTextBoxAction(binding.textBox)
            true
        }
        binding.textBox.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) frameView.requestFocus()
        }
        binding.textBox.onTextCopyListener = {
            viewModel.sendClipboardText()
        }
        binding.closeBtn.setOnClickListener {
            hide(true)
        }
    }

    private fun initKeys(binding: VirtualKeysBinding) {
        initToggleKey(binding.vkSuper, KeyEvent.KEYCODE_META_LEFT)
        initToggleKey(binding.vkShift, KeyEvent.KEYCODE_SHIFT_RIGHT) // See if we can switch to 'LEFT' versions of these
        initToggleKey(binding.vkAlt, KeyEvent.KEYCODE_ALT_RIGHT)
        initToggleKey(binding.vkCtrl, KeyEvent.KEYCODE_CTRL_RIGHT)

        initNormalKey(binding.vkEsc, KeyEvent.KEYCODE_ESCAPE)
        initNormalKey(binding.vkTab, KeyEvent.KEYCODE_TAB)
        initNormalKey(binding.vkHome, KeyEvent.KEYCODE_MOVE_HOME)
        initNormalKey(binding.vkEnd, KeyEvent.KEYCODE_MOVE_END)
        initNormalKey(binding.vkPageUp, KeyEvent.KEYCODE_PAGE_UP)
        initNormalKey(binding.vkPageDown, KeyEvent.KEYCODE_PAGE_DOWN)
        initNormalKey(binding.vkInsert, KeyEvent.KEYCODE_INSERT)
        initNormalKey(binding.vkDelete, KeyEvent.KEYCODE_FORWARD_DEL)

        initNormalKey(binding.vkLeft, KeyEvent.KEYCODE_DPAD_LEFT)
        initNormalKey(binding.vkRight, KeyEvent.KEYCODE_DPAD_RIGHT)
        initNormalKey(binding.vkUp, KeyEvent.KEYCODE_DPAD_UP)
        initNormalKey(binding.vkDown, KeyEvent.KEYCODE_DPAD_DOWN)

        initNormalKey(binding.vkF1, KeyEvent.KEYCODE_F1)
        initNormalKey(binding.vkF2, KeyEvent.KEYCODE_F2)
        initNormalKey(binding.vkF3, KeyEvent.KEYCODE_F3)
        initNormalKey(binding.vkF4, KeyEvent.KEYCODE_F4)
        initNormalKey(binding.vkF5, KeyEvent.KEYCODE_F5)
        initNormalKey(binding.vkF6, KeyEvent.KEYCODE_F6)
        initNormalKey(binding.vkF7, KeyEvent.KEYCODE_F7)
        initNormalKey(binding.vkF8, KeyEvent.KEYCODE_F8)
        initNormalKey(binding.vkF9, KeyEvent.KEYCODE_F9)
        initNormalKey(binding.vkF10, KeyEvent.KEYCODE_F10)
        initNormalKey(binding.vkF11, KeyEvent.KEYCODE_F11)
        initNormalKey(binding.vkF12, KeyEvent.KEYCODE_F12)
    }


    private fun initToggleKey(key: ToggleButton, keyCode: Int) {
        key.setOnCheckedChangeListener { _, isChecked ->
            keyHandler.onKeyEvent(keyCode, isChecked)
            if (!isChecked) lockedToggleKeys.remove(key)
        }
        key.setOnLongClickListener {
            key.toggle()
            if (key.isChecked) lockedToggleKeys.add(key)
            true
        }
        toggleKeys.add(key)
    }

    private fun initNormalKey(key: View, keyCode: Int) {
        check(key !is ToggleButton) { "use initToggleKey()" }
        key.setOnClickListener { keyHandler.onKey(keyCode) }
        makeKeyRepeatable(key)
    }

    /**
     * When a View is touched, we schedule a callback to to simulate a click.
     * As long as finger stays on the view, we keep repeating this callback.
     */
    private fun makeKeyRepeatable(keyView: View) {
        keyView.setOnTouchListener(object : View.OnTouchListener {
            private var doRepeat = false

            private fun repeat(v: View) {
                if (doRepeat) {
                    v.performClick()
                    v.postDelayed({ repeat(v) }, ViewConfiguration.getKeyRepeatDelay().toLong())
                }
            }

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        doRepeat = true
                        v.postDelayed({ repeat(v) }, ViewConfiguration.getKeyRepeatTimeout().toLong())
                    }

                    MotionEvent.ACTION_POINTER_DOWN,
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        doRepeat = false
                    }
                }
                return false
            }
        })
    }

    private fun handleTextBoxAction(textBox: EditText) {
        val text = textBox.text?.ifEmpty { "\n" }?.toString() ?: return
        val events = keyCharMap.getEvents(text.toCharArray())

        if (events == null || text.contains('ç', true))
            keyHandler.onKeyEvent(KeyEvent(SystemClock.uptimeMillis(), text, 0, 0))
        else
            events.forEach { keyHandler.onKeyEvent(it) }

        textBox.setText("")
    }
}

/**
 * Simple extension to add hook for Copy action.
 */
class VkEditText(context: Context, attributeSet: AttributeSet? = null) : AppCompatEditText(context, attributeSet) {

    var onTextCopyListener: (() -> Unit)? = null

    override fun onTextContextMenuItem(id: Int): Boolean {
        val result = super.onTextContextMenuItem(id)
        if (result && (id == android.R.id.cut || id == android.R.id.copy)) {
            onTextCopyListener?.invoke()
        }
        return result
    }
}

/**
 * Stock [HorizontalScrollView] intercepts all scroll events irrespective of whether
 * it can actually scroll or not. It makes it unsuitable for use as child/parent of
 * another horizontally scrollable View, e.g. ViewPager.
 *
 * [NestableHorizontalScrollView] fixes this by only intercepting events when it is scrollable.
 */
class NestableHorizontalScrollView(context: Context, attributeSet: AttributeSet? = null) :
        HorizontalScrollView(context, attributeSet) {
    /**
     * Direction of current horizontal scrolling.
     * See [canScrollHorizontally].
     */
    private var hScrollDirection = 0
    private val gestureDetector = GestureDetector(context, object : SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            hScrollDirection = 0
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            hScrollDirection = distanceX.sign.toInt()
            return true
        }
    })

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        if (hScrollDirection != 0 && !canScrollHorizontally(hScrollDirection))
            return false

        return super.onInterceptTouchEvent(ev)
    }
}
