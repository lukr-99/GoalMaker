package com.goalmaker.app.data.auth

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.goalmaker.app.application.auth.UnlockAvailability

/**
 * The phone's own prompt, behind the app lock (docs/sign-in.md). It asks for a weak biometric or
 * the screen lock, which is the one pairing androidx.biometric supports on every Android the app
 * runs on, and it means a phone with no fingerprint enrolled can still be unlocked with its PIN.
 *
 * Android 8 is left out on purpose: below API 28 the library draws its own fingerprint dialog with
 * AppCompat, and this app's window theme is not an AppCompat one to draw it in.
 */
class DeviceUnlock(context: Context) {
    private val appContext = context.applicationContext

    /** What the phone can do right now. Asked again each time, because the owner can change it. */
    fun availability(): UnlockAvailability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return UnlockAvailability.UNAVAILABLE
        return when (BiometricManager.from(appContext).canAuthenticate(ALLOWED)) {
            BiometricManager.BIOMETRIC_SUCCESS -> UnlockAvailability.READY
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> UnlockAvailability.NOTHING_ENROLLED
            else -> UnlockAvailability.UNAVAILABLE
        }
    }

    /**
     * Puts the prompt up. [onUnlocked] runs when the owner proves who they are; [onGaveUp] when
     * they close it or the phone stops asking, which leaves the lock where it was rather than
     * anywhere final, because the lock screen still offers the emailed code.
     */
    fun ask(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onUnlocked: () -> Unit,
        onGaveUp: (String?) -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onUnlocked()

                // Closing the prompt says nothing worth repeating; a phone that has stopped
                // asking, after too many tries, does.
                override fun onAuthenticationError(code: Int, message: CharSequence) =
                    onGaveUp(message.toString().takeUnless { code in CLOSED_IT })
            },
        )
        // No negative button: the prompt refuses one when the screen lock is among the ways in.
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(ALLOWED)
                .setConfirmationRequired(false)
                .build(),
        )
    }

    private companion object {
        const val ALLOWED = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

        val CLOSED_IT = setOf(
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_CANCELED,
        )
    }
}
