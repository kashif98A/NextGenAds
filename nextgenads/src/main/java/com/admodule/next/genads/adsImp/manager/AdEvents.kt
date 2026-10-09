package com.admodule.next.genads.adsImp.manager

import com.google.android.libraries.ads.mobile.sdk.common.AdValue
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class AdFormat { APP_OPEN, INTERSTITIAL, REWARDED, REWARDED_INTERSTITIAL, BANNER, NATIVE }

/**
 * One step in an ad's lifecycle, delivered to every [AdEventListener] registered with
 * [AdsManager.addEventListener]. Covers every format, so you can forward them to analytics and
 * measure match rate (loaded ÷ requested) and show rate (impressions ÷ loaded) per placement.
 */
sealed interface AdEvent {
    val format: AdFormat
    val adUnitId: String

    /** An ad request was sent to the SDK (includes retries and banner auto-refreshes). */
    data class LoadRequested(override val format: AdFormat, override val adUnitId: String) : AdEvent

    /** The request returned an ad. [latencyMs] is the time since its [LoadRequested] (-1 for a banner auto-refresh). */
    data class Loaded(
        override val format: AdFormat,
        override val adUnitId: String,
        val latencyMs: Long
    ) : AdEvent

    /**
     * The request failed. [retryInMs] is when the library will retry automatically, or null when it
     * won't (permanent error, retry budget used up, or a format that doesn't auto-retry).
     */
    data class LoadFailed(
        override val format: AdFormat,
        override val adUnitId: String,
        val code: LoadAdError.ErrorCode,
        val message: String,
        val retryInMs: Long?
    ) : AdEvent {
        val willRetry: Boolean get() = retryInMs != null
    }

    /** A loaded ad passed its expiry without being shown and was discarded — a lost impression. */
    data class Expired(override val format: AdFormat, override val adUnitId: String) : AdEvent

    /** The SDK recorded an impression. */
    data class Impression(override val format: AdFormat, override val adUnitId: String) : AdEvent

    data class Clicked(override val format: AdFormat, override val adUnitId: String) : AdEvent

    /** Estimated or precise revenue for an impression (see Google's "impression-level ad revenue"). */
    data class Paid(
        override val format: AdFormat,
        override val adUnitId: String,
        val valueMicros: Long,
        val currencyCode: String,
        val precision: String
    ) : AdEvent

    /** A full-screen ad was ready but couldn't be displayed. */
    data class ShowFailed(
        override val format: AdFormat,
        override val adUnitId: String,
        val message: String
    ) : AdEvent

    /** A full-screen ad was closed by the user. */
    data class Dismissed(override val format: AdFormat, override val adUnitId: String) : AdEvent
}

/**
 * Receives [AdEvent]s. May be called on ANY thread (SDK callbacks arrive on worker threads), so keep
 * it fast and thread-safe — e.g. hand the event to your analytics SDK. Exceptions thrown here are
 * caught and logged so a faulty listener can never break ad delivery.
 */
fun interface AdEventListener {
    fun onAdEvent(event: AdEvent)
}

internal fun AdValue.toPaid(format: AdFormat, adUnitId: String) =
    AdEvent.Paid(format, adUnitId, valueMicros, currencyCode, precisionType.name)

/**
 * Ready-made [AdEventListener] that counts events per format and computes the two numbers that
 * matter for ad revenue:
 *  - **match rate** = loaded ÷ requested  (how often a request returns an ad)
 *  - **show rate**  = impressions ÷ loaded (how often a loaded ad is actually seen)
 *
 *     val stats = AdStats().also(adsManager::addEventListener)
 *     stats.snapshot(AdFormat.INTERSTITIAL).showRate
 */
class AdStats : AdEventListener {

    /** Immutable counts for one format (or the total across formats). */
    data class Counts(
        val requests: Int,
        val loaded: Int,
        val failed: Int,
        val expired: Int,
        val impressions: Int,
        val clicks: Int,
        val revenueMicros: Long
    ) {
        /** Loaded ÷ requested, or null before the first request. */
        val matchRate: Double? get() = if (requests == 0) null else loaded.toDouble() / requests

        /** Impressions ÷ loaded, or null before the first load. */
        val showRate: Double? get() = if (loaded == 0) null else impressions.toDouble() / loaded
    }

    private class Counters {
        val requests = AtomicInteger()
        val loaded = AtomicInteger()
        val failed = AtomicInteger()
        val expired = AtomicInteger()
        val impressions = AtomicInteger()
        val clicks = AtomicInteger()
        val revenueMicros = AtomicLong()

        fun snapshot() = Counts(
            requests.get(), loaded.get(), failed.get(), expired.get(),
            impressions.get(), clicks.get(), revenueMicros.get()
        )
    }

    private val byFormat = ConcurrentHashMap<AdFormat, Counters>()

    override fun onAdEvent(event: AdEvent) {
        val c = byFormat.computeIfAbsent(event.format) { Counters() }
        when (event) {
            is AdEvent.LoadRequested -> c.requests.incrementAndGet()
            is AdEvent.Loaded -> c.loaded.incrementAndGet()
            is AdEvent.LoadFailed -> c.failed.incrementAndGet()
            is AdEvent.Expired -> c.expired.incrementAndGet()
            is AdEvent.Impression -> c.impressions.incrementAndGet()
            is AdEvent.Clicked -> c.clicks.incrementAndGet()
            is AdEvent.Paid -> c.revenueMicros.addAndGet(event.valueMicros)
            is AdEvent.ShowFailed, is AdEvent.Dismissed -> Unit
        }
    }

    fun snapshot(format: AdFormat): Counts = (byFormat[format] ?: Counters()).snapshot()

    /** Sum across all formats. */
    fun total(): Counts = AdFormat.entries.map(::snapshot).reduce { a, b ->
        Counts(
            a.requests + b.requests, a.loaded + b.loaded, a.failed + b.failed, a.expired + b.expired,
            a.impressions + b.impressions, a.clicks + b.clicks, a.revenueMicros + b.revenueMicros
        )
    }

    fun reset() = byFormat.clear()
}
