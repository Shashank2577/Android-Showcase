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

/** The outcome of asking the user to confirm it's them. */
sealed interface Confirmation {
    /** Confirmed; [crypto] is the object the prompt unlocked, if one was passed. */
    data class Confirmed(val crypto: BiometricPrompt.CryptoObject?) : Confirmation

    /** The user backed out; nothing to report. */
    data object Cancelled : Confirmation

    /** The system refused (lockout, no hardware…); [message] is Android's own explanation. */
    data class Failed(val message: String) : Confirmation
}

/** The platform fingerprint / face / screen-lock prompt; no extra library needed on Android 11+. */
object Biometrics {
    /** The vault and the app lock need a screen lock to tie their keys to. */
    fun deviceSecure(context: Context): Boolean = context.getSystemService(KeyguardManager::class.java).isDeviceSecure

    /** Ask the user to confirm it's them. True only on success. */
    suspend fun confirm(context: Context, title: String, subtitle: String? = null): Boolean =
        prompt(context, title, subtitle, null) is Confirmation.Confirmed

    /**
     * Ask the user to confirm it's them, optionally unlocking [crypto] (a cipher
     * whose Keystore key needs the user for every use).
     */
    suspend fun prompt(context: Context, title: String, subtitle: String?, crypto: BiometricPrompt.CryptoObject?): Confirmation {
        val activity = context.findActivity() ?: return Confirmation.Failed("No screen to show the prompt on")
        return suspendCancellableCoroutine { cont ->
            val cancel = CancellationSignal()
            cont.invokeOnCancellation { cancel.cancel() }
            val prompt = BiometricPrompt.Builder(activity)
                .setTitle(title)
                .apply { if (subtitle != null) setSubtitle(subtitle) }
                .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL)
                .setConfirmationRequired(false)
                .build()
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resume(Confirmation.Confirmed(result.cryptoObject))
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (!cont.isActive) return
                    val cancelled = errorCode == BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
                        errorCode == BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                    cont.resume(if (cancelled) Confirmation.Cancelled else Confirmation.Failed(errString.toString()))
                }
            }
            try {
                if (crypto != null) prompt.authenticate(crypto, cancel, activity.mainExecutor, callback)
                else prompt.authenticate(cancel, activity.mainExecutor, callback)
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(Confirmation.Failed(e.message ?: e.javaClass.simpleName))
            }
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
