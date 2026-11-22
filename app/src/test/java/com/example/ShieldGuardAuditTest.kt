package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.SensitivityLevel
import com.example.data.scanner.AppSecurityAnalyzer
import com.example.data.scanner.DevicePostureAuditor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31, 32])
class ShieldGuardAuditTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivity_renders_and_switches_tabs_without_crash() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("quick_audit_button").assertIsDisplayed()
        composeRule.onNodeWithTag("full_apk_scan_button").assertIsDisplayed()
        composeRule.onNodeWithTag("overview_list")
            .performScrollToNode(hasTestTag("inspect_file_button"))
        composeRule.onNodeWithTag("inspect_file_button").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_apps").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("nav_logs").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("update_signatures_button").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_overview").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("quick_audit_button").assertIsDisplayed()
    }

    @Test
    fun devicePostureAndAppAudit_workOnMultipleApiLevels() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val apps = AppSecurityAnalyzer.auditInstalledApps(
            context = context,
            sensitivity = SensitivityLevel.MEDIUM,
            blacklistedHashes = emptySet()
        )
        val posture = DevicePostureAuditor.inspect(
            context = context,
            auditedApps = apps,
            trustedPackages = emptySet()
        )
        assertTrue(posture.checks.isNotEmpty())
        assertTrue(posture.score in 0..100)
    }
}
