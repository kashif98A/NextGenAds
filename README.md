# NextGenAds — AdMob for Jetpack Compose (Next-Gen SDK)

A reusable Android **library** plus a runnable **demo app**, built on Google's **Next-Gen Mobile Ads
SDK**. It covers every AdMob format: App Open, Interstitial, Rewarded, Rewarded Interstitial, Banner
(standard / adaptive / collapsible) and Native (small / medium / collapsible). It includes a
**GDPR consent flow** (Google UMP) and loading logic tuned for a high **show rate**.

- **`:nextgenads`**: the library you add to your app (namespace `com.admodule.next.genads.adsImp`).
- **`:app`**: a demo dashboard that uses every format. Read `BaseApplication.kt` and
  `MainActivity.kt` for a complete, working integration.

---

## Contents

1. [Versions](#versions)
2. [What's new in 1.1.0](#whats-new-in-110)
3. [Installation](#installation)
4. [Quick start (3 steps)](#quick-start-3-steps)
5. [GDPR consent form (UMP)](#gdpr-consent-form-ump)
6. [Calling each ad format](#calling-each-ad-format)
   - [Interstitial](#interstitial)
   - [Rewarded](#rewarded)
   - [Rewarded Interstitial](#rewarded-interstitial)
   - [App Open](#app-open)
   - [Banner (standard / adaptive / collapsible)](#banners)
   - [Native (small / medium / collapsible)](#native-ads)
   - [Loading dialogs](#loading-dialogs)
7. [Ad events, match rate & show rate](#ad-events-match-rate--show-rate)
8. [Using your own ad unit IDs](#using-your-own-ad-unit-ids)
9. [Configuration reference](#configuration-reference)
10. [Built-in behaviour](#built-in-behaviour)
11. [Testing](#testing)
12. [Release checklist](#release-checklist)
13. [Troubleshooting](#troubleshooting)
14. [Project structure](#project-structure)

---

## Versions

| | Version |
| --- | --- |
| **NextGenAds library** | **1.1.0** |
| Next-Gen Google Mobile Ads SDK (`ads-mobile-sdk`) | 1.5.0 |
| User Messaging Platform (UMP, consent) | 4.0.0 |
| Kotlin | 2.4.21 (JVM target 17) |
| Android Gradle Plugin / Gradle | 9.4.1 / 9.8.1 |
| Compose BOM | 2026.09.00 (Material 3) |
| AndroidX Lifecycle / Activity / Core KTX | 2.11.0 / 1.13.0 / 1.19.1 |
| Kotlinx Coroutines | 1.11.0 |
| `minSdk` / `targetSdk` / `compileSdk` | 24 / 37 / 37 |

The library exposes `ads-mobile-sdk` and `user-messaging-platform` as `api` dependencies, so your
app gets those SDK classes automatically and doesn't need to declare them.

## What's new in 1.1.0

- **Ad events and metrics.** `adsManager.addEventListener { }` reports every request, load, failure,
  expiry, impression, click and impression revenue for all 6 formats. The ready-made `AdStats`
  listener computes **match rate** and **show rate** per format.
- **App Open ads now show when the user returns to the app.** They never appear on top of another
  full-screen ad, and they're skipped right after the user taps an ad.
- **Native ad pool.** Native ads are preloaded and reused, so a loaded native ad is never wasted.
- **Retries are limited.** A failed load is retried at most 4 times, only for temporary errors, and
  never while the app is in the background.
- **Banners and natives stay on screen during short network drops.**
- **The App ID goes in the manifest.** UMP needs it there (see [Installation](#installation)).
- **Fixed:** before 1.1.0 the consent request failed on a fresh install, so no ads ever loaded.
- **Faster start for returning users.** Ads start while the consent status is still refreshing.
- **Policy-safe native layouts.** Google's native ad validator reports no issues.
- Every library dependency is updated to its latest stable version, and the project now has a test
  suite (JVM and on-device).

---

## Installation

### 1. Add the dependency (JitPack)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.github.kashif98A:NextGenAds:1.1.0") // the git tag (or a commit hash)
}
```

### 2. Set your AdMob App ID (required)

The library's manifest declares the `com.google.android.gms.ads.APPLICATION_ID` meta-data. You only
supply the value:

```kotlin
// app/build.gradle.kts
android {
    defaultConfig {
        manifestPlaceholders["admobAppId"] = "ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY"
    }
}
```

> **If you skip this, the build fails** with a missing `admobAppId` placeholder error, which is
> deliberate. Without the App ID the UMP consent request fails and **no ad ever loads**.
> `AdsManager` reads the App ID from this same meta-data, so you only set it here.

### 3. Permissions

`INTERNET` and `ACCESS_NETWORK_STATE` are declared in the library manifest and merged into your app
automatically. You don't need to add anything.

### 4. Compose

The banner and native ad views are Jetpack Compose functions, so enable Compose in your app module
(`buildFeatures { compose = true }`). The full-screen managers (interstitial, rewarded, app open) are
plain Kotlin and also work in View-based apps.

---

## Quick start (3 steps)

### Step 1: Create the managers once, in your `Application`

```kotlin
class App : Application() {

    lateinit var adsManager: AdsManager; private set
    lateinit var consentManager: ConsentManager; private set
    lateinit var appOpenManager: AppOpenManager; private set
    lateinit var interstitialManager: InterstitialManager; private set
    lateinit var rewardedManager: RewardedManager; private set
    lateinit var rewardedInterstitialManager: RewardedInterstitialManager; private set

    override fun onCreate() {
        super.onCreate()
        adsManager = AdsManager(this, LiveNetworkMonitor(this))
        consentManager = ConsentManager(this)

        // Create every manager here. AppOpenManager needs to exist before the first Activity starts.
        appOpenManager = AppOpenManager(adsManager)
        interstitialManager = InterstitialManager(adsManager)
        rewardedManager = RewardedManager(adsManager)
        rewardedInterstitialManager = RewardedInterstitialManager(adsManager)

        // Runs when the SDK is ready, when the network comes back, and each time the app returns to
        // the foreground. Preload only the formats most sessions will show. Each call does nothing
        // if a fresh ad is already cached.
        adsManager.setPreloaderCallback {
            appOpenManager.loadAd()
            interstitialManager.loadAd()
            adsManager.nativeAds().preload()
        }

        // Do NOT call adsManager.initialize() here. It must wait for consent (Step 2).
    }
}
```

Register the class in your manifest with `<application android:name=".App" …>`.

### Step 2: Gather consent and start the SDK from your first Activity

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as App

        setContent { /* your UI */ }   // set the content FIRST (see the note below)

        // A returning user with a stored decision: start ads right away, while consent refreshes.
        if (app.consentManager.canRequestAds) app.adsManager.initialize()

        // Shows the consent form only if it's needed (for example, an EEA/UK user with no decision yet).
        app.consentManager.gatherConsent(this) { error ->
            if (app.consentManager.canRequestAds) {
                app.adsManager.initialize() // safe to call twice: only the first call does anything
            }
        }
    }
}
```

> **Call `gatherConsent` after `setContent` (or `setContentView`).** UMP inspects the Activity's
> window. If it runs before the window has content, the first request fails with `Window couldn't
> find content container view`. The library retries and recovers, but only after an extra ~2 s.

### Step 3: Show ads

```kotlin
app.interstitialManager.showAd(activity) { goToNextScreen() } // full-screen
AdaptiveBannerAdView(app.adsManager)                          // in Compose
NativeMediumAdView(app.adsManager)                            // in Compose
```

That's it. App Open ads now show by themselves whenever the user returns to the app.

---

## GDPR consent form (UMP)

`ConsentManager` wraps Google's **User Messaging Platform**, which shows the GDPR/IAB TCF consent
form required for users in the EEA, the UK and Switzerland.

### How the flow works

```
App start ─► gatherConsent(activity)
               │
               ├─ UMP refreshes the consent status from Google
               │     └─ fails? retried 3× (2s, 4s, 8s) for temporary errors
               │
               ├─ form required (EEA/UK user, no decision yet)?
               │     ├─ yes → shows the form: [Consent] [Do not consent] [Manage options]
               │     └─ no  → returns immediately, no UI
               │
               └─ onConsentResolved(error) ─► if (canRequestAds) adsManager.initialize()
```

### API

| Member | What it does |
| --- | --- |
| `gatherConsent(activity) { error -> }` | Runs the full flow and shows the form only when required. The callback runs on the main thread; `error` is `null` on success. **Always check `canRequestAds` afterwards, not `error`**: a returning user resolves with no form, and `canRequestAds` is already `true`. |
| `canRequestAds: Boolean` | `true` when ads may be requested (consent given, or not required). **Never initialize the SDK or load an ad while this is `false`.** |
| `isPrivacyOptionsRequired: Boolean` | `true` for users who must be able to change their choice (EEA/UK). Show a "Privacy options" button only when this is `true`. |
| `showPrivacyOptionsForm(activity) { error -> }` | Opens the form again so the user can change their choice. |
| `reset()` | Clears the stored consent. **For testing only.** |
| `forceEeaDebugGeography` / `eeaDebugTestDeviceIds` | Make UMP act as if the device is in the EEA, so you can see the form from anywhere. **For testing only** (see below). |

### "Privacy options" button (required for EEA users)

```kotlin
var showPrivacyButton by remember { mutableStateOf(consentManager.isPrivacyOptionsRequired) }

if (showPrivacyButton) {
    TextButton(onClick = {
        consentManager.showPrivacyOptionsForm(activity) {
            showPrivacyButton = consentManager.isPrivacyOptionsRequired
        }
    }) { Text("Privacy options") }
}
```

### What each answer means

| User taps | `canRequestAds` | Ads |
| --- | --- | --- |
| **Consent** | `true` | Personalized ads |
| **Do not consent** | `true` | Limited / non-personalized ads (UMP still allows requests) |
| *(form not answered yet)* | `false` | None. The SDK is not started |

### Seeing the EEA form from outside the EEA

```kotlin
consentManager.reset()                        // forget the stored decision
consentManager.forceEeaDebugGeography = true  // emulators: this alone is enough
// Physical phone: also add the hashed device ID that UMP prints to Logcat on its first run:
// consentManager.eeaDebugTestDeviceIds = listOf("33BE2250B43518CCDA7DE426D04EE231")
consentManager.gatherConsent(activity) { /* … */ }
```

UMP ignores these debug settings on devices that aren't registered as test devices. Still, never
turn them on in release builds.

---

## Calling each ad format

Every full-screen manager works the same way:

| Method / property | Meaning |
| --- | --- |
| `loadAd()` | Starts loading an ad in the background. Does nothing if a fresh ad is already cached or a load is already running. |
| `isAdAvailable(): Boolean` | `true` when a cached ad exists **and has not expired**. |
| `isAdLoaded: StateFlow<Boolean>` | Observe this in Compose (`collectAsStateWithLifecycle()`) to drive "Ready" or "Watch ad" UI. |
| `showAd(activity, …, onDismiss)` | Shows the cached ad. If none is ready, it starts a load and calls `onDismiss` **immediately**, so your flow always continues. The next ad starts loading after the current one closes. |
| `adUnitId` / `isEnabled` / `expiryMs` | See [Configuration reference](#configuration-reference). |

> All callbacks (`onDismiss`, `onUserEarnedReward`, `onLoading`) are delivered on the **main
> thread**, so you can update Compose state, show toasts or navigate from them directly.

### Interstitial

Two ways to call it:

**A. Preloaded (recommended).** It shows instantly because the ad is already cached:

```kotlin
// It loads automatically through setPreloaderCallback. Show it at a natural break:
interstitialManager.showAd(activity) {
    navigateToNextScreen() // runs after the ad closes, or right away if no ad was ready
}
```

**B. On-demand.** It loads a fresh ad when the user taps, with a full-screen loader:

```kotlin
var loading by remember { mutableStateOf(false) }
if (loading) AdLoadingDialog(message = "Loading ad")

Button(onClick = {
    interstitialManager.loadAndShow(
        activity = activity,
        onLoading = { isLoading -> loading = isLoading }, // true while fetching; false once shown or failed
        onDismiss = { navigateToNextScreen() }           // after close, or right away if it failed
    )
}) { Text("Continue") }
```

`loadAndShow` is separate from the preloaded cache. It never uses or replaces the cached ad.

**C. Preloaded, with a short "Ad starting…" screen.** Use this when the ad might still be loading:

```kotlin
var showLoader by remember { mutableStateOf(false) }
if (showLoader) {
    AdStartingDialog(
        isAdReady = { interstitialManager.isAdAvailable() },
        onReadyToShow = {
            showLoader = false
            interstitialManager.showAd(activity) { navigateToNextScreen() }
        }
    )
}
Button(onClick = { interstitialManager.loadAd(); showLoader = true }) { Text("Continue") }
```

### Rewarded

Load it when the screen that **offers** the reward opens. Don't preload it at app start, or it may
expire before anyone sees it:

```kotlin
LaunchedEffect(Unit) { rewardedManager.loadAd() }

val ready by rewardedManager.isAdLoaded.collectAsStateWithLifecycle()

Button(enabled = ready, onClick = {
    rewardedManager.showAd(
        activity = activity,
        onUserEarnedReward = { type, amount ->  // e.g. ("coins", 10). Grant the reward HERE.
            coins += amount
        },
        onDismiss = { /* ad closed (the reward callback, if any, has already fired) */ }
    )
}) { Text("Watch ad for +10 coins") }
```

> Grant the reward **only** in `onUserEarnedReward`. If the user closes the ad early, `onDismiss`
> runs without it.

### Rewarded Interstitial

Same API as Rewarded. It's typically shown at a natural break (between levels, for example) with an
opt-out intro screen:

```kotlin
LaunchedEffect(Unit) { rewardedInterstitialManager.loadAd() }

rewardedInterstitialManager.showAd(
    activity = activity,
    onUserEarnedReward = { type, amount -> grantBonus(amount) },
    onDismiss = { continueGame() }
)
```

### App Open

**Automatic (default).** With `AppOpenManager` created in your `Application`, an App Open ad shows
every time the app **returns to the foreground**, over the current Activity. It is skipped when:

- another full-screen ad is on screen
- the user is coming back from tapping a banner or native ad
- the current Activity is in `excludedActivities`
- you called `adsManager.skipNextAppOpen()`

```kotlin
appOpenManager.excludedActivities += SplashActivity::class.java // never show over these
appOpenManager.showOnResume = false                              // or turn automatic mode off

// Before sending the user out of your app on purpose (share sheet, purchase, settings, permissions):
adsManager.skipNextAppOpen()
```

**Manual.** Show it yourself, for example at the end of a splash screen:

```kotlin
appOpenManager.showAd(activity) { openHome() } // runs after close, or right away if none was ready
```

### Banners

Banners are Compose functions. Each one loads its own ad, shows a shimmer placeholder while loading,
takes up no space if the load fails, and cleans up when removed from the screen.

```kotlin
val isInitialized by adsManager.isInitialized.collectAsStateWithLifecycle()

if (isInitialized) { // create banners only after consent + SDK start
    BannerAdView(adsManager)                                    // 320×50 standard
    AdaptiveBannerAdView(adsManager)                            // full-width anchored adaptive (recommended)
    CollapsibleBannerAdView(adsManager, direction = "bottom")   // "bottom" or "top"
}
```

| Parameter | Default | Notes |
| --- | --- | --- |
| `adsManager` | required | |
| `modifier` | `Modifier` | |
| `adUnitId` | `AdUnitIds.BANNER` (`COLLAPSIBLE_BANNER` for collapsible) | Your production ID |
| `isEnabled` | `true` | `false` renders nothing (for example, a "remove ads" purchase) |
| `direction` | `"bottom"` | Collapsible banner only: the edge it collapses toward |

### Native ads

Three ready-made layouts. They take their ads from a **shared pool**, so an ad shows instantly when
one is cached and a loaded ad is never thrown away.

```kotlin
if (isInitialized) {
    NativeSmallAdView(adsManager)        // icon + headline + body + button
    NativeMediumAdView(adsManager)       // large media + icon + headline + body + button
    NativeCollapsibleAdView(adsManager)  // media the user can shrink or expand (never hidden; policy-safe)
}
```

Parameters: `adsManager`, `modifier`, `adUnitId = AdUnitIds.NATIVE`, `isEnabled = true`.

**The pool.** Get it with `adsManager.nativeAds(adUnitId)`. There is one pool per ad unit:

```kotlin
val pool = adsManager.nativeAds()   // AdsManager.nativeAds(adUnitId = AdUnitIds.NATIVE)
pool.preload()                      // fill it up to poolSize (default 2), e.g. at app start
pool.poolSize = 3                   // keep more ads ready on screens with many native slots
pool.clear()                        // destroy every cached ad (e.g. after a "remove ads" purchase)

// Advanced: get an ad yourself for a custom layout, and destroy() it once it has been displayed.
val ad: NativeAd? = pool.acquire()  // suspend; null if nothing could be loaded
```

### Loading dialogs

| Composable | Use |
| --- | --- |
| `AdStartingDialog(isAdReady, onReadyToShow, maxWaitMillis = 8000, minVisibleMillis = 600, message)` | "Ad starting…" overlay that waits for a **preloaded** ad. It moves on as soon as the ad is ready, and after 8 s at most. |
| `AdLoadingDialog(message = "Loading ad")` | Plain full-screen loader you show and hide yourself. Use it with `loadAndShow`'s `onLoading`. |
| `BannerAdPlaceholder` / `NativeAdPlaceholder(isMedium)` | The shimmer placeholders, if you want them for custom layouts. |

---

## Ad events, match rate & show rate

Every ad format reports its lifecycle as an `AdEvent`. Register a listener once, for example in your
`Application`, and send the events to your analytics:

```kotlin
adsManager.addEventListener { event ->
    when (event) {
        is AdEvent.LoadRequested -> analytics.log("ad_request", event.format, event.adUnitId)
        is AdEvent.Loaded        -> analytics.log("ad_loaded", event.format, "latency" to event.latencyMs)
        is AdEvent.LoadFailed    -> analytics.log("ad_failed", event.format, "code" to event.code, "retry" to event.willRetry)
        is AdEvent.Expired       -> analytics.log("ad_expired_unseen", event.format)  // a lost impression
        is AdEvent.Impression    -> analytics.log("ad_impression", event.format)
        is AdEvent.Clicked       -> analytics.log("ad_click", event.format)
        is AdEvent.Paid          -> analytics.logRevenue(event.valueMicros / 1e6, event.currencyCode)
        is AdEvent.ShowFailed, is AdEvent.Dismissed -> Unit
    }
}
```

| Event | When it fires |
| --- | --- |
| `LoadRequested` | A request was sent: the first load, every automatic retry, and every banner auto-refresh |
| `Loaded(latencyMs)` | The request returned an ad (`latencyMs` is `-1` for a banner auto-refresh) |
| `LoadFailed(code, message, retryInMs)` | The request failed. `retryInMs` is when the library will retry, or `null` if it won't (`willRetry`) |
| `Expired` | A loaded ad passed its `expiryMs` without being shown and was thrown away |
| `Impression` | The SDK counted an impression |
| `Clicked` | The user tapped the ad |
| `Paid(valueMicros, currencyCode, precision)` | Revenue for an impression (impression-level ad revenue) |
| `ShowFailed(message)` / `Dismissed` | A full-screen ad couldn't be shown / was closed |

Every event carries `format` (`APP_OPEN`, `INTERSTITIAL`, `REWARDED`, `REWARDED_INTERSTITIAL`,
`BANNER`, `NATIVE`) and `adUnitId`.

> Listeners may be called on **any thread** (SDK callbacks arrive on worker threads), so keep them
> fast and thread-safe. If a listener throws, the error is caught and logged, and ads keep working.

### Measure match rate and show rate with `AdStats`

```kotlin
val stats = AdStats().also(adsManager::addEventListener)

val inter = stats.snapshot(AdFormat.INTERSTITIAL)
inter.matchRate   // loaded ÷ requested    e.g. 0.92 → 92% of requests returned an ad
inter.showRate    // impressions ÷ loaded  e.g. 0.81 → 81% of loaded ads were actually seen
inter.expired     // ads that expired unseen (wasted)
inter.revenueMicros
stats.total()     // all formats combined
stats.reset()
```

The demo app shows these numbers live in an **"Ad metrics (this session)"** card on its dashboard,
and logs every event under the Logcat tag `AdEvents`.

**Reading the numbers:**
- A low **match rate** is mostly about ad demand: add mediation, check `app-ads.txt`, check floors.
  Many `LoadFailed` events with `NETWORK_ERROR` and HTTP 400/403 mean a wrong ad unit ID.
- A low **show rate** is about your code and placements: ads loaded but never shown. A high
  `expired` count means you preload a format users rarely reach, so load it closer to where it's
  shown.

---

## Using your own ad unit IDs

`AdUnitIds` holds **Google's test IDs**. Use them while developing. For release, pass your
production IDs to the managers and Compose functions (you can't edit `AdUnitIds` inside the
published library):

```kotlin
interstitialManager = InterstitialManager(adsManager, adUnitId = "ca-app-pub-XXXX/1111111111")
rewardedManager     = RewardedManager(adsManager, adUnitId = "ca-app-pub-XXXX/2222222222")
appOpenManager      = AppOpenManager(adsManager, adUnitId = "ca-app-pub-XXXX/3333333333")

AdaptiveBannerAdView(adsManager, adUnitId = "ca-app-pub-XXXX/4444444444")
NativeMediumAdView(adsManager, adUnitId = "ca-app-pub-XXXX/5555555555")
adsManager.nativeAds("ca-app-pub-XXXX/5555555555").preload()
```

**Use a separate ad unit for each placement** (for example, home native vs. list native). AdMob
reports then show which placement earns and which underperforms.

---

## Configuration reference

**Full-screen managers** (`InterstitialManager`, `RewardedManager`, `RewardedInterstitialManager`,
`AppOpenManager`):

| Property | Default | Meaning |
| --- | --- | --- |
| `adUnitId` | test ID | AdMob ad unit |
| `isEnabled` | `true` | `false` makes `loadAd` do nothing and `showAd` call `onDismiss` right away |
| `expiryMs` | 1 h (App Open: 4 h) | A cached ad older than this is thrown away and reloaded, never shown |

**`AppOpenManager` only:** `showOnResume` (`true`), `excludedActivities` (empty), `showAdIfReady()`
(the check that runs when the app returns to the foreground; you can call it yourself).

**`NativeAdManager`** (from `adsManager.nativeAds()`): `poolSize` (2), `expiryMs` (1 h),
`isEnabled` (`true`), `preload()`, `suspend acquire()`, `clear()`.

**`AdsManager`:**

| Member | Meaning |
| --- | --- |
| `initialize(onComplete = {})` | Starts the SDK. Call it only when `canRequestAds` is true. Safe to call more than once. |
| `isInitialized: StateFlow<Boolean>` | Create banners and natives only after this becomes `true` |
| `setPreloaderCallback { }` | Your preload list. Runs when the SDK is ready, when the network returns, and each time the app returns to the foreground |
| `nativeAds(adUnitId)` | The native ad pool for that unit |
| `skipNextAppOpen()` | Skips the next App Open ad shown on return to the app |
| `isFullScreenAdShowing` | `true` while any full-screen ad is on screen |
| `appId` | The App ID read from the manifest |
| `addEventListener(listener)` / `removeEventListener(listener)` | Receive every `AdEvent` (see [Ad events](#ad-events-match-rate--show-rate)) |
| `networkMonitor.isOnline: Flow<Boolean>` | Live online/offline status |

---

## Built-in behaviour

Designed so that **an ad that loads gets shown** (show rate) without wasting requests (match rate):

| Behaviour | Detail |
| --- | --- |
| **Consent first** | Nothing loads before `canRequestAds` is true and the SDK has started. Returning users start right away. |
| **Limited retries** | Only temporary errors (no fill, network, timeout, internal) are retried: at most **4 times**, after 2 s, 4 s, 8 s and 16 s, and **never in the background**. Any `loadAd()` call, or the app returning to the foreground, starts a fresh set of retries. |
| **One load at a time** | Each ad unit has at most one request in progress, even when several things trigger a load at once. |
| **Expiry** | 4 h for App Open, 1 h for the others and for natives. An expired ad is reloaded, never shown. |
| **Auto reload** | When a full-screen ad closes or fails to show, the next one starts loading. |
| **Network drops** | Offline only blocks the *first* request. A banner or native already on screen stays when the network drops briefly (for example, Wi-Fi switching to mobile data). |
| **No stacking** | An App Open ad never appears on top of another full-screen ad, or right after an ad click. |

There is intentionally **no** remote config, analytics, frequency capping or cooldown layer. Add
your own on top if you need one.

---

## Testing

```bash
# JVM unit tests: retry policy on simulated time (seconds, no device needed)
./gradlew :nextgenads:testDebugUnitTest

# On-device tests against the REAL Next-Gen SDK + UMP (emulator or phone, about 5 min)
ANDROID_SERIAL=<device-serial> ./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
```

| Suite | What it checks |
| --- | --- |
| `AdStatsTest` (JVM) | Match rate, show rate, expired and revenue counting per format and in total |
| `AdLoadControllerTest` (JVM) | Which errors retry; max 4 retries at 2 s / 4 s / 8 s / 16 s; no retry in the background; the retry count resets after a success; one load in progress per unit, even with 64 threads competing |
| `RetryOnDeviceTest` | An ad unit the real SDK rejects gets exactly 5 requests (the first + 4 retries) at the intervals above, then stops. Counted from `AdEvent`s, not Logcat |
| `ExpiryOnDeviceTest` | Default expiry times; for every format, an expired ad is refused, never shown, and replaced with a fresh one; the native pool throws away stale ads |
| `EeaConsentOnDeviceTest` | The EEA form appears, ads stay blocked until the user answers, Consent and Do not consent both work, and Privacy options opens the form again |

The device tests wake the phone and keep its screen on while they run. Otherwise a sleeping screen
backgrounds the app, and the library correctly pauses retries. On some physical phones the
forced-EEA consent tests can be flaky because UMP's debug geography gets reset by extra consent
requests. They are stable on emulators. This only affects the test-only debug mode, not real users.

The demo uses Google's **test ad units**, so tapping its ads is safe. While developing with
**your own** IDs, register your phone as a test device, or AdMob may flag the clicks as invalid
traffic.

---

## Release checklist

- [ ] `manifestPlaceholders["admobAppId"]` is set to your **production App ID**
- [ ] Production ad unit IDs are passed to every manager and Compose function (no test IDs left)
- [ ] `forceEeaDebugGeography` and `eeaDebugTestDeviceIds` are **not** set
- [ ] A "Privacy options" button appears when `isPrivacyOptionsRequired` is true
- [ ] Splash, checkout and video screens are added to `appOpenManager.excludedActivities`
- [ ] `app-ads.txt` is published on your developer website and verified in AdMob
- [ ] Mediation (bidding) is set up in AdMob for a better match rate, if you use it
- [ ] The library version is raised, and a matching git tag is pushed for JitPack

---

## Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| **No ads at all, ever** | Check Logcat for `ConsentManager: Consent info update failed … APPLICATION_ID`. The App ID is missing: set `manifestPlaceholders["admobAppId"]`. |
| Build error: `requires a placeholder substitution … admobAppId` | Same fix: set the placeholder in your app's `defaultConfig`. |
| Logcat: `Window couldn't find content container view` from UMP | `gatherConsent` ran before `setContent`. Call it after the Activity has content (the library retries, but it costs ~2 s). |
| Ads load in the demo but not in your app | `adsManager.initialize()` is never called. It must run after `gatherConsent` with `canRequestAds == true`. |
| Banner or native missing, or a crash when the screen opens | It was created before the SDK finished starting. Wrap inline ads in `if (isInitialized)` (see [Banners](#banners)). |
| App Open shows over the splash screen | Add the splash to `appOpenManager.excludedActivities`. |
| App Open shows after a share or purchase screen | Call `adsManager.skipNextAppOpen()` before sending the user out of the app. |
| Logcat: `Giving up after 4 retries` | The unit has no fill, or the ad unit ID is wrong. In SDK 1.5.0 an **invalid ad unit is reported as `NETWORK_ERROR` (HTTP 400/403)**, so check the ID first. |
| Logcat: `AdMob native ad validator` popup | A debug-only Google check on test devices. "No implementation issues found" is the expected result. |

---

## Project structure

```
nextgenads/                                   # the library (published via JitPack)
  src/main/AndroidManifest.xml                # permissions + APPLICATION_ID meta-data (${admobAppId})
  src/main/java/com/admodule/next/genads/adsImp/
    AdUnitIds.kt                              # Google TEST ad unit IDs (defaults)
    data/NetworkMonitor.kt                    # online/offline Flow
    manager/
      AdsManager.kt                           # SDK start, request builders, preload hook, native pools
      ConsentManager.kt                       # GDPR / UMP consent flow + EEA debug switches
      AppOpenManager.kt                       # App Open (automatic on resume + manual)
      InterstitialManager.kt                  # preloaded + on-demand
      RewardedManager.kt
      RewardedInterstitialManager.kt
      NativeAdManager.kt                      # native ad pool
      AdEvents.kt                             # AdEvent, AdEventListener, AdStats (match / show rate)
      AdLoadController.kt                     # retry policy + one load at a time (internal)
    ui/                                       # Compose banners, natives, placeholders, dialogs
  src/test/                                   # JVM unit tests

app/                                          # demo app
  src/main/java/com/admodule/next/genads/
    BaseApplication.kt                        # creates the managers + preload list
    MainActivity.kt                           # consent + demo dashboard for every format
  src/androidTest/                            # on-device tests (real SDK + UMP)
```
