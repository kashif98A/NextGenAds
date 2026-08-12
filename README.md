# NextGenAds — Next-Gen Google Mobile Ads module

A reusable Android **library** (plus a runnable demo app) built on the **Next-Gen Google Mobile
Ads SDK** (`com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk`). It loads and shows every
AdMob format from **Jetpack Compose**, with retry + exponential backoff, cache expiry,
network-aware loading, auto-preloading of the next full-screen ad after dismiss, and a proper
**GDPR consent flow** via Google's User Messaging Platform (UMP).

The project is split into two Gradle modules:

- **`:nextgenads`** — the publishable library (all the ad + consent code). Namespace
  `com.admodule.next.genads.adsImp`.
- **`:app`** — a demo dashboard that depends on `:nextgenads` and exercises every format.

## Install (JitPack)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.github.kashif98A:nextgenads:<tag-or-commit>")
}
```

The library exposes `ads-mobile-sdk` and `user-messaging-platform` as `api`, so consumers get those
SDK types transitively.

## Ad formats supported

| Type | Component |
| --- | --- |
| App Open | `AppOpenManager` |
| Interstitial (preload **and** on-demand) | `InterstitialManager` |
| Rewarded | `RewardedManager` |
| Rewarded Interstitial | `RewardedInterstitialManager` |
| Banner | `BannerAdView` |
| Adaptive (anchored) Banner | `AdaptiveBannerAdView` |
| Collapsible Banner | `CollapsibleBannerAdView` |
| Native (small / medium / collapsible) | `NativeSmallAdView`, `NativeMediumAdView`, `NativeCollapsibleAdView` |
| GDPR consent (UMP) | `ConsentManager` |

## Tech stack

- **Language:** Kotlin 2.2.10 (Java 17)
- **UI:** Jetpack Compose (Compose BOM 2024.09.00, Material 3)
- **Ads:** Next-Gen GMA SDK 1.2.0 · UMP 4.0.0
- **Async:** Kotlinx Coroutines 1.10.2 (all SDK init/loads run off the main thread on `Dispatchers.IO`)
- **Build:** Android Gradle Plugin 9.1.1 · `minSdk 24` · `targetSdk 37` · `compileSdk 37`
- **DI:** none — managers are wired programmatically as a service locator (see the demo's `BaseApplication`)

## Project structure

```
nextgenads/                                  # the library module
  src/main/AndroidManifest.xml               # INTERNET + ACCESS_NETWORK_STATE (merged into consumers)
  src/main/java/com/admodule/next/genads/adsImp/
    AdUnitIds.kt                             # ad unit IDs (single source of truth)
    data/NetworkMonitor.kt                   # online/offline Flow (interface + LiveNetworkMonitor)
    manager/
      AdsManager.kt                          # SDK init + request builders + network checks (core)
      ConsentManager.kt                      # GDPR / UMP consent flow
      AppOpenManager.kt
      InterstitialManager.kt                 # preload + on-demand
      RewardedManager.kt
      RewardedInterstitialManager.kt
    ui/                                       # Compose ad composables + loading dialogs

app/                                         # demo app (depends on :nextgenads)
  src/main/java/com/admodule/next/genads/
    BaseApplication.kt                        # bootstraps + exposes the managers (service locator)
    MainActivity.kt                           # Compose demo dashboard
    ui/theme/
```

## Getting started

1. Open the project in **Android Studio** and let it sync Gradle.
2. Run the demo:
   ```bash
   ./gradlew :app:installDebug
   ```
   It ships with **AdMob test ad unit IDs**, so it shows test ads out of the box.

## Using your own ad IDs

1. Replace the test IDs in `nextgenads/.../adsImp/AdUnitIds.kt` with your production ad unit IDs.
2. Replace `AdsManager.APP_ID` in `adsImp/manager/AdsManager.kt` with your real AdMob **App ID**.
3. Update the App ID `<meta-data>` in the **app manifest** to the same value (see below).

The Next-Gen Ads SDK receives the App ID **programmatically** via `InitializationConfig.Builder(APP_ID)`,
but the **UMP consent SDK** reads it from a manifest `<meta-data>` tag — so the app manifest **must**
declare it, or `requestConsentInfoUpdate()` fails, `canRequestAds` never becomes true, and no ads load:

```xml
<meta-data
    android:name="com.google.android.gms.ads.APPLICATION_ID"
    android:value="ca-app-pub-XXXX~XXXX" /> <!-- keep in sync with AdsManager.APP_ID -->
```

The only permissions needed (`INTERNET`, `ACCESS_NETWORK_STATE`) are declared in the library
manifest and merged into your app.

## GDPR consent (UMP)

`ConsentManager` wraps the User Messaging Platform. Call it once from a foreground Activity
(main screen or a splash) **before** initializing ads:

```kotlin
consentManager.gatherConsent(activity) { error ->
    if (consentManager.canRequestAds) {
        adsManager.initialize()   // ads init ONLY after consent
    }
}
```

`ads-mobile-sdk` must not be initialized (and no ad requested) until `canRequestAds` is true — the
demo gates the SDK init and every ad load on this. Offer a re-openable privacy entry point with
`consentManager.showPrivacyOptionsForm(activity)` when `consentManager.isPrivacyOptionsRequired`.

## Showing ads

```kotlin
// Full-screen (from an Activity)
interstitialManager.showAd(activity) { /* dismissed */ }                 // preload strategy
interstitialManager.loadAndShow(activity,                                // on-demand strategy
    onLoading = { loading -> /* toggle a full-screen loader */ },
    onDismiss = { /* closed */ })
rewardedManager.showAd(activity,
    onUserEarnedReward = { type, amount -> /* grant reward */ },
    onDismiss = { })
appOpenManager.showAd(activity) { /* dismissed */ }

// Banner / native (Jetpack Compose)
BannerAdView(adsManager)
CollapsibleBannerAdView(adsManager, direction = "bottom")
NativeMediumAdView(adsManager)
```

Full-screen managers auto-preload the next ad on dismiss — no manual reload.

**Built-in behavior:** retry with exponential backoff, 4-hour cache expiry, network checks,
auto-preload after dismiss, and a consent gate. There is intentionally **no** remote config,
analytics, frequency capping, or cooldown layer.

## Caveats

- **The `ui/` composables are Compose-only.** The four full-screen managers are pure Kotlin and
  work in any project (Compose or View-based).
- **App-open auto-show is a stub.** `AppOpenManager.showAdIfReady()` has no Activity reference, so
  app-open ads *load* but won't auto-*display* until you call `appOpenManager.showAd(activity)`
  from a foreground Activity.
- **Ads are gated on consent + init.** Inline banner/native views render a placeholder until the
  SDK is initialized (which only happens after the consent flow resolves).
