/*
 * Copyright (c) 2024  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.prefs

import android.animation.LayoutTransition
import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.ToggleButton
import androidx.annotation.Keep
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.core.view.doOnLayout
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.FragmentVirtualKeysEditorBinding
import com.gaurav.avnc.ui.vnc.VirtualKey
import com.gaurav.avnc.ui.vnc.VirtualKeyLayoutConfig
import com.gaurav.avnc.ui.vnc.VirtualKeyViewFactory
import com.gaurav.avnc.util.AppPreferences
import com.google.android.material.snackbar.Snackbar


/**
 * Editor for virtual key layout
 */
@Keep
class VirtualKeysEditor : Fragment() {

    // Wraps a VirtualKey & corresponding View
    private data class KeyWrapper(val vk: VirtualKey, val view: View)

    lateinit var binding: FragmentVirtualKeysEditorBinding
    private val focusOverlay by lazy { AppCompatResources.getDrawable(requireContext(), R.drawable.focus_overlay)!! }
    private val prefs by lazy { AppPreferences(requireContext()) }

    private val keyList = ArrayList<KeyWrapper>()
    private var focusedKey: KeyWrapper? = null

    override fun onResume() {
        super.onResume()
        activity?.setTitle(R.string.pref_customize_virtual_keys)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentVirtualKeysEditorBinding.inflate(inflater, container, false)

        binding.keyGrid.rowCount = prefs.input.vkRowCount
        binding.keyGrid.orientation = GridLayout.VERTICAL
        binding.keyGrid.layoutTransition.apply {
            setDuration(150)
            setStartDelay(LayoutTransition.CHANGE_DISAPPEARING, 150)
            addTransitionListener(object : LayoutTransition.TransitionListener {
                override fun startTransition(t: LayoutTransition, c: ViewGroup, view: View, tt: Int) {}
                override fun endTransition(t: LayoutTransition, c: ViewGroup, view: View, tt: Int) {
                    if (view == focusedKey?.view)
                        refreshFocus()
                }
            })
        }

        keyList.clear()
        focusedKey = null

        // Populate keys
        val focusIndex = savedInstanceState?.getInt("focus_index") ?: -1
        val enabledKeys = savedInstanceState?.getStringArray("key_names")?.map { VirtualKey.valueOf(it) }
                          ?: VirtualKeyLayoutConfig.getLayout(prefs)
        enabledKeys.forEach { addNewKey(it) }
        if (focusIndex >= 0) {
            setFocusedKey(keyList[focusIndex].view)
            binding.keyGrid.doOnLayout { refreshFocus() }
        }

        binding.addKeyBtn.setOnClickListener { showNewKeyPopup() }
        binding.moveUpBtn.setOnClickListener { focusedKey?.let { moveKey(it, -1) } }
        binding.moveDownBtn.setOnClickListener { focusedKey?.let { moveKey(it, 1) } }
        binding.deleteBtn.setOnClickListener { focusedKey?.let { removeKey(it) } }
        updateActionButtons()

        binding.saveBtn.setOnClickListener { saveKeys() }
        binding.cancelBtn.setOnClickListener { finish() }

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.add(R.string.title_load_defaults).setOnMenuItemClickListener { loadDefaults(); true }
            }

            override fun onMenuItemSelected(menuItem: MenuItem) = false
        }, viewLifecycleOwner)

        return binding.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArray("key_names", keyList.map { it.vk.name }.toTypedArray())
        outState.putInt("focus_index", keyList.indexOf(focusedKey))
    }

    private fun finish() {
        parentFragmentManager.popBackStack()
    }

    private fun saveKeys() {
        val enabledKeys = keyList.map { it.vk }
        VirtualKeyLayoutConfig.setLayout(prefs, enabledKeys)
        Snackbar.make(requireView(), R.string.msg_saved, Snackbar.LENGTH_SHORT).show()
        finish()
    }

    private fun loadDefaults() {
        while (keyList.isNotEmpty())
            removeKey(keyList.last())
        VirtualKeyLayoutConfig.getDefaultLayout(prefs).forEach { addNewKey(it) }
    }

    private fun updateActionButtons() {
        val index = focusedKey?.let { keyList.indexOf(it) } ?: -1
        binding.moveUpBtn.isEnabled = index > 0
        binding.moveDownBtn.isEnabled = index >= 0 && index < (keyList.size - 1)
        binding.deleteBtn.isEnabled = index >= 0 && keyList.size > 1
    }

    private fun addNewKey(vk: VirtualKey): KeyWrapper {
        check(keyList.find { it.vk == vk } == null) { "Duplicate key detected" }

        val view = VirtualKeyViewFactory.create(requireContext(), vk)
        binding.keyGrid.addView(view)
        view.contentDescription = vk.description
        view.setOnClickListener {
            if (focusedKey?.view == view) setFocusedKey(null) // Clear focus on 2nd click
            else setFocusedKey(view)
            (view as? ToggleButton)?.isChecked = false  // Keep toggle keys unchecked
        }

        val wrapper = KeyWrapper(vk, view)
        keyList.add(wrapper)

        updateActionButtons()
        return wrapper
    }

    private fun removeKey(key: KeyWrapper) {
        binding.keyGrid.removeView(key.view)
        keyList.remove(key)

        if (focusedKey == key)
            setFocusedKey(null)

        updateActionButtons()
    }

    private fun moveKey(key: KeyWrapper, delta: Int) {
        val currentIndex = keyList.indexOf(key)
        val newIndex = currentIndex + delta
        check(currentIndex == binding.keyGrid.indexOfChild(key.view)) { "View index mismatch" }

        keyList.removeAt(currentIndex)
        keyList.add(newIndex, key)

        // Instead of removing current view, we remove the target view.
        // This gives a better layout animation where the view at new
        // position disappears and current view moves in its place.
        check(delta == 1 || delta == -1)
        val viewAtNew = binding.keyGrid.getChildAt(newIndex)
        binding.keyGrid.removeViewAt(newIndex)
        binding.keyGrid.addView(viewAtNew, currentIndex)

        updateActionButtons()
    }

    private fun setFocusedKey(keyView: View?) {
        focusedKey?.view?.overlay?.clear()
        focusedKey = keyList.find { it.view == keyView }
        focusedKey?.view?.overlay?.add(focusOverlay)
        refreshFocus()

        binding.focusedKeyLabel.text = focusedKey?.view?.contentDescription ?: ""
        updateActionButtons()
    }

    private fun refreshFocus() {
        focusedKey?.view?.let { v ->
            if (v.width > 0 || v.height > 0) {
                focusOverlay.setBounds(0, 0, v.width, v.height)
                val r = Rect()
                v.getDrawingRect(r)
                v.requestRectangleOnScreen(r)
            }
        }
    }

    /**
     * For now, only one instance of a particular [VirtualKey] is added in key list.
     * But this could be changed if required in future.
     */
    private fun selectOrAddKey(virtualKey: VirtualKey) {
        var key = keyList.find { it.vk == virtualKey }
        if (key == null)
            key = addNewKey(virtualKey)
        setFocusedKey(key.view)
    }

    private fun showNewKeyPopup() {
        var popup: PopupWindow? = null
        val context = requireContext()
        val root = ScrollView(context).apply {
            setPadding(context.resources.getDimensionPixelSize(R.dimen.padding_normal))
            addView(
                    GridLayout(context).apply {
                        columnCount = 6
                        VirtualKey.entries.forEach { virtualKey ->
                            addView(VirtualKeyViewFactory.create(context, virtualKey).apply {
                                setOnClickListener {
                                    selectOrAddKey(virtualKey)
                                    popup?.dismiss()
                                }
                            })
                        }
                    })
        }

        popup = PopupWindow(root, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        popup.setBackgroundDrawable(ContextCompat.getDrawable(context, R.drawable.bg_round_rect))
        popup.isOutsideTouchable = true
        popup.isFocusable = true
        popup.elevation = 30f
        popup.showAsDropDown(binding.addKeyBtn)
    }
}