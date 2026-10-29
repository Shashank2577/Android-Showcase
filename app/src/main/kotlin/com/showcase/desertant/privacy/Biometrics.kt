package com.showcase.desertant.privacy

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** The platform fingerprint / face / screen-lock prompt; no extra library needed on Android 11+. */
object Biometrics {
    /** The vault and the app lock need a screen lock to tie their keys to. */
    fun deviceSecure(context: Context): Boolean = context.getSystemService(KeyguardManager::class.java).isDeviceSecure

    /** Ask the user to confirm it's them. Returns true on success, false if they cancelled or failed. */
    suspend fun confirm(context: Context, title: String, subtitle: String? = null): Boolean {
        val activity = context.findActivity() ?: return false
        return suspendCancellableCoroutine { cont ->
            val cancel = CancellationSignal()
            cont.invokeOnCancellation { cancel.cancel() }
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(title)
                .apply { if (subtitle != null) setSubtitle(subtitle) }
                .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL)
                .setConfirmationRequired(false)
                .build()
            prompt.authenticate(
                cancel,
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
            )
        }
    }
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
