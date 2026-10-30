package com.showcase.desertant.ui.privacy

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.showcase.desertant.app
import com.showcase.desertant.privacy.findActivity

/**
 * Screens that show private things (the vault, the private sweep, a masked
 * text) can never be screenshotted or shown in recent apps, whatever the
 * global "hide from screenshots" switch says.
 */
@Composable
fun SecureScreen() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (!context.app.privacy.secureScreens.value) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
