package com.admodule.next.genads.adsImp.manager

import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread-safe load bookkeeping + bounded retry policy shared by the full-screen ad managers.
 *
 * - [tryStart] atomically claims the single in-flight load slot, so concurrent triggers (network
 *   restore, foreground, retry, a button tap) can never fire two requests for the same unit.
 * - [onFailed] retries ONLY errors that can succeed later (no fill / network / timeout / internal),
 *   at most [maxRetries] times with exponential backoff, and never while the app is backgrounded.
 *   Endless retries on no-fill burn requests, lower the match rate and can get the app throttled.
 *   A deferred retry is picked up again by the foreground preload in [AdsManager].
 */
internal class AdLoadController(
    private val scope: CoroutineScope,
    private val isAppInForeground: () -> Boolean,
    private val tag: String,
    private val maxRetries: Int = DEFAULT_MAX_RETRIES
) {
    constructor(adsManager: AdsManager, tag: String, maxRetries: Int = DEFAULT_MAX_RETRIES) :
        this(adsManager.scope, { adsManager.isAppInForeground }, tag, maxRetries)

    private val loading = AtomicBoolean(false)
    private val retryAttempt = AtomicInteger(0)

    /** Claims the in-flight slot. Returns false when a load is already running. */
    fun tryStart(): Boolean = loading.compareAndSet(false, true)

    fun onLoaded() {
        loading.set(false)
        retryAttempt.set(0)
    }

    /** An explicit (non-retry) load request gets a fresh retry budget. */
    fun resetRetries() = retryAttempt.set(0)

    /** Ends the in-flight load without retrying (e.g. offline — the network-restore preload reloads). */
    fun onAborted() = loading.set(false)

    /**
     * Ends the in-flight load and schedules [reload] if the error is worth retrying.
     * @return the delay before the scheduled retry, or null when no retry was scheduled.
     */
    fun onFailed(code: LoadAdError.ErrorCode, reload: () -> Unit): Long? {
        loading.set(false)
        if (code !in RETRYABLE_ERRORS) {
            Log.w(tag, "Load error $code is not retryable; waiting for the next explicit load.")
            return null
        }
        val attempt = retryAttempt.incrementAndGet()
        if (attempt > maxRetries) {
            Log.w(tag, "Giving up after $maxRetries retries; waiting for the next explicit load.")
            return null
        }
        val delayMs = (BASE_DELAY_MS shl attempt).coerceAtMost(MAX_DELAY_MS)
        Log.d(tag, "Scheduling reload in $delayMs ms (retry #$attempt of $maxRetries)")
        scope.launch {
            delay(delayMs)
            if (!isAppInForeground()) {
                Log.d(tag, "App is in the background; deferring retry to the next foreground.")
                return@launch
            }
            reload()
        }
        return delayMs
    }

    companion object {
        const val DEFAULT_MAX_RETRIES = 4
        internal const val BASE_DELAY_MS = 1000L
        internal const val MAX_DELAY_MS = 64_000L

        internal val RETRYABLE_ERRORS = setOf(
            LoadAdError.ErrorCode.NO_FILL,
            LoadAdError.ErrorCode.NETWORK_ERROR,
            LoadAdError.ErrorCode.TIMEOUT,
            LoadAdError.ErrorCode.INTERNAL_ERROR
        )
    }
}
