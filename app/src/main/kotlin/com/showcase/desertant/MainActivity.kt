package com.showcase.desertant

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.showcase.desertant.ui.shell.BurrowApp
import com.showcase.desertant.ui.theme.BurrowTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    /** A route to open, from a notification tap. */
    private val deepLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        deepLink.value = intent?.getStringExtra(EXTRA_ROUTE)

        // "Hide from screenshots & recents": no screen capture, and a blank card in the app switcher.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                app.privacy.secureScreens.collect { secure ->
                    if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!secure)
                }
            }
        }

        setContent {
            BurrowTheme {
                BurrowApp(app.hub, deepLink.value) { deepLink.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { deepLink.value = it }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}
