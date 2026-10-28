package com.showcase.desertant

import android.app.Application
import com.showcase.desertant.core.ModelHub
import com.showcase.desertant.data.AppDatabase
import com.showcase.desertant.notify.Notifications
import com.showcase.desertant.photos.PhotoInsights
import com.showcase.desertant.photos.PhotoInsightsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ShowcaseApp : Application() {
    lateinit var hub: ModelHub
        private set
    lateinit var db: AppDatabase
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Cleanup suggestions, shared by every Photos screen and kept warm for a minute. */
    val photoInsights: StateFlow<PhotoInsights?> by lazy {
        PhotoInsightsEngine(db.photos()).insights.stateIn(scope, SharingStarted.WhileSubscribed(60_000), null)
    }

    override fun onCreate() {
        super.onCreate()
        hub = ModelHub(this)
        // Must be set before the first model loads; covers every model in the app.
        hub.applyStoredApiKey(BuildConfig.DESERT_ANT_API_KEY)
        db = AppDatabase.create(this)
        Notifications.createChannels(this)
    }
}

val android.content.Context.app: ShowcaseApp get() = applicationContext as ShowcaseApp
