package com.admodule.next.genads

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.admodule.next.genads.adsImp.AdUnitIds
import com.admodule.next.genads.adsImp.manager.AdEvent
import com.admodule.next.genads.adsImp.manager.AdFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Retry limits against the REAL Next-Gen SDK. Google's test units always fill, so each test points
 * the interstitial manager at a unit the SDK will reject, then checks the library's own events:
 * retryable errors → exactly 4 retries (2s/4s/8s/16s) then stop; permanent errors → no retry.
 */
@RunWith(AndroidJUnit4::class)
class RetryOnDeviceTest {

    private lateinit var scenario: ActivityScenario<MainActivity>
    private val manager get() = app.interstitialManager

    // The manager allows one in-flight load per unit, so a test must start from a settled state —
    // otherwise its loadAd() is (correctly) rejected while an earlier load is still running.
    @Before fun setUp() {
        scenario = launchInitialized()
        manager.loadAd()
        waitUntil(40_000, "a real interstitial cached (no load in flight)") { manager.isAdAvailable() }
    }

    @After fun tearDown() {
        manager.adUnitId = AdUnitIds.INTERSTITIAL
        manager.expiryMs = 3600 * 1000L
        manager.loadAd()
        waitUntil(40_000, "interstitial restored") { manager.isAdAvailable() }
        scenario.close()
    }

    private fun assertRetryPolicy(badUnit: String) {
        val recorder = EventRecorder { it.format == AdFormat.INTERSTITIAL && it.adUnitId == badUnit }.attach()
        try {
            manager.adUnitId = badUnit
            manager.expiryMs = 1 // treat the cached interstitial as stale so loadAd() really requests
            Thread.sleep(50)
            manager.loadAd()

            // Settles when a failure says "no retry" (permanent error, or budget used up).
            recorder.waitFor(60_000, "the manager to stop retrying") { events ->
                events.filterIsInstance<AdEvent.LoadFailed>().any { !it.willRetry }
            }
            Thread.sleep(20_000) // nothing more may be requested after it stops

            val requests = recorder.ofType<AdEvent.LoadRequested>().size
            val failures = recorder.ofType<AdEvent.LoadFailed>()
            val summary = "code=${failures.first().code} requests=$requests " +
                "retryDelays=${failures.map { it.retryInMs }}"
            println(summary)

            if (failures.first().willRetry) {
                assertEquals(summary, 5, requests) // 1 initial + 4 retries
                assertEquals(summary, listOf(2000L, 4000L, 8000L, 16000L, null), failures.map { it.retryInMs })
            } else {
                assertEquals(summary, 1, requests)
                assertEquals(summary, listOf<Long?>(null), failures.map { it.retryInMs })
            }
            assertTrue(summary, recorder.ofType<AdEvent.Loaded>().isEmpty())
        } finally {
            recorder.detach()
        }
    }

    @Test
    fun formatMismatchUnit_followsRetryPolicy() = assertRetryPolicy(AdUnitIds.BANNER) // banner unit as interstitial

    @Test
    fun nonexistentUnit_followsRetryPolicy() = assertRetryPolicy("ca-app-pub-3940256099942544/0000000000")
}
