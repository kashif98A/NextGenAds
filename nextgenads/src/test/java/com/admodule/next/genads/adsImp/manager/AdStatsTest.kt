package com.admodule.next.genads.adsImp.manager

import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError.ErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdStatsTest {

    private val unit = "ca-app-pub-test/1"

    @Test
    fun matchAndShowRate_areComputedPerFormat() {
        val stats = AdStats()
        repeat(4) { stats.onAdEvent(AdEvent.LoadRequested(AdFormat.INTERSTITIAL, unit)) }
        repeat(3) { stats.onAdEvent(AdEvent.Loaded(AdFormat.INTERSTITIAL, unit, latencyMs = 100)) }
        stats.onAdEvent(AdEvent.LoadFailed(AdFormat.INTERSTITIAL, unit, ErrorCode.NO_FILL, "no fill", retryInMs = 2000))
        repeat(2) { stats.onAdEvent(AdEvent.Impression(AdFormat.INTERSTITIAL, unit)) }
        stats.onAdEvent(AdEvent.Expired(AdFormat.INTERSTITIAL, unit))
        stats.onAdEvent(AdEvent.Paid(AdFormat.INTERSTITIAL, unit, 1_500, "USD", "PRECISE"))
        stats.onAdEvent(AdEvent.Paid(AdFormat.INTERSTITIAL, unit, 500, "USD", "ESTIMATED"))

        val c = stats.snapshot(AdFormat.INTERSTITIAL)
        assertEquals(4, c.requests)
        assertEquals(3, c.loaded)
        assertEquals(1, c.failed)
        assertEquals(1, c.expired)
        assertEquals(2, c.impressions)
        assertEquals(2_000L, c.revenueMicros)
        assertEquals(0.75, c.matchRate!!, 1e-9)
        assertEquals(2.0 / 3.0, c.showRate!!, 1e-9)

        // Other formats are untouched.
        assertEquals(0, stats.snapshot(AdFormat.BANNER).requests)
    }

    @Test
    fun rates_areNullBeforeAnyData() {
        val c = AdStats().snapshot(AdFormat.NATIVE)
        assertNull(c.matchRate)
        assertNull(c.showRate)
    }

    @Test
    fun total_sumsAcrossFormats_andResetClears() {
        val stats = AdStats()
        stats.onAdEvent(AdEvent.LoadRequested(AdFormat.BANNER, unit))
        stats.onAdEvent(AdEvent.LoadRequested(AdFormat.NATIVE, unit))
        stats.onAdEvent(AdEvent.Loaded(AdFormat.NATIVE, unit, 50))
        assertEquals(2, stats.total().requests)
        assertEquals(1, stats.total().loaded)

        stats.reset()
        assertEquals(0, stats.total().requests)
    }

    @Test
    fun loadFailed_willRetry_reflectsRetryDelay() {
        assertEquals(true, AdEvent.LoadFailed(AdFormat.REWARDED, unit, ErrorCode.TIMEOUT, "", 4000).willRetry)
        assertEquals(false, AdEvent.LoadFailed(AdFormat.REWARDED, unit, ErrorCode.INVALID_REQUEST, "", null).willRetry)
    }
}
