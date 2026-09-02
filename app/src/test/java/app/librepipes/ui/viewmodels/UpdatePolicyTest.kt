package app.librepipes.ui.viewmodels

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    @Test
    fun versionCode_onlyNewerReleaseIsAvailable() {
        assertFalse(isUpdateAvailable(currentVersionCode = 10, releaseVersionCode = 9))
        assertFalse(isUpdateAvailable(currentVersionCode = 10, releaseVersionCode = 10))
        assertTrue(isUpdateAvailable(currentVersionCode = 10, releaseVersionCode = 11))
    }

    @Test
    fun automaticCheck_isThrottledForTwentyFourHours() {
        val now = 10L * UPDATE_CHECK_INTERVAL_MS
        assertTrue(shouldAutomaticCheck(now, lastCheckAt = 0L))
        assertFalse(shouldAutomaticCheck(now, lastCheckAt = now - UPDATE_CHECK_INTERVAL_MS + 1L))
        assertTrue(shouldAutomaticCheck(now, lastCheckAt = now - UPDATE_CHECK_INTERVAL_MS))
        assertTrue(shouldAutomaticCheck(now, lastCheckAt = now + 1L))
    }
}
