/*
 * Copyright (c) 2021  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.home

import androidx.core.content.edit
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ScrollToAction
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withHint
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gaurav.avnc.CleanPrefsRule
import com.gaurav.avnc.EmptyDatabaseRule
import com.gaurav.avnc.R
import com.gaurav.avnc.TestServer
import com.gaurav.avnc.checkIsDisplayed
import com.gaurav.avnc.checkIsNotDisplayed
import com.gaurav.avnc.checkWillBeDisplayed
import com.gaurav.avnc.doClick
import com.gaurav.avnc.doTypeText
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.performWithTimeout
import com.gaurav.avnc.setupFileOpenIntent
import com.gaurav.avnc.targetContext
import com.gaurav.avnc.targetPrefs
import org.hamcrest.core.AllOf.allOf
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BasicEditorTest {

    private val testProfile = ServerProfile(name = "foo", host = "bar")

    @Rule
    @JvmField
    val activityRule = ActivityScenarioRule(HomeActivity::class.java)

    @Rule
    @JvmField
    val dbRule = EmptyDatabaseRule()

    @Rule
    @JvmField
    val prefRule = CleanPrefsRule()

    @Test
    fun createSimpleProfile() {
        onView(withContentDescription(R.string.desc_add_new_server_btn)).doClick()
        onView(withText(R.string.title_add_server_profile)).inRoot(isDialog()).checkIsDisplayed()
        onView(withHint(R.string.hint_server_name)).doTypeText(testProfile.name)
        onView(withHint(R.string.hint_host)).doTypeText(testProfile.host)
        closeSoftKeyboard()
        onView(withText(R.string.title_save)).doClick()

        onView(withText(R.string.msg_server_profile_added)).checkWillBeDisplayed()
        onView(allOf(
                withParent(withId(R.id.servers_rv)),
                hasDescendant(withText(testProfile.name)),
                hasDescendant(withText(testProfile.host)))
        ).checkWillBeDisplayed()
    }

    private fun checkAdvancedModeIsOpen() {
        //This checkbox is only shown in advanced mode
        onView(withText(R.string.title_use_repeater)).checkWillBeDisplayed()
    }

    @Test
    fun createAdvancedProfile() {
        onView(withContentDescription(R.string.desc_add_new_server_btn)).doClick()
        onView(withText(R.string.title_advanced)).doClick()
        checkAdvancedModeIsOpen()
        onView(withHint(R.string.hint_server_name)).doTypeText(testProfile.name)
        onView(withHint(R.string.hint_host)).doTypeText(testProfile.host)
        closeSoftKeyboard()
        onView(withText(R.string.title_save)).doClick()

        onView(withText(R.string.msg_server_profile_added)).checkWillBeDisplayed()
        onView(allOf(
                withParent(withId(R.id.servers_rv)),
                hasDescendant(withText(testProfile.name)),
                hasDescendant(withText(testProfile.host)))
        ).checkWillBeDisplayed()
    }

    @Test
    fun dataSharingBetweenSimpleAndAdvanceMode() {
        // If user switches to advanced mode after making some changes in simple mode,
        // those changes should not be lost.
        onView(withContentDescription(R.string.desc_add_new_server_btn)).doClick()
        onView(withHint(R.string.hint_server_name)).doTypeText(testProfile.name)
        onView(withHint(R.string.hint_host)).doTypeText(testProfile.host)

        closeSoftKeyboard()
        onView(withText(R.string.title_advanced)).doClick()

        checkAdvancedModeIsOpen()
        onView(allOf(withHint(R.string.hint_host), withText(testProfile.host))).checkIsDisplayed()
        onView(allOf(withHint(R.string.hint_server_name), withText(testProfile.name))).checkIsDisplayed()
    }

    @Test
    fun directlyOpenAdvancedMode() {
        // open advanced mode
        onView(withContentDescription(R.string.desc_add_new_server_btn)).doClick()
        closeSoftKeyboard()
        onView(withText(R.string.title_advanced)).doClick()
        checkAdvancedModeIsOpen()

        // set as default
        Espresso.openActionBarOverflowOrOptionsMenu(targetContext)
        onView(withText(R.string.title_always_show_advanced_editor)).doClick()
        Espresso.pressBack()

        // it should now open by default
        onView(withContentDescription(R.string.desc_add_new_server_btn)).doClick()
        checkAdvancedModeIsOpen()
        Assert.assertTrue(targetPrefs.getBoolean("prefer_advanced_editor", false))
    }

    @Test
    fun tryProfileBeforeSaving() {
        onView(withContentDescription(R.string.desc_add_new_server_btn)).doClick()
        closeSoftKeyboard()
        onView(withText(R.string.title_advanced)).doClick()
        checkAdvancedModeIsOpen()

        val server = TestServer()
        server.start()
        onView(withHint(R.string.hint_host)).doTypeText(server.host)
        onView(withId(R.id.port)).perform(ViewActions.clearText()).doTypeText(server.port.toString())
        onView(withText(R.string.title_try)).doClick()

        onView(withId(R.id.frame_view)).checkWillBeDisplayed()
        onView(withId(R.id.status_container)).checkIsNotDisplayed()

        Espresso.pressBack()
        onView(withText(R.string.title_try)).checkWillBeDisplayed()
        checkAdvancedModeIsOpen()
    }
}

@RunWith(AndroidJUnit4::class)
class PrivateKeyTest {

    private val SAMPLE_KEY = """
        -----BEGIN EC PRIVATE KEY-----
        MHcCAQEEIOlzJCBaTq2RpkLatMn+PMwsQ/ncijqK5M6y5kPns+BAoAoGCCqGSM49
        AwEHoUQDQgAE5vfo9Is5+pnk+6dC3NlXA9rKNy0X6PdifyeI2OKKTGVA4cdhEOBh
        MXTgJKarp26FiFnsFuWEGOO61Q/Plmv8Wg==
        -----END EC PRIVATE KEY-----
    """.trimIndent()

    private val SAMPLE_ENCRYPTED_KEY = """
        -----BEGIN EC PRIVATE KEY-----
        Proc-Type: 4,ENCRYPTED
        DEK-Info: AES-128-CBC,D18B845F2B9D4B9FEB3472809F270AD2

        fNNjQEFap5uf3+gaQNI+A2ait7WZDjxPO6sT6MfOTQ34/HRxm7El4FWhhYMfIiGl
        5j/A30Je2OMbOEqTpiKZKYxKxWIZ27te8HpxsQkC9xvU9rxPPsd0Vt/7na5rbx1S
        Iqfy2ows7NJY3f3s/GlMY2ZmaAwk6TJ3DAJ+YK0KMrc=
        -----END EC PRIVATE KEY-----
    """.trimIndent()

    private val SAMPLE_PKCS8_RSA_KEY = """
            -----BEGIN PRIVATE KEY-----
            MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCdwT/QVDFbUAp
            unY5uN2x1ODEtN2rBkM4ZwGqJUkOcAQWyUzCzxgBw8PP+DYG6Z608tyniKO0EDz
            JLda3Ak1DU050tLwRAIrnq/Swp6hRyZ/uYOIAdQWX8DKhme93DV9bnDLY3frtny
            hI4EHnhaUbKtQFPVOvqQMjfpiN6B0wnQpeGJCwlqtjzoIEdEJ7yT8uxhJCd2Ok5
            2MhGWTdjY3wTGzSxpQ38XLy/S3+JDik0sufSLEzunEGfQfmkh7eBAJDXlI0nMfl
            QPuMc2BUzq1D9fagKEL3XwxzaZxgYHefH4CdVMS1LZ1qZJ3BcFLEzr9n6I05r9i
            fd1cU9ytSlPWCzAgMBAAECggEAIiICEK1m0H8NAsoMW0SarvItkb7/1knijifX5
            UZrYoHGHcNqMjuRNN6trDZ370Endo+a/FgmkE5Jb5JSuewl/SacR354yPe6imGl
            AJypN+fPxCvVbH8N9e83MJV0ciO7V9qkQnWlTtul/YNzG5aPvqRTWDrjoJfL3rg
            vzONvUr76zZbymCaBScVHKqRq9JhONe2nv6Mohsaityzs+zV9WOqIaNLgJypJFQ
            oCNrvsyaAwT6xsJBQdKAB0oUM/Au/yRTSp1agiptI9RlKjiN/9YBnsBLNdv9gUY
            K9ze0d3ptENMlTkpbdcBJBrDAwTYU3Dvv7q62swXpe3QCi0MjcHIQKBgQDQJhGe
            D8USokWzl0Z74iBAFe1chvv+Zr2vj3sVhlraU9sQPnzmZ9HofUoBpSslqA+8pJL
            GXXS5GRi/P0J+5CXRog+2y1dZFyc47TtD7HPXqGD101DRE6/diYhQDUNUIz6++8
            l6hBl1eYYnhpt4WbcrK9Ve9cox5lEWBiFXs9Yd3QKBgQDCBWls9JY9nGlNDf5Rs
            ggQzZC/AabTZGqT4R6d7OBTftZlhs/kGFybgm/+RiHG3H0mICeTH459HLLt9jT3
            zAd/fsN26/VW8bqBqd+CksLzlvv7GmFsmYIFK2ThaZ45jO51ytDu4tFduRPlUGX
            zgDAyXeQI1sGqQQfv2gA2DEf3zwKBgQCXjTBEmozNvXLsiNdb+c+reYuiU/IEUA
            AKZHikundKAcY9dJHyHGNcWGTa/8yDlXMn0dfAMGl9H8XB8ahTxX+3u7yfRjxp9
            I6tRyVgljfeLI20TtDH+gKRVcL3LkD4cNUNrzwKRUZYH0xLWRm3rfLMrxSjGGjI
            nj4pv/rk5V6fzQKBgQCE1Xmxa980vVJmu+7jddUJ1AOGkhXqYrSJHDZ+/v8yiwM
            0LVFFo9w7Z9lPKCrV4H0aTidqFc/THoPuYYMwKBL2Gg6u66tj5EnBnlD1L4+jgD
            pyV0ReOtcGvQfrQAlg83kLUlkrRET9OspBVIMIbDoTMa7+0jFzY+SQNRux5USch
            wKBgHI1FYE2Q9798a0dMoHqBAf1KQ669vOHbjIPsVWXtbrhYZ16FUgg3iR9RuI7
            aQ37HJvilQUHLuSSISpUWZ6tLtaWboVevFtuREs1bs1DvuM/8U6tRfqyt3Ec2GX
            GezpZeTJIsnBHhJTkAR53iTjV6QAevaiZfhuyPQiTQWM/Vcsg
            -----END PRIVATE KEY-----
        """.trimIndent()

    @Rule
    @JvmField
    val activityRule = ActivityScenarioRule(HomeActivity::class.java)

    @Before
    fun before() {
        Intents.init()
        targetPrefs.edit { putBoolean("prefer_advanced_editor", true) }
        onView(withContentDescription(R.string.desc_add_new_server_btn)).doClick()
        onView(withId(R.id.use_ssh_tunnel)).performWithTimeout(ScrollToAction())
        onView(withId(R.id.use_ssh_tunnel)).doClick()
        onView(withId(R.id.key_import_btn)).performWithTimeout(ScrollToAction())
    }

    @After
    fun after() {
        targetPrefs.edit { putBoolean("prefer_advanced_editor", false) }
        Intents.release()
    }

    @Test
    fun importValidPK() {
        setupFileOpenIntent(SAMPLE_KEY)
        onView(withId(R.id.key_import_btn)).doClick()
        onView(withText(R.string.msg_imported)).checkWillBeDisplayed()
    }

    @Test
    fun importEncryptedPK() {
        setupFileOpenIntent(SAMPLE_ENCRYPTED_KEY)
        onView(withId(R.id.key_import_btn)).doClick()
        onView(withText(R.string.msg_imported)).checkWillBeDisplayed()
    }

    @Test
    fun importPKCS8Key() {
        setupFileOpenIntent(SAMPLE_PKCS8_RSA_KEY)
        onView(withId(R.id.key_import_btn)).doClick()
        onView(withText(R.string.msg_imported)).checkWillBeDisplayed()
    }

    @Test
    fun importInvalidFile() {
        setupFileOpenIntent("This is not a Private Key, right?")
        onView(withId(R.id.key_import_btn)).doClick()
        onView(withText(R.string.msg_invalid_key_file)).checkWillBeDisplayed()
    }
}