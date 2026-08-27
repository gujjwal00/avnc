/*
 * Copyright (c) 2026  Gaurav Ujjwal.
 *
 * SPDX-License-Identifier:  GPL-3.0-or-later
 *
 * See COPYING.txt for more details.
 */

package com.gaurav.avnc

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.biometric.auth.AuthPrompt
import androidx.biometric.auth.AuthPromptCallback
import androidx.biometric.auth.AuthPromptErrorException
import androidx.biometric.auth.authenticateWithClass2BiometricsOrCredentials
import androidx.biometric.auth.startClass2BiometricOrCredentialAuthentication
import androidx.fragment.app.FragmentActivity
import io.mockk.clearStaticMockk
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.delay
import org.junit.Assert
import kotlin.time.Duration.Companion.seconds

/**
 * Helper class to mock biometric authentication.
 * It specifically targets internals of [com.gaurav.avnc.util.DeviceAuthPrompt].
 */
object BiometricMocking {
    var promptHost: FragmentActivity? = null
    var promptCallback: AuthPromptCallback? = null

    /**
     * Start mocking biometric calls
     */
    fun start(isAuthAvailable: Boolean = true, coAuthSuccess: Boolean = false) {
        check(Build.VERSION.SDK_INT >= 28) { "Mocking static functions is not possible on older versions" }
        check(promptHost == null && promptCallback == null)

        val biometricMock = mockk<BiometricManager>()
        if (isAuthAvailable)
            every { biometricMock.canAuthenticate(any()) } returns BiometricManager.BIOMETRIC_SUCCESS
        else
            every { biometricMock.canAuthenticate(any()) } returns BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED

        mockkStatic(BiometricManager::class)
        mockkStatic(FragmentActivity::startClass2BiometricOrCredentialAuthentication)
        mockkStatic(FragmentActivity::authenticateWithClass2BiometricsOrCredentials)

        every { BiometricManager.from(any()) } returns biometricMock
        every { ofType<FragmentActivity>().startClass2BiometricOrCredentialAuthentication(any(), any(), any(), any(), any(), any()) } answers {
            promptHost = firstArg()  // Target of extension function
            promptCallback = lastArg()
            mockk<AuthPrompt>()
        }

        if (coAuthSuccess)
            coEvery { ofType<FragmentActivity>().authenticateWithClass2BiometricsOrCredentials(any(), any(), any(), any()) } coAnswers {
                delay(1.seconds) // Add some delay to simulate user action
                mockk<BiometricPrompt.AuthenticationResult>()
            }
        else
            coEvery { ofType<FragmentActivity>().authenticateWithClass2BiometricsOrCredentials(any(), any(), any(), any()) } throws (
                    AuthPromptErrorException(BiometricPrompt.ERROR_UNABLE_TO_PROCESS, "Some random error"))
    }

    fun end() {
        promptHost = null
        promptCallback = null
        clearStaticMockk(BiometricManager::class)
        clearStaticMockk(FragmentActivity::startClass2BiometricOrCredentialAuthentication)
    }

    fun endWithSuccess() {
        waitForAuthStart()
        runOnMainSync {
            promptCallback?.onAuthenticationSucceeded(promptHost, mockk<BiometricPrompt.AuthenticationResult>())
        }
        end()
    }

    fun endWithError(errorMessage: String) {
        waitForAuthStart()
        runOnMainSync {
            promptCallback?.onAuthenticationError(promptHost, 123, errorMessage)
        }
        end()
    }

    private fun waitForAuthStart() {
        pollingAssert {
            Assert.assertNotNull(promptHost)
            Assert.assertNotNull(promptCallback)
        }
    }
}