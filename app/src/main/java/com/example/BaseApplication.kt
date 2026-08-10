package com.example

import android.app.Application
import android.util.Log
import com.example.adsImp.data.LiveNetworkMonitor
import com.example.adsImp.manager.AdsManager
import com.example.adsImp.manager.AppOpenManager
import com.example.adsImp.manager.InterstitialManager
import com.example.adsImp.manager.RewardedManager
import com.example.adsImp.manager.RewardedInterstitialManager

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

        // 3. Configure automated sequential caches on successful initialization signals
        manager.setPreloaderCallback {
            Log.i(TAG, "GMA SDK ready callback signal received. Preloading full-screen ad units.")
            appOpenManager.loadAd()
            interstitialManager.loadAd()
            rewardedManager.loadAd()
            rewardedInterstitialManager.loadAd()
        }

        // 4. Initialize Ads and fetch Remote Config presets 
        manager.initialize {
            Log.d(TAG, "AdsManager has finished initialization phase successfully.")
        }
    }
}
