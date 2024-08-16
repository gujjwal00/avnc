/*
 * Copyright (c) 2023  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

/*
 * Copyright (c) 2023  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc.ui.vnc

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onIdle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.gaurav.avnc.R
import com.gaurav.avnc.checkIsDisplayed
import com.gaurav.avnc.checkIsNotDisplayed
import com.gaurav.avnc.checkWillBeDisplayed
import com.gaurav.avnc.doClick
import com.gaurav.avnc.doTypeText
import com.gaurav.avnc.inDialog
import com.gaurav.avnc.model.LoginInfo
import com.gaurav.avnc.model.ServerProfile
import com.gaurav.avnc.model.db.MainDb
import com.gaurav.avnc.runOnMainSync
import com.gaurav.avnc.targetContext
import com.gaurav.avnc.viewmodel.VncViewModel
import com.gaurav.avnc.withActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.Closeable
import kotlin.concurrent.thread

private const val SAMPLE_USERNAME = "Chandler"
private const val SAMPLE_PASSWORD = "Bing"

class LoginFragmentTest {

    private class Scenario(private val profileTemplate: ServerProfile? = null) : Closeable {
        private val dao = MainDb.getInstance(targetContext).serverProfileDao
        private val profile = setupProfile()
        private var loginInfo = LoginInfo()
        private val activityScenario = ActivityScenario.launch<VncActivity>(createVncIntent(targetContext, profile))
        private val viewModel = activityScenario.withActivity { viewModel }
        private var loginInfoThread: Thread? = null

        private fun setupProfile() = runBlocking {
            dao.deleteAll()
            (profileTemplate?.copy() ?: ServerProfile()).apply { ID = dao.insert(this) }
        }

        fun triggerLoginInfoRequest(type: LoginInfo.Type) {
            loginInfoThread = thread { loginInfo = viewModel.getLoginInfo(type) }
        }

        fun waitForLoginInfo(): LoginInfo {
            loginInfoThread!!.join(5000)
            return loginInfo
        }

        fun triggerLoginSave(): ServerProfile {
            viewModel.state.postValue(VncViewModel.State.Connected)
            onIdle()
            return viewModel.profile
        }

        override fun close() {
            activityScenario.close()
            runBlocking { dao.deleteAll() }
        }
    }

    private fun passwordLogin(type: LoginInfo.Type) = Scenario().use { scenario ->
        scenario.triggerLoginInfoRequest(type)
        onView(withId(R.id.password)).inDialog().checkWillBeDisplayed().doTypeText(SAMPLE_PASSWORD)
        onView(withId(R.id.username)).inDialog().checkIsNotDisplayed()
        onView(withText(android.R.string.ok)).inDialog().checkIsDisplayed().doClick()
        assertEquals(SAMPLE_PASSWORD, scenario.waitForLoginInfo().password)
    }

    @Test
    fun vncPasswordLogin() = passwordLogin(LoginInfo.Type.VNC_PASSWORD)

    @Test
    fun sshPasswordLogin() = passwordLogin(LoginInfo.Type.SSH_PASSWORD)

    @Test
    fun sshKeyPasswordLogin() = passwordLogin(LoginInfo.Type.SSH_KEY_PASSWORD)

    @Test
    fun vncCredentialLoginWithRememberChecked() = Scenario().use { scenario ->
        scenario.triggerLoginInfoRequest(LoginInfo.Type.VNC_CREDENTIAL)
        onView(withId(R.id.username)).inDialog().checkWillBeDisplayed().doTypeText(SAMPLE_USERNAME)
        onView(withId(R.id.password)).inDialog().checkWillBeDisplayed().doTypeText(SAMPLE_PASSWORD)
        onView(withId(R.id.remember)).inDialog().checkIsDisplayed().doClick()
        onView(withText(android.R.string.ok)).inDialog().checkIsDisplayed().doClick()

        val l = scenario.waitForLoginInfo()
        val p = scenario.triggerLoginSave()
        assertEquals(SAMPLE_USERNAME, l.username)
        assertEquals(SAMPLE_PASSWORD, l.password)
        assertEquals(SAMPLE_USERNAME, p.username)
        assertEquals(SAMPLE_PASSWORD, p.password)
    }

    @Test
    fun vncCredentialLoginWhenPasswordIsAvailable() = Scenario(ServerProfile(password = SAMPLE_PASSWORD)).use { scenario ->
        scenario.triggerLoginInfoRequest(LoginInfo.Type.VNC_CREDENTIAL)
        onView(withId(R.id.username)).inDialog().checkWillBeDisplayed().doTypeText(SAMPLE_USERNAME)
        onView(withId(R.id.password)).inDialog().checkIsNotDisplayed()
        onView(withText(android.R.string.ok)).inDialog().checkIsDisplayed().doClick()

        val l = scenario.waitForLoginInfo()
        assertEquals(SAMPLE_USERNAME, l.username)
        assertEquals(SAMPLE_PASSWORD, l.password)
    }

    @Test
    fun sshPasswordLoginWithRememberChecked() = Scenario().use { scenario ->
        scenario.triggerLoginInfoRequest(LoginInfo.Type.SSH_PASSWORD)
        onView(withId(R.id.password)).inDialog().checkWillBeDisplayed().doTypeText(SAMPLE_PASSWORD)
        onView(withId(R.id.remember)).inDialog().checkIsDisplayed().doClick()
        onView(withText(android.R.string.ok)).inDialog().checkIsDisplayed().doClick()

        val l = scenario.waitForLoginInfo()
        val p = scenario.triggerLoginSave()
        assertEquals(SAMPLE_PASSWORD, l.password)
        assertEquals(SAMPLE_PASSWORD, p.sshPassword)
    }

    // We no longer save Private Key password. It is always asked from user and a message is shown to users
    // who have previously saved key password.
    @Test
    fun sshKeyPasswordMigrationMessage() = Scenario(ServerProfile(sshPrivateKeyPassword = "foo")).use { scenario ->
        scenario.triggerLoginInfoRequest(LoginInfo.Type.SSH_KEY_PASSWORD)
        onView(withId(R.id.password)).inDialog().checkWillBeDisplayed().doTypeText(SAMPLE_PASSWORD)
        onView(withId(R.id.remember)).inDialog().checkIsNotDisplayed()
        onView(withId(R.id.pk_password_msg)).inDialog().checkIsDisplayed()
        onView(withText(android.R.string.ok)).inDialog().checkIsDisplayed().doClick()

        val l = scenario.waitForLoginInfo()
        val p = scenario.triggerLoginSave()
        assertEquals(SAMPLE_PASSWORD, l.password)
        assertEquals("", p.sshPrivateKeyPassword) // Saved password should have been cleared
    }

    /**
     * If login information is already available in profile,
     * [VncViewModel] should provide it without triggering login dialog.
     */
    @Test(timeout = 5000)
    fun savedLoginTest() {
        val profile = ServerProfile(username = "AB", password = "BC", sshPassword = "CD")
        val viewModel = runOnMainSync { VncViewModel(profile, ApplicationProvider.getApplicationContext()) }

        assertEquals("AB", viewModel.getLoginInfo(LoginInfo.Type.VNC_CREDENTIAL).username)
        assertEquals("BC", viewModel.getLoginInfo(LoginInfo.Type.VNC_CREDENTIAL).password)
        assertEquals("BC", viewModel.getLoginInfo(LoginInfo.Type.VNC_PASSWORD).password)
        assertEquals("CD", viewModel.getLoginInfo(LoginInfo.Type.SSH_PASSWORD).password)
    }
}
