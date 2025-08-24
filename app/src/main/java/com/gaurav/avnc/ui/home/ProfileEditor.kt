/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import android.app.Dialog
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.os.BundleCompat
import androidx.core.view.MenuProvider
import androidx.core.view.descendants
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.FragmentProfileEditorAdvancedBinding
import com.gaurav.avnc.databinding.FragmentProfileEditorBinding
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.util.MsgDialog
import com.gaurav.avnc.util.OpenableDocument
import com.gaurav.avnc.util.parseMacAddress
import com.gaurav.avnc.viewmodel.EditorViewModel
import com.gaurav.avnc.viewmodel.HomeViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.elevation.ElevationOverlayProvider
import com.google.android.material.snackbar.Snackbar
import com.trilead.ssh2.crypto.PEMDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/********************************************************************************
 * ServerProfile editor. There are two modes:
 *
 * Simple:
 * A simple Dialog with most common options.
 *
 * Advanced:
 * A fullscreen fragment attached to content root.
 * All available options are shown in this mode.
 *
 *******************************************************************************/

private const val PROFILE_KEY = "profile"

fun startProfileEditor(host: FragmentActivity, profile: ServerProfile, preferAdvancedEditor: Boolean) {
    if (preferAdvancedEditor)
        startAdvancedProfileEditor(host, profile)
    else
        startSimpleProfileEditor(host, profile)
}

private fun startSimpleProfileEditor(host: FragmentActivity, profile: ServerProfile) {
    SimpleProfileEditor().apply {
        arguments = Bundle().apply { putParcelable(PROFILE_KEY, profile) }
        show(host.supportFragmentManager, null)
    }
}

private fun startAdvancedProfileEditor(host: FragmentActivity, profile: ServerProfile) {
    val fragment = AdvancedProfileEditor().apply {
        arguments = Bundle().apply { putParcelable(PROFILE_KEY, profile) }
    }

    host.supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, fragment)
            .addToBackStack(null)
            .commit()
}

/**
 * Extracts [ServerProfile] passed to given fragment via argument.
 */
private fun getProfileArg(f: Fragment): ServerProfile {
    return f.arguments?.let { BundleCompat.getParcelable(it, PROFILE_KEY, ServerProfile::class.java) }
           ?: ServerProfile()
}

/**
 * Returns title string based on whether we are editing an existing profile, or creating a new profile.
 */
private fun getTitle(f: Fragment): Int {
    return if (getProfileArg(f).ID == 0L) R.string.title_add_server_profile
    else R.string.title_edit_server_profile
}


/**
 * If [preCondition] is `true`, validates that [target] is not empty.
 */
private fun validateNotEmpty(target: EditText, preCondition: Boolean = true): Boolean {
    if (preCondition && target.length() == 0) {
        target.error = target.context.getText(R.string.msg_required)
        return false
    }
    return true
}


/********************************************************************************
 * Simple mode
 *******************************************************************************/

class SimpleProfileEditor : DialogFragment() {
    private val homeViewModel by activityViewModels<HomeViewModel>()
    private val profile by lazy { getProfileArg(this) }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = FragmentProfileEditorBinding.inflate(layoutInflater, null, false)
        binding.profile = profile
        binding.lifecycleOwner = this
        isCancelable = false

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialog_Dimmed)
                .setTitle(getTitle(this))
                .setView(binding.root)
                .setPositiveButton(R.string.title_save) { _, _ -> /* See below */ }
                .setNegativeButton(R.string.title_cancel) { _, _ -> dismiss() }
                .setNeutralButton(R.string.title_advanced) { _, _ -> startAdvancedProfileEditor(requireActivity(), profile) }
                .setBackgroundInsetTop(0)
                .setBackgroundInsetBottom(0)
                .create()

        // Customize Save button directly to avoid Dialog dismissal if validation fails
        dialog.setOnShowListener {
            dialog.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener {
                if (validateNotEmpty(binding.host) and validateNotEmpty(binding.port)) {
                    homeViewModel.saveProfile(profile)
                    dismiss()
                }
            }
        }

        return dialog
    }
}


/********************************************************************************
 * Advanced mode
 *******************************************************************************/

class AdvancedProfileEditor : Fragment() {
    private val homeViewModel by activityViewModels<HomeViewModel>()
    private val viewModel by viewModels<EditorViewModel> { EditorViewModelFactory(this) }
    private lateinit var binding: FragmentProfileEditorAdvancedBinding
    private val keyFilePicker = registerForActivityResult(OpenableDocument()) { importPrivateKey(it) }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentProfileEditorAdvancedBinding.inflate(inflater, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        binding.toolbar.title = getString(getTitle(this))
        binding.tryBtn.setOnClickListener { tryConnection() }
        binding.saveBtn.setOnClickListener { save() }
        binding.cancelBtn.setOnClickListener { dismiss() }
        binding.toolbar.setNavigationOnClickListener { dismiss() }
        binding.keyImportBtn.setOnClickListener { keyFilePicker.launch(arrayOf("*/*")) }

        //setupHelpButton(binding.keyCompatModeHelpBtn, R.string.title_key_compat_mode, R.string.msg_key_compat_mode_help)
        setupHelpButton(binding.buttonUpDelayHelpBtn, R.string.title_button_up_delay, R.string.msg_button_up_delay_help)
        setupHelpButton(binding.wolHelpBtn, R.string.title_enable_wol, R.string.msg_wake_on_lan_help)
        setupNightModeNavBarColor()
        setupToolbarMenu()

        return binding.root
    }

    private fun setupHelpButton(button: View, title: Int, msg: Int) {
        button.setOnClickListener {
            MsgDialog.show(parentFragmentManager, getString(title), getString(msg))
        }
    }

    /**
     * When Dark theme is active, navigation bar background color should match
     * with the background color of bottom app bar.
     */
    private fun setupNightModeNavBarColor() {
        if ((resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES)
            return // Night mode is not active

        @Suppress("DEPRECATION")
        viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            var originalNavBarColor: Int? = null

            private fun getNewColor(): Int {
                val elevation = resources.getDimension(R.dimen.editor_bottom_bar_elevation)
                return ElevationOverlayProvider(requireContext())
                        .compositeOverlayWithThemeSurfaceColorIfNeeded(elevation)
            }

            override fun onStart(owner: LifecycleOwner) {
                requireActivity().window.let {
                    originalNavBarColor = it.navigationBarColor
                    it.navigationBarColor = getNewColor()
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                originalNavBarColor?.let {
                    requireActivity().window.navigationBarColor = it
                    originalNavBarColor = null
                }
            }
        })
    }

    private fun setupToolbarMenu() {
        binding.toolbar.addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menu.add(R.string.title_always_show_advanced_editor)
                        .setCheckable(true)
                        .setChecked(viewModel.pref.ui.preferAdvancedEditor)
                        .setOnMenuItemClickListener {
                            viewModel.pref.ui.apply {
                                preferAdvancedEditor = !preferAdvancedEditor
                                it.isChecked = preferAdvancedEditor
                            }
                            true
                        }
            }

            override fun onMenuItemSelected(menuItem: MenuItem) = false
        }, viewLifecycleOwner)
    }

    private fun dismiss() = parentFragmentManager.popBackStack()

    private fun save() {
        if (!validate()) {
            highlightFieldWithError()
            return
        }
        homeViewModel.saveProfile(viewModel.prepareProfileForSave())
        dismiss()
    }

    private fun tryConnection() {
        if (!validate()) {
            highlightFieldWithError()
            return
        }
        // Clear the ID to make sure VncActivity doesn't make any changes
        // to profile being edited here
        val profile = viewModel.prepareProfileForSave().copy(ID = 0)
        homeViewModel.startConnection(profile)
    }

    private fun validate(): Boolean {
        var result = validateNotEmpty(binding.host) and validateNotEmpty(binding.port)

        if (binding.useRepeater.isChecked)
            result = result and validateNotEmpty(binding.idOnRepeater)

        if (binding.wol.isChecked) {
            result = result and
                    (validateNotEmpty(binding.wolMac) && validateWolMACAddress()) and
                    validateNotEmpty(binding.wolPort)
        }

        if (binding.useSshTunnel.isChecked) {
            result = result and
                    validateNotEmpty(binding.sshHost) and
                    validateNotEmpty(binding.sshPort) and
                    validateNotEmpty(binding.sshUsername) and
                    validatePrivateKey()
        }

        return result
    }

    private fun highlightFieldWithError() {
        binding.scrollView.descendants.find { it.isVisible && it is TextView && it.error != null }?.let {
            // Try to focus the View, which would also reveal it.
            // If View isn't focusable, manually reveal  it.
            if (!it.requestFocus())
                scrollToView(it)
        }
    }

    private fun scrollToView(view: View) {
        if (Build.VERSION.SDK_INT >= 29)
            binding.scrollView.scrollToDescendant(view)
        else
            binding.scrollView.requestChildFocus(null, view) // Wierd but effective workaround
    }


    private fun validateWolMACAddress(): Boolean {
        runCatching {
            parseMacAddress(binding.wolMac.text.toString())
        }.onFailure {
            binding.wolMac.error = getText(R.string.msg_invalid_mac_address)
        }.let {
            return it.isSuccess
        }
    }

    private fun validatePrivateKey(): Boolean {
        if (binding.sshAuthTypeKey.isChecked && viewModel.hasSshPrivateKey.value != true) {
            binding.keyImportBtn.error = getText(R.string.msg_required)
            return false
        }
        return true
    }


    private fun importPrivateKey(uri: Uri?) {
        if (uri == null)
            return

        lifecycleScope.launch(Dispatchers.IO) {
            var key = ""
            val result = runCatching {
                requireContext().contentResolver.openAssetFileDescriptor(uri, "r")!!.use {
                    // Valid key files are only few KBs. So if selected file is too big,
                    // user has accidentally selected something else.
                    check(it.length < 2 * 1024 * 1024) { "File is too big [${it.length}]" }
                    key = it.createInputStream().use { s -> s.reader().use { r -> r.readText() } }
                }
                PEMDecoder.parsePEM(key.toCharArray()) //Try to parse key
            }

            withContext(Dispatchers.Main) {
                result.onSuccess {
                    viewModel.profile.sshPrivateKey = key
                    viewModel.hasSshPrivateKey.value = true
                    binding.keyImportBtn.error = null
                    Snackbar.make(binding.root, R.string.msg_imported, Snackbar.LENGTH_SHORT).show()
                }.onFailure {
                    MsgDialog.show(parentFragmentManager, getString(R.string.msg_invalid_key_file), it.message ?: "")
                    Log.e("ProfileEditor", "Error importing Private Key", it)
                }
            }
        }
    }

    private class EditorViewModelFactory(private val fragment: Fragment) : AbstractSavedStateViewModelFactory(fragment, null) {
        override fun <T : ViewModel> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T {
            val app = fragment.requireActivity().application
            val profile = getProfileArg(fragment)
            return modelClass.cast(EditorViewModel(app, handle, profile))!!
        }
    }
}
