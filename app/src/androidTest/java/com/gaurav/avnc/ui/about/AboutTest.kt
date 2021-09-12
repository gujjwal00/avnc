/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.about

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaurav.avnc.R
import com.gaurav.avnc.checkIsDisplayed
import com.gaurav.avnc.doClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutTest {

    @Rule
    @JvmField
    val activityRule = ActivityScenarioRule(AboutActivity::class.java)

    @Test
    fun librariesTest() {
        onView(withId(R.id.library_btn)).doClick()

        //Check some libs
        onView(withText("LibVNCClient")).checkIsDisplayed()
        onView(withText("Libjpeg-turbo")).checkIsDisplayed()
    }

    @Test
    fun licenseTest() {
        onView(withId(R.id.licence_btn)).doClick()
        onView(withId(R.id.license_text)).checkIsDisplayed()
        onView(withSubstring("GNU General Public License, version 3 or later")).checkIsDisplayed()
    }
}