package com.showcase.desertant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.showcase.desertant.ui.ShowcaseNavHost
import com.showcase.desertant.ui.theme.ShowcaseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val hub = (application as ShowcaseApp).hub
        setContent {
            ShowcaseTheme {
                ShowcaseNavHost(hub)
            }
        }
    }
}
