package com.admodule.next.genads

import android.app.Application
import android.util.Log
import com.admodule.next.genads.adsImp.data.LiveNetworkMonitor
import com.admodule.next.genads.adsImp.manager.AdsManager
import com.admodule.next.genads.adsImp.manager.AppOpenManager
import com.admodule.next.genads.adsImp.manager.ConsentManager
import com.admodule.next.genads.adsImp.manager.InterstitialManager
import com.admodule.next.genads.adsImp.manager.RewardedManager
import com.admodule.next.genads.adsImp.manager.RewardedInterstitialManager

class BaseApplication : Application() {

    private val TAG = "BaseApplication"

    companion object {
        lateinit var instance: BaseApplication
            private set
    }

    // Programmatic Service Locator singletons — the managers consumed by the UI are exposed here.
    lateinit var adsManager: AdsManager
        private set
    lateinit var appOpenManager: AppOpenManager
        private set
    lateinit var interstitialManager: InterstitialManager
        private set
    lateinit var rewardedManager: RewardedManager
        private set
    lateinit var rewardedInterstitialManager: RewardedInterstitialManager
        private set
    lateinit var consentManager: ConsentManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "BaseApplication starting. Bootstrapping managers.")

        // 1. Instantiate Core Ad Components Programmatically
        val monitor = LiveNetworkMonitor(this)

        val manager = AdsManager(this, monitor)
        adsManager = manager

        // 2. Instantiate Full Screen Managers
        appOpenManager = AppOpenManager(manager)
        interstitialManager = InterstitialManager(manager)
        rewardedManager = RewardedManager(manager)
        rewardedInterstitialManager = RewardedInterstitialManager(manager)

        // 3. GDPR consent (UMP). Created here, but gathered from a foreground Activity (MainActivity).
        consentManager = ConsentManager(this)

        // 4. Configure automated sequential caches on successful initialization signals
        manager.setPreloaderCallback {
            Log.i(TAG, "GMA SDK ready callback signal received. Preloading full-screen ad units.")
            appOpenManager.loadAd()
            interstitialManager.loadAd()
            rewardedManager.loadAd()
            rewardedInterstitialManager.loadAd()
        }

        // NOTE: The Ads SDK is intentionally NOT initialized here. To stay GDPR-compliant we defer
        // AdsManager.initialize() until the UMP consent flow completes and canRequestAds is true —
        // see MainActivity.onCreate(). Initializing here would risk requesting ads before consent.
    }
}
