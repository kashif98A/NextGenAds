package com.example.adsImp

/**
 * AdMob sample/test ad unit IDs, inlined directly into the app.
 *
 * There is no remote config layer — these constants are the single source of truth for
 * which ad units the app loads. Replace them with your production ad unit IDs before release.
 */
object AdUnitIds {
    const val APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
    const val BANNER = "ca-app-pub-3940256099942544/6300978111"
    // No dedicated collapsible test unit exists. Google's docs test collapsible banners with the
    // anchored adaptive banner test ID + the "collapsible" request extra (added in buildBannerAdRequest).
    const val COLLAPSIBLE_BANNER = "ca-app-pub-3940256099942544/9214589741"
    const val NATIVE = "ca-app-pub-3940256099942544/2247696110"
    const val INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED = "ca-app-pub-3940256099942544/5224354917"
    const val REWARDED_INTERSTITIAL = "ca-app-pub-3940256099942544/5354046379"
}
