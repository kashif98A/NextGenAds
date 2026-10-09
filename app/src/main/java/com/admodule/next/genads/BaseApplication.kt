package com.admodule.next.genads

import android.app.Application
import android.util.Log
import com.admodule.next.genads.adsImp.data.LiveNetworkMonitor
import com.admodule.next.genads.adsImp.manager.AdStats
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

    /** Live match rate / show rate per format, fed by the library's ad events. */
    val adStats = AdStats()

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "BaseApplication starting. Bootstrapping managers.")

        // 1. Instantiate Core Ad Components Programmatically
        val monitor = LiveNetworkMonitor(this)

        val manager = AdsManager(this, monitor)
        adsManager = manager

        // Ad events: count them for the dashboard's metrics card, and log them. In a real app,
        // forward them to your analytics SDK here instead (e.g. Firebase logEvent).
        manager.addEventListener(adStats)
        manager.addEventListener { event -> Log.d("AdEvents", event.toString()) }

        // 2. Instantiate Full Screen Managers
        appOpenManager = AppOpenManager(manager)
        interstitialManager = InterstitialManager(manager)
        rewardedManager = RewardedManager(manager)
        rewardedInterstitialManager = RewardedInterstitialManager(manager)

        // 3. GDPR consent (UMP). Created here, but gathered from a foreground Activity (MainActivity).
        consentManager = ConsentManager(this)

        // 4. Preload ONLY the formats a typical session will actually show: App Open (every resume),
        //    the interstitial and the native pool. Rewarded / rewarded-interstitial are loaded when the
        //    screen that offers them opens (see MainActivity.DashboardScreen) — a cached ad that
        //    expires unseen is a matched request with no impression, which drags the show rate down.
        //    This callback also runs when the network returns and whenever the app comes back to the
        //    foreground; each loadAd()/preload() is a no-op while its cache is still fresh.
        manager.setPreloaderCallback {
            Log.i(TAG, "GMA SDK ready callback signal received. Preloading likely-to-show ad units.")
            appOpenManager.loadAd()
            interstitialManager.loadAd()
            manager.nativeAds().preload()
        }

        // NOTE: The Ads SDK is intentionally NOT initialized here. To stay GDPR-compliant we defer
        // AdsManager.initialize() until the UMP consent flow completes and canRequestAds is true —
        // see MainActivity.onCreate(). Initializing here would risk requesting ads before consent.
    }
}
