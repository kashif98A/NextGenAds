package com.admodule.next.genads

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.admodule.next.genads.adsImp.manager.AdEvent
import com.admodule.next.genads.adsImp.manager.AdFormat
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cache expiry against real ads: a cached ad older than expiryMs must be reported unavailable, must
 * NOT be shown, and must be replaced by a fresh load. expiryMs is shortened to seconds so the test
 * doesn't wait the real 1h / 4h.
 */
@RunWith(AndroidJUnit4::class)
class ExpiryOnDeviceTest {

    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before fun setUp() { scenario = launchInitialized() }
    @After fun tearDown() { scenario.close() }

    @Test
    fun defaultExpiries_matchAdMobGuidance() {
        val hour = 3600 * 1000L
        assertEquals(4 * hour, app.appOpenManager.expiryMs)
        assertEquals(hour, app.interstitialManager.expiryMs)
        assertEquals(hour, app.rewardedManager.expiryMs)
        assertEquals(hour, app.rewardedInterstitialManager.expiryMs)
        assertEquals(hour, app.adsManager.nativeAds().expiryMs)
    }

    private fun checkFullScreenExpiry(
        format: AdFormat,
        defaultExpiry: Long,
        setExpiry: (Long) -> Unit,
        isAvailable: () -> Boolean,
        load: () -> Unit,
        show: (Activity, onDismiss: () -> Unit) -> Unit
    ) {
        val recorder = EventRecorder { it.format == format }
        try {
            load()
            waitUntil(40_000, "$format: initial ad loaded") { isAvailable() }

            setExpiry(3_000)
            Thread.sleep(3_500)
            assertFalse("$format: a 3.5s-old ad must be expired with expiryMs=3s", isAvailable())

            recorder.attach()
            var dismissedImmediately = false
            scenario.onActivity { activity -> show(activity) { dismissedImmediately = true } }
            assertTrue("$format: showAd() on an expired ad must not show it (onDismiss right away)", dismissedImmediately)

            recorder.waitFor(40_000, "$format: fresh ad loaded") { events -> events.any { it is AdEvent.Loaded } }
            val events = recorder.events
            println("$format expiry events: $events")
            assertEquals("$format: the stale ad is reported once", 1, recorder.ofType<AdEvent.Expired>().size)
            assertTrue("$format: a new request was made", recorder.ofType<AdEvent.LoadRequested>().isNotEmpty())
            assertTrue("$format: the stale ad was never shown", recorder.ofType<AdEvent.Impression>().isEmpty())
            assertTrue("$format: the replacement ad is fresh", isAvailable())
        } finally {
            recorder.detach()
            setExpiry(defaultExpiry)
        }
    }

    @Test
    fun interstitial_expiredAdIsRefusedAndReloaded() = with(app.interstitialManager) {
        checkFullScreenExpiry(AdFormat.INTERSTITIAL, 3600_000L, { expiryMs = it }, ::isAdAvailable, ::loadAd) { a, d ->
            showAd(a, d)
        }
    }

    @Test
    fun rewarded_expiredAdIsRefusedAndReloaded() = with(app.rewardedManager) {
        checkFullScreenExpiry(AdFormat.REWARDED, 3600_000L, { expiryMs = it }, ::isAdAvailable, ::loadAd) { a, d ->
            showAd(a, onUserEarnedReward = { _, _ -> }, onDismiss = d)
        }
    }

    @Test
    fun rewardedInterstitial_expiredAdIsRefusedAndReloaded() = with(app.rewardedInterstitialManager) {
        checkFullScreenExpiry(AdFormat.REWARDED_INTERSTITIAL, 3600_000L, { expiryMs = it }, ::isAdAvailable, ::loadAd) { a, d ->
            showAd(a, onUserEarnedReward = { _, _ -> }, onDismiss = d)
        }
    }

    @Test
    fun appOpen_expiredAdIsRefusedAndReloaded() = with(app.appOpenManager) {
        checkFullScreenExpiry(AdFormat.APP_OPEN, 4 * 3600_000L, { expiryMs = it }, ::isAdAvailable, ::loadAd) { a, d ->
            showAd(a, d)
        }
    }

    @Test
    fun nativePool_dropsExpiredAdsAndServesAFreshOne() {
        val pool = app.adsManager.nativeAds()
        val recorder = EventRecorder { it.format == AdFormat.NATIVE }.attach()
        try {
            // Make sure the pool holds ads, then let every one of them go stale.
            runBlocking { withTimeout(40_000) { pool.acquire() } }?.destroy()
            pool.preload()
            Thread.sleep(8_000) // let the refill finish

            pool.expiryMs = 2_000
            Thread.sleep(2_500)

            val before = recorder.events.size
            val ad = runBlocking { withTimeout(40_000) { pool.acquire() } }
            val during = recorder.events.drop(before)
            println("native expiry events: $during")
            assertNotNull("acquire() must still deliver an ad", ad)
            assertTrue("stale pooled ads are reported as expired: $during", during.any { it is AdEvent.Expired })
            // All pooled ads were expired, so the one returned must come from a NEW load.
            assertTrue("a fresh native load was required: $during", during.any { it is AdEvent.Loaded })
            ad?.destroy()
        } finally {
            recorder.detach()
            pool.expiryMs = 3600 * 1000L
        }
    }
}
