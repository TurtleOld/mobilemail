package com.mobilemail.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupBaselineProfile {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generateStartupProfile() {
        val packageName = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: "com.mobilemail"

        baselineProfileRule.collect(packageName) {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.pkg(packageName).depth(0)), 10_000)
        }
    }
}
