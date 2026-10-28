package com.showcase.desertant

import android.app.Application
import com.showcase.desertant.core.ModelHub

class ShowcaseApp : Application() {
    lateinit var hub: ModelHub
        private set

    override fun onCreate() {
        super.onCreate()
        hub = ModelHub(this)
        // Must be set before the first model loads; covers every model in the app.
        hub.applyStoredApiKey(BuildConfig.DESERT_ANT_API_KEY)
    }
}
