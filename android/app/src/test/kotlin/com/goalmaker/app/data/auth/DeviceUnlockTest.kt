package com.goalmaker.app.data.auth

import android.os.Build
import androidx.biometric.BiometricManager
import com.goalmaker.app.application.auth.UnlockAvailability
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the app makes of each answer the phone can give (docs/sign-in.md). The phone the lock was
 * tried on has a fingerprint on it, so this is where the other answers are covered.
 */
class DeviceUnlockTest {
    private fun on(sdkInt: Int = Build.VERSION_CODES.VANILLA_ICE_CREAM, answer: Int) =
        DeviceUnlock.readAvailability(sdkInt, answer)

    @Test
    fun `a phone that can ask is ready`() {
        assertEquals(UnlockAvailability.READY, on(answer = BiometricManager.BIOMETRIC_SUCCESS))
    }

    @Test
    fun `a phone with nothing enrolled says so, because the owner can fix that`() {
        assertEquals(
            UnlockAvailability.NOTHING_ENROLLED,
            on(answer = BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED),
        )
    }

    @Test
    fun `every other answer is a phone that cannot ask`() {
        val cannot = listOf(
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN,
        )
        cannot.forEach { answer ->
            assertEquals("$answer", UnlockAvailability.UNAVAILABLE, on(answer = answer))
        }
    }

    @Test
    fun `Android 8 cannot ask however willing it says it is`() {
        // Below API 28 androidx.biometric draws its own AppCompat dialog, which this app's window
        // theme cannot host, so a yes from the phone is still a no from the app.
        assertEquals(
            UnlockAvailability.UNAVAILABLE,
            on(sdkInt = Build.VERSION_CODES.O_MR1, answer = BiometricManager.BIOMETRIC_SUCCESS),
        )
        assertEquals(
            UnlockAvailability.READY,
            on(sdkInt = Build.VERSION_CODES.P, answer = BiometricManager.BIOMETRIC_SUCCESS),
        )
    }

    @Test
    fun `the prompt asks for a weak biometric or the screen lock`() {
        // The one pairing androidx.biometric supports on every Android this app runs on, and what
        // gives a phone with no fingerprint enrolled its PIN as the way in.
        assertEquals(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            DeviceUnlock.ALLOWED,
        )
    }
}
