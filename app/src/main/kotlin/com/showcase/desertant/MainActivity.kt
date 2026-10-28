package com.showcase.desertant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.showcase.desertant.ui.shell.BurrowApp
import com.showcase.desertant.ui.theme.ShowcaseTheme

class MainActivity : ComponentActivity() {
    /** A route to open, from a notification tap. */
    private val deepLink = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        deepLink.value = intent?.getStringExtra(EXTRA_ROUTE)
        setContent {
            ShowcaseTheme {
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
