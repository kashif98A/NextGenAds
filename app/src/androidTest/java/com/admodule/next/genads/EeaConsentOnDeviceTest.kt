package com.admodule.next.genads

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.graphics.Rect
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.ump.FormError
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * The real UMP GDPR form, forced via the EEA debug geography: it must appear for a user with no
 * stored decision, block ads until answered, and the privacy-options entry point must re-open it.
 */
@RunWith(AndroidJUnit4::class)
class EeaConsentOnDeviceTest {

    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var automation: UiAutomation
    private val consent get() = app.consentManager

    @Before fun setUp() {
        // The UMP form is a WebView in its own dialog window. Its buttons are only exposed as
        // accessibility nodes when "not important" views + interactive windows are requested, so
        // the test drives ONE explicitly configured UiAutomation connection (UiDevice would open
        // its own connection without these flags and never see the buttons).
        automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        // MainActivity runs its own (non-EEA) consent flow on launch. Wait for it to finish so it
        // can't race with the EEA flow this test starts (the SDK may already be initialized from a
        // previous test, so "initialized" alone doesn't prove the launch flow is done).
        val launch = Logcat.mark()
        scenario = launchInitialized()
        Logcat.waitFor(launch, "ConsentManager", 30_000) { lines ->
            lines.any { "Consent resolved" in it || "dismissed with error" in it || "update failed" in it }
        }
        consent.reset()
        consent.forceEeaDebugGeography = true
        // A phone registered by an earlier test in this run (UMP logs its id only once).
        registeredTestDeviceId?.let { consent.eeaDebugTestDeviceIds = listOf(it) }
    }

    @After fun tearDown() {
        // Leave the device as a normal (non-EEA) user with no stored decision.
        consent.forceEeaDebugGeography = false
        consent.eeaDebugTestDeviceIds = emptyList()
        consent.reset()
        scenario.close()
    }

    /** Starts gatherConsent and returns a latch + the error slot filled when it resolves. */
    private fun startGather(): Pair<CountDownLatch, AtomicReference<FormError?>> {
        val done = CountDownLatch(1)
        val error = AtomicReference<FormError?>(null)
        scenario.onActivity { activity ->
            consent.gatherConsent(activity) { e -> error.set(e); done.countDown() }
        }
        return done to error
    }

    /**
     * Shows the EEA form, retrying from a clean state up to 3 times. Emulators are UMP test devices
     * automatically; a physical phone must be registered by its hashed id, which UMP logs (read it
     * from Logcat). On some phones a concurrent consent request can briefly override the forced
     * geography, hence the retries — the test still fails if the form never appears.
     */
    private fun openEeaForm(): Pair<CountDownLatch, AtomicReference<FormError?>> {
        repeat(3) { attempt ->
            consent.reset()
            val (done, error) = startGather()
            if (waitForFormOrResolution(done)) return done to error

            // Resolved without a form: this device isn't registered as a UMP test device yet.
            if (registeredTestDeviceId == null) {
                registeredTestDeviceId = Logcat.since("", "UserMessagingPlatform")
                    .firstNotNullOfOrNull { Regex("addTestDeviceHashedId\\(\"([0-9A-F-]+)\"\\)").find(it)?.groupValues?.get(1) }
                    ?.also { println("Registering UMP test device id: $it") }
            }
            registeredTestDeviceId?.let { consent.eeaDebugTestDeviceIds = listOf(it) }
            println("EEA form not shown on attempt ${attempt + 1}; retrying (resolved=${done.count == 0L})")
        }
        throw AssertionError(
            "EEA consent form did not appear after 3 attempts (registered id=$registeredTestDeviceId, " +
                "buttons=${visibleButtons()})"
        )
    }

    /** True once the form's "Consent" button is on screen; false if the flow resolved without a form. */
    private fun waitForFormOrResolution(done: CountDownLatch, timeoutMs: Long = 45_000): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (findNode("Consent") != null) return true
            if (done.count == 0L) return false
            SystemClock.sleep(500)
        }
        return false
    }

    /**
     * Finds an on-screen node (in any window, incl. the form's dialog) labelled exactly [text].
     * The UMP form's buttons are Chromium WebView nodes whose label is in contentDescription (text is
     * empty), which findAccessibilityNodeInfosByText / By.text don't match — so walk the tree.
     */
    private fun findNode(label: String): AccessibilityNodeInfo? {
        fun AccessibilityNodeInfo.label(): String? =
            this.text?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: this.contentDescription?.toString()?.trim()

        fun search(node: AccessibilityNodeInfo?, depth: Int): AccessibilityNodeInfo? {
            if (node == null || depth > 50) return null
            if (node.isVisibleToUser && node.className?.endsWith("Button") == true && node.label() == label) return node
            for (i in 0 until node.childCount) search(node.getChild(i), depth + 1)?.let { return it }
            return null
        }
        // Only app windows (the activity + the form dialog) — never the status bar / system UI.
        return automation.windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .firstNotNullOfOrNull { search(it.root, 0) }
    }

    private fun waitForButton(text: String, timeoutMs: Long = 45_000): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (findNode(text) != null) return true
            SystemClock.sleep(500)
        }
        return false
    }

    private fun hasButton(text: String) = findNode(text) != null

    private fun swipeUp() {
        val (w, h) = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics
            .let { it.widthPixels to it.heightPixels }
        ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand("input swipe ${w / 2} ${h * 3 / 4} ${w / 2} ${h / 4} 300")
        ).use { it.readBytes() }
    }

    /** Labels of all visible buttons in app windows — for failure messages. */
    private fun visibleButtons(): List<String> {
        val out = mutableListOf<String>()
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 50) return
            if (n.isVisibleToUser && n.className?.endsWith("Button") == true) {
                out += (n.text?.toString()?.takeIf { it.isNotBlank() } ?: n.contentDescription?.toString() ?: "?")
            }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        automation.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.forEach { walk(it.root, 0) }
        return out
    }

    /** Taps the centre of the button with a real touch, as a user would. */
    private fun tap(text: String) {
        val node = findNode(text)
        assertNotNull("button '$text' not on screen", node)
        val bounds = Rect().also { node!!.getBoundsInScreen(it) }
        // Read the command's output to EOF: closing the stream early can cancel the tap.
        ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand("input tap ${bounds.centerX()} ${bounds.centerY()}")
        ).use { it.readBytes() }
    }

    @Test
    fun eeaUser_seesForm_noAdsUntilAnswered_consentEnablesAds() {
        val (done, error) = openEeaForm()
        assertFalse("ads must stay blocked while the form is unanswered", consent.canRequestAds)
        assertTrue("form offers 'Do not consent'", hasButton("Do not consent"))
        assertTrue("form offers 'Manage options'", hasButton("Manage options"))

        tap("Consent")
        assertTrue("consent flow must resolve after answering", done.await(20, TimeUnit.SECONDS))
        assertNull("no form error", error.get())
        assertTrue("consent given → ads allowed", consent.canRequestAds)
        assertTrue("EEA users must get a privacy-options entry point", consent.isPrivacyOptionsRequired)
    }

    @Test
    fun eeaUser_doNotConsent_resolvesWithoutError() {
        val (done, error) = openEeaForm()
        tap("Do not consent")
        assertTrue(done.await(20, TimeUnit.SECONDS))
        assertNull("no form error", error.get())
        println("After 'Do not consent': canRequestAds=${consent.canRequestAds} privacyOptionsRequired=${consent.isPrivacyOptionsRequired}")
        assertTrue("EEA users must get a privacy-options entry point", consent.isPrivacyOptionsRequired)
    }

    @Test
    fun privacyOptionsForm_reopensAndCanChangeTheDecision() {
        val (done, _) = openEeaForm()
        tap("Consent")
        assertTrue(done.await(20, TimeUnit.SECONDS))

        val dismissed = CountDownLatch(1)
        val error = AtomicReference<FormError?>(null)
        scenario.onActivity { activity ->
            consent.showPrivacyOptionsForm(activity) { e -> error.set(e); dismissed.countDown() }
        }
        // The re-opened form always shows "Manage options"; depending on layout, the decision
        // buttons may be below the fold, so scroll until "Do not consent" is reachable.
        val reopened = waitForButton("Manage options")
        assertTrue(
            "privacy options form must re-open the consent form " +
                "(callbackFired=${dismissed.count == 0L} error=${error.get()?.message} buttons=${visibleButtons()})",
            reopened
        )
        repeat(6) {
            if (hasButton("Do not consent")) return@repeat
            swipeUp()
            SystemClock.sleep(600)
        }
        assertTrue("'Do not consent' reachable in the re-opened form (buttons=${visibleButtons()})", hasButton("Do not consent"))
        tap("Do not consent")
        assertTrue(dismissed.await(20, TimeUnit.SECONDS))
        assertNull("no form error", error.get())
    }

    private companion object {
        /** UMP hashed id of a physical test phone, discovered once per test run. */
        var registeredTestDeviceId: String? = null
    }
}
