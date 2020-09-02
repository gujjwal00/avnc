/*
 * Copyright (c) 2020  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.gaurav.avnc.R
import com.gaurav.avnc.databinding.FragmentBookmarksBinding
import com.gaurav.avnc.ui.home.adapter.BookmarksAdapter
import com.gaurav.avnc.viewmodel.HomeViewModel

/**
 * Fragment for displaying list of bookmarks.
 */
class BookmarksFragment : Fragment() {
    val viewModel by activityViewModels<HomeViewModel>()
    private lateinit var binding: FragmentBookmarksBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_bookmarks, container, false)
        binding.lifecycleOwner = viewLifecycleOwner
        binding.viewModel = viewModel

        val adapter = BookmarksAdapter(viewModel)
        binding.bookmarksRv.layoutManager = LinearLayoutManager(context)
        binding.bookmarksRv.adapter = adapter
        binding.bookmarksRv.setHasFixedSize(true)

        binding.newBookmarkFab.setOnClickListener { viewModel.onNewBookmark() }

        viewModel.bookmarks.observe(viewLifecycleOwner, Observer { adapter.submitList(it) })

        return binding.root
    }
}