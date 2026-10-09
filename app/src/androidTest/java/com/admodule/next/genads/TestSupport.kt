package com.admodule.next.genads

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import android.util.Log
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import com.admodule.next.genads.adsImp.manager.AdEvent
import com.admodule.next.genads.adsImp.manager.AdEventListener
import java.util.UUID

/** Reads this app's own Logcat so tests can assert on what the ad managers actually did. */
object Logcat {
    private const val TAG = "NextGenAdsTest"

    private var bufferEnlarged = false

    /**
     * The ads SDK writes very large debug lines; on phones with a small log buffer they push the
     * managers' lines (and our markers) out before a test reads them. Enlarge it once per run.
     */
    fun ensureLargeBuffer() {
        if (bufferEnlarged) return
        shell("logcat -G 16M")
        bufferEnlarged = true
    }

    /** Writes a unique marker; [since] only returns lines logged after it. */
    fun mark(): String = "MARK-${UUID.randomUUID()}".also { Log.i(TAG, it) }

    fun since(marker: String, tag: String): List<String> {
        val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "brief"))
        val lines = process.inputStream.bufferedReader().readLines()
        val start = if (marker.isEmpty()) -1 else lines.indexOfLast { marker in it } // "" = whole log
        val tagPattern = Regex("^[VDIWEF]/${Regex.escape(tag)}\\s*\\(")
        return lines.drop(start + 1).filter { tagPattern.containsMatchIn(it) }
    }

    /** Polls until [done] holds for the lines logged under [tag] since [marker]. */
    fun waitFor(marker: String, tag: String, timeoutMs: Long, done: (List<String>) -> Boolean): List<String> {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            val lines = since(marker, tag)
            if (done(lines) || SystemClock.elapsedRealtime() > deadline) return lines
            SystemClock.sleep(250)
        }
    }
}

fun waitUntil(timeoutMs: Long, what: String, condition: () -> Boolean) {
    val deadline = SystemClock.elapsedRealtime() + timeoutMs
    while (!condition()) {
        check(SystemClock.elapsedRealtime() < deadline) { "Timed out after ${timeoutMs}ms waiting for: $what" }
        SystemClock.sleep(100)
    }
}

val app: BaseApplication get() = BaseApplication.instance

/** Runs a shell command with the instrumentation's shell privileges and waits for it to finish. */
fun shell(command: String) {
    ParcelFileDescriptor.AutoCloseInputStream(
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
    ).use { it.readBytes() }
}

/** Launches the demo's MainActivity (which runs consent + SDK init) and waits for the SDK. */
fun launchInitialized(): ActivityScenario<MainActivity> {
    Logcat.ensureLargeBuffer()
    // Keep the App Open resume ad from popping over the activity mid-test.
    app.appOpenManager.showOnResume = false
    // A phone whose screen sleeps mid-test backgrounds the app, and the library then (correctly)
    // pauses retries. Wake + unlock it, and keep the screen on while the test activity is open.
    shell("input keyevent KEYCODE_WAKEUP")
    shell("wm dismiss-keyguard")
    val scenario = ActivityScenario.launch(MainActivity::class.java)
    scenario.onActivity { it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    waitUntil(45_000, "Ads SDK initialized (consent + init)") { app.adsManager.isInitialized.value }
    return scenario
}

/**
 * Records [AdEvent]s straight from the library (via [AdsManager.addEventListener]) — exact and
 * immune to Logcat buffer limits / log throttling on physical phones.
 */
class EventRecorder(private val filter: (AdEvent) -> Boolean = { true }) : AdEventListener {
    private val recorded = java.util.concurrent.CopyOnWriteArrayList<AdEvent>()

    override fun onAdEvent(event: AdEvent) {
        if (filter(event)) recorded += event
    }

    val events: List<AdEvent> get() = recorded.toList()
    inline fun <reified T : AdEvent> ofType(): List<T> = events.filterIsInstance<T>()

    fun waitFor(timeoutMs: Long, what: String, done: (List<AdEvent>) -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (!done(events)) {
            // Message built at failure time, so it shows what was actually recorded.
            check(SystemClock.elapsedRealtime() < deadline) { "Timed out after ${timeoutMs}ms waiting for: $what — recorded: $events" }
            SystemClock.sleep(100)
        }
    }

    fun attach() = apply { app.adsManager.addEventListener(this) }
    fun detach() = app.adsManager.removeEventListener(this)
}
