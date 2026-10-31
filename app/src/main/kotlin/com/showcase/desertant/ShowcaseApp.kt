package com.showcase.desertant

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.showcase.desertant.core.ModelHub
import com.showcase.desertant.data.AppDatabase
import com.showcase.desertant.notify.Notifications
import com.showcase.desertant.photos.PhotoInsights
import com.showcase.desertant.photos.PhotoInsightsEngine
import com.showcase.desertant.privacy.Housekeeping
import com.showcase.desertant.privacy.Ledger
import com.showcase.desertant.privacy.MaskSession
import com.showcase.desertant.privacy.PrivacySettings
import com.showcase.desertant.privacy.Vault
import com.showcase.desertant.speech.SpeechPacks
import com.showcase.desertant.ui.theme.Appearance
import dev.baseplatform.base_models.SharedModelStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShowcaseApp : Application() {
    lateinit var hub: ModelHub
        private set

    /** The shared model store: Burrow's models, shared with the vendor's other apps on this phone. */
    lateinit var models: SharedModelStore
        private set
    lateinit var db: AppDatabase
        private set
    lateinit var privacy: PrivacySettings
        private set
    lateinit var ledger: Ledger
        private set
    lateinit var vault: Vault
        private set
    lateinit var mask: MaskSession
        private set
    lateinit var appearance: Appearance
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Cleanup suggestions, shared by every Photos screen and kept warm for a minute. */
    val photoInsights: StateFlow<PhotoInsights?> by lazy {
        PhotoInsightsEngine(db.photos()).insights.stateIn(scope, SharingStarted.WhileSubscribed(60_000), null)
    }

    private val _locked = MutableStateFlow(false)

    /** With "lock with fingerprint" on: true until the user confirms it's them. */
    val locked: StateFlow<Boolean> = _locked.asStateFlow()
    private var backgroundedAt = 0L

    fun unlocked() {
        _locked.value = false
    }

    override fun onCreate() {
        super.onCreate()
        privacy = PrivacySettings(this)
        appearance = Appearance(this)
        ledger = Ledger(this)
        models = SharedModelStore.open(this, offline = { privacy.neverOnline.value })
        hub = ModelHub(this, models) { privacy.neverOnline.value }
        // Must be set before the first model loads; covers every model in the app.
        hub.applyStoredApiKey(BuildConfig.DESERT_ANT_API_KEY)
        db = AppDatabase.create(this)
        vault = Vault(this)
        mask = MaskSession(hub, ledger)
        Notifications.createChannels(this)
        _locked.value = privacy.lockApp.value

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                // Leaving the app closes the vault and forgets its decrypted thumbnails.
                vault.lock()
                backgroundedAt = System.currentTimeMillis()
            }

            override fun onStart(owner: LifecycleOwner) {
                if (privacy.lockApp.value && backgroundedAt > 0 && System.currentTimeMillis() - backgroundedAt > RELOCK_AFTER_MS) _locked.value = true
            }
        })
        scope.launch { Housekeeping.deleteOldAudio(this@ShowcaseApp) }
        scope.launch(Dispatchers.IO) {
            // Tell sibling apps Burrow is still installed, and forget apps not seen for half a year.
            runCatching { models.touchClaims(); models.pruneStaleClaims() }
            // Models from before the shared store: Desert Ant weights keep working
            // from the cache, speech packs move into the store when they match.
            hub.detectPreStoreModels()
            SpeechPacks.adoptPreStorePacks(this@ShowcaseApp)
            SpeechPacks.refresh(this@ShowcaseApp)
        }
    }

    private companion object {
        const val RELOCK_AFTER_MS = 30_000L
    }
}

val android.content.Context.app: ShowcaseApp get() = applicationContext as ShowcaseApp
