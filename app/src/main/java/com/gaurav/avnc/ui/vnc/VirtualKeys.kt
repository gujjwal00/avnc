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
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.Gravity
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ToggleButton
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.VirtualKeysBinding
import com.gaurav.avnc.util.AppPreferences
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
        initTextPage(binding)
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


    private fun initTextPage(binding: VirtualKeysBinding) {
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

    }

    private fun initKeys(binding: VirtualKeysBinding) {
        VirtualKeyLayoutConfig.getLayout(pref).forEach { vk ->
            val view = VirtualKeyViewFactory.create(binding.root.context, vk)
            binding.keys.addView(view)

            if (vk == VirtualKey.ToggleKeyboard) {
                view.setOnClickListener {
                    @Suppress("DEPRECATION")
                    ContextCompat.getSystemService(frameView.context, InputMethodManager::class.java)
                            ?.toggleSoftInput(0, 0)
                }
            } else if (vk == VirtualKey.CloseKeys) {
                view.setOnClickListener { hide(true) }
            } else if (vk.keyCode != null) {
                if (view is ToggleButton)
                    initToggleKey(view, vk.keyCode)
                else
                    initNormalKey(view, vk.keyCode)
            }
        }
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
 * NOTE: Names of these enums may be persisted in app preferences. So if any key name
 *       is ever modified, add a migration to handle old name.
 */
enum class VirtualKey(
        /**
         * [KeyEvent] keycode to be generated when this key is pressed.
         */
        val keyCode: Int? = null,

        /**
         * If key name is not appropriate for UI, use this to set the label.
         */
        val label: String? = null,

        /**
         * If icon is set, this key will be rendered as an ImageButton.
         */
        @DrawableRes
        val icon: Int? = null,

        /**
         * Short description of the key, if the itself isn't sufficient.
         */
        val description: String? = null,

        val isToggle: Boolean = false,
) {

    // Special actions
    ToggleKeyboard(description = "Toggle keyboard", icon = R.drawable.ic_keyboard),
    CloseKeys(description = "Close virtual keys", icon = R.drawable.ic_clear),

    // Meta keys
    RightShift(keyCode = KeyEvent.KEYCODE_SHIFT_RIGHT, label = "Shift", isToggle = true),
    RightCtrl(keyCode = KeyEvent.KEYCODE_CTRL_RIGHT, label = "Ctrl", isToggle = true),
    RightAlt(keyCode = KeyEvent.KEYCODE_ALT_RIGHT, label = "Alt", isToggle = true),
    RightSuper(keyCode = KeyEvent.KEYCODE_META_RIGHT, label = "Super", icon = R.drawable.ic_super_key, isToggle = true),

    Esc(keyCode = KeyEvent.KEYCODE_ESCAPE),
    Tab(keyCode = KeyEvent.KEYCODE_TAB),
    Home(keyCode = KeyEvent.KEYCODE_MOVE_HOME),
    End(keyCode = KeyEvent.KEYCODE_MOVE_END),
    PgUp(keyCode = KeyEvent.KEYCODE_PAGE_UP),
    PgDn(keyCode = KeyEvent.KEYCODE_PAGE_DOWN),
    Insert(keyCode = KeyEvent.KEYCODE_INSERT),
    Delete(keyCode = KeyEvent.KEYCODE_FORWARD_DEL),

    // Arrow keys
    Left(keyCode = KeyEvent.KEYCODE_DPAD_LEFT, icon = R.drawable.ic_keyboard_arrow_left),
    Right(keyCode = KeyEvent.KEYCODE_DPAD_RIGHT, icon = R.drawable.ic_keyboard_arrow_right),
    Up(keyCode = KeyEvent.KEYCODE_DPAD_UP, icon = R.drawable.ic_keyboard_arrow_up),
    Down(keyCode = KeyEvent.KEYCODE_DPAD_DOWN, icon = R.drawable.ic_keyboard_arrow_down),

    F1(keyCode = KeyEvent.KEYCODE_F1),
    F2(keyCode = KeyEvent.KEYCODE_F2),
    F3(keyCode = KeyEvent.KEYCODE_F3),
    F4(keyCode = KeyEvent.KEYCODE_F4),
    F5(keyCode = KeyEvent.KEYCODE_F5),
    F6(keyCode = KeyEvent.KEYCODE_F6),
    F7(keyCode = KeyEvent.KEYCODE_F7),
    F8(keyCode = KeyEvent.KEYCODE_F8),
    F9(keyCode = KeyEvent.KEYCODE_F9),
    F10(keyCode = KeyEvent.KEYCODE_F10),
    F11(keyCode = KeyEvent.KEYCODE_F11),
    F12(keyCode = KeyEvent.KEYCODE_F12),
}

/**
 * Users can change the layout of keys in app settings.
 * Layout configuration is stored as a simple list of key-names.
 */
object VirtualKeyLayoutConfig {

    private val DEFAULT_LAYOUT = listOf(VirtualKey.ToggleKeyboard, VirtualKey.CloseKeys, VirtualKey.Esc, VirtualKey.RightSuper,
                                        VirtualKey.Tab, VirtualKey.RightCtrl, VirtualKey.RightShift, VirtualKey.RightAlt,
                                        VirtualKey.Home, VirtualKey.Left, VirtualKey.Up, VirtualKey.Down, VirtualKey.End,
                                        VirtualKey.Right, VirtualKey.PgUp, VirtualKey.PgDn)

    /**
     * In older versions, before users could customize key layout, there was a pref to
     * 'Show all' keys. This layout is used for compatibility with that pref.
     */
    private val DEFAULT_LAYOUT_ALL = DEFAULT_LAYOUT +
                                     listOf(VirtualKey.Insert, VirtualKey.Delete, VirtualKey.F1, VirtualKey.F2, VirtualKey.F3,
                                            VirtualKey.F4, VirtualKey.F5, VirtualKey.F6, VirtualKey.F7, VirtualKey.F8,
                                            VirtualKey.F9, VirtualKey.F10, VirtualKey.F11, VirtualKey.F12)


    fun getDefaultLayout(pref: AppPreferences): List<VirtualKey> {
        return if (pref.input.vkShowAll) DEFAULT_LAYOUT_ALL else DEFAULT_LAYOUT
    }

    fun getLayout(pref: AppPreferences): List<VirtualKey> {
        runCatching {
            pref.input.vkLayout?.let { vkLayout ->
                vkLayout.split(',').map { VirtualKey.valueOf(it) }.let { keys ->
                    check(keys.isNotEmpty())
                    return keys
                }
            }
        }.onFailure { Log.e(javaClass.simpleName, "Error parsing key layout [${pref.input.vkLayout}]: ", it) }

        return getDefaultLayout(pref)
    }

    fun setLayout(pref: AppPreferences, keys: List<VirtualKey>) {
        if (keys == getDefaultLayout(pref) && pref.input.vkLayout != null) {
            // Restoring the defaults, so simply remove the pref.
            // Pref is only used if user changes the default layout.
            pref.input.vkLayout = null
            return
        }

        if (keys == getLayout(pref))
            return   // Nothing changed

        pref.input.vkLayout = keys.joinToString(",") { it.name }
    }
}

/**
 * Factory for creating individual key [View]s.
 */
object VirtualKeyViewFactory {

    /**
     * There are three types of Views tht are generated:
     *
     * [ToggleButton] - if [key] is a toggle
     * [ImageButton]  - if [key] has an icon (label will be ignored)
     * [Button]       - in all other cases
     */
    fun create(context: Context, key: VirtualKey): View {
        val view = if (key.isToggle) createToggle(context, key) else createSimple(context, key)
        view.layoutParams = GridLayout.LayoutParams().apply {
            width = GridLayout.LayoutParams.WRAP_CONTENT
            height = GridLayout.LayoutParams.WRAP_CONTENT
            setGravity(Gravity.CENTER)
        }
        return view
    }

    private fun createSimple(context: Context, key: VirtualKey): View {
        return if (key.icon != null)
            ImageButton(context, null, 0, selectStyle(key))
                    .apply {
                        setImageDrawable(ContextCompat.getDrawable(context, key.icon))
                        contentDescription = getDescription(key)
                    }
        else
            Button(context, null, 0, selectStyle(key))
                    .apply { text = getLabel(key) }
    }

    private fun createToggle(context: Context, key: VirtualKey): View {
        val view = ToggleButton(context, null, 0, selectStyle(key))
        view.isClickable = true

        if (key.icon != null) {
            view.setCompoundDrawablesRelativeWithIntrinsicBounds(key.icon, 0, 0, 0)
            view.contentDescription = getDescription(key)
        } else {
            val label = getLabel(key)
            view.text = label
            view.textOff = label
            view.textOn = label
        }

        return view
    }

    private fun selectStyle(key: VirtualKey): Int {
        if (key == VirtualKey.CloseKeys || key == VirtualKey.ToggleKeyboard)
            return R.style.VirtualKey_Special

        if (key.isToggle) {
            return if (key.icon != null) R.style.VirtualKey_Toggle_Image else R.style.VirtualKey_Toggle
        }

        return R.style.VirtualKey
    }

    private fun getLabel(virtualKey: VirtualKey) = virtualKey.label ?: virtualKey.name
    private fun getDescription(virtualKey: VirtualKey) = virtualKey.description ?: getLabel(virtualKey)
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
