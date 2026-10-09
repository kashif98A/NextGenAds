package com.admodule.next.genads.adsImp.manager

import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError.ErrorCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Retry policy of [AdLoadController], on virtual time: which errors retry, how many times, with
 * which delays, and that nothing retries while the app is backgrounded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AdLoadControllerTest {

    /** Simulates a unit that fails with [code] on every attempt; returns the virtual time of each reload. */
    private fun TestScope.failEveryAttempt(
        code: ErrorCode,
        foreground: () -> Boolean = { true }
    ): List<Long> {
        val reloadTimes = mutableListOf<Long>()
        val controller = AdLoadController(this, foreground, "test")
        lateinit var attempt: () -> Unit
        attempt = {
            assertTrue("a retry must be able to claim the load slot", controller.tryStart())
            controller.onFailed(code) {
                reloadTimes += currentTime
                attempt()
            }
        }
        attempt()
        advanceUntilIdle()
        return reloadTimes
    }

    @Test
    fun noFill_retriesFourTimesWithExponentialBackoff_thenGivesUp() = runTest {
        val reloadTimes = failEveryAttempt(ErrorCode.NO_FILL)
        // Delays 2s, 4s, 8s, 16s → cumulative virtual time of each reload.
        assertEquals(listOf(2_000L, 6_000L, 14_000L, 30_000L), reloadTimes)
    }

    @Test
    fun onFailed_returnsTheScheduledRetryDelay_orNullWhenNotRetrying() = runTest {
        val controller = AdLoadController(this, { true }, "test")
        val returned = mutableListOf<Long?>()
        repeat(AdLoadController.DEFAULT_MAX_RETRIES + 1) {
            controller.tryStart()
            returned += controller.onFailed(ErrorCode.NO_FILL) {}
            advanceUntilIdle()
        }
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, null), returned)

        controller.tryStart()
        assertEquals(null, controller.onFailed(ErrorCode.INVALID_REQUEST) {})
    }

    @Test
    fun everyRetryableError_isRetriedExactlyMaxTimes() = runTest {
        for (code in listOf(ErrorCode.NO_FILL, ErrorCode.NETWORK_ERROR, ErrorCode.TIMEOUT, ErrorCode.INTERNAL_ERROR)) {
            assertEquals("$code", AdLoadController.DEFAULT_MAX_RETRIES, failEveryAttempt(code).size)
        }
    }

    @Test
    fun nonRetryableErrors_areNeverRetried() = runTest {
        val nonRetryable = ErrorCode.values().filterNot { it in AdLoadController.RETRYABLE_ERRORS }
        assertTrue(nonRetryable.containsAll(listOf(ErrorCode.INVALID_REQUEST, ErrorCode.APP_ID_MISSING, ErrorCode.NOT_FOUND)))
        for (code in nonRetryable) {
            assertEquals("$code must not retry", emptyList<Long>(), failEveryAttempt(code))
        }
    }

    @Test
    fun backgroundedApp_defersRetry() = runTest {
        assertEquals(emptyList<Long>(), failEveryAttempt(ErrorCode.NO_FILL, foreground = { false }))
    }

    @Test
    fun successfulLoad_resetsRetryBudget() = runTest {
        val controller = AdLoadController(this, { true }, "test")
        var reloads = 0
        repeat(3) {
            controller.tryStart()
            controller.onFailed(ErrorCode.NO_FILL) { reloads++ }
            advanceUntilIdle()
        }
        assertEquals(3, reloads)

        controller.tryStart()
        controller.onLoaded()

        // Fresh budget: the next failure waits the FIRST backoff step again (2s), not the 4th (16s).
        val start = currentTime
        var reloadedAt = -1L
        controller.tryStart()
        controller.onFailed(ErrorCode.NO_FILL) { reloadedAt = currentTime }
        advanceUntilIdle()
        assertEquals(2_000L, reloadedAt - start)
    }

    @Test
    fun resetRetries_afterGivingUp_allowsRetryingAgain() = runTest {
        val controller = AdLoadController(this, { true }, "test")
        var reloads = 0
        repeat(AdLoadController.DEFAULT_MAX_RETRIES + 1) {
            controller.tryStart()
            controller.onFailed(ErrorCode.NO_FILL) { reloads++ }
            advanceUntilIdle()
        }
        assertEquals(AdLoadController.DEFAULT_MAX_RETRIES, reloads)

        controller.resetRetries() // an explicit loadAd()
        controller.tryStart()
        controller.onFailed(ErrorCode.NO_FILL) { reloads++ }
        advanceUntilIdle()
        assertEquals(AdLoadController.DEFAULT_MAX_RETRIES + 1, reloads)
    }

    @Test
    fun loadSlot_isExclusive_andReleasedByFailureOrAbort() = runTest {
        val controller = AdLoadController(this, { true }, "test")
        assertTrue(controller.tryStart())
        assertFalse("second concurrent load must be rejected", controller.tryStart())

        controller.onAborted()
        assertTrue(controller.tryStart())

        controller.onFailed(ErrorCode.INVALID_REQUEST) {}
        assertTrue(controller.tryStart())
    }

    @Test
    fun loadSlot_underRealThreadContention_admitsExactlyOne() = runTest {
        val controller = AdLoadController(this, { true }, "test")
        val threads = 64
        val pool = Executors.newFixedThreadPool(threads)
        val ready = CountDownLatch(threads)
        val go = CountDownLatch(1)
        val winners = AtomicInteger(0)
        repeat(threads) {
            pool.execute {
                ready.countDown()
                go.await()
                if (controller.tryStart()) winners.incrementAndGet()
            }
        }
        ready.await()
        go.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        assertEquals(1, winners.get())
    }
}
