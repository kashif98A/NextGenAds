package com.admodule.next.genads.adsImp.manager

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform

/**
 * GDPR / IAB-TCF consent handling via Google's User Messaging Platform (UMP).
 *
 * Recommended flow (per Google's docs):
 *   1. [gatherConsent] → requestConsentInfoUpdate — refreshes the latest consent state from the UMP
 *      backend for this app + user.
 *   2. loadAndShowConsentFormIfRequired — shows the consent form ONLY when UMP says it is required
 *      (e.g. an EEA/UK user, or a state that is out of date). For everyone else it returns instantly
 *      with no UI.
 *   3. [canRequestAds] — once true, it is safe to initialize the Ads SDK and request ads.
 *
 * IMPORTANT: do NOT initialize the Ads SDK or request any ad until [canRequestAds] is true. That is
 * what makes the integration GDPR-compliant. See [BaseApplication] / [MainActivity] for the wiring.
 *
 * This object is UI-agnostic and safe to hold as an application-scoped singleton (it is created with
 * the application [Context]), but every method that may present a form needs a foreground [Activity].
 */
class ConsentManager(context: Context) {

    private val TAG = "ConsentManager"

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    /** True once UMP has enough consent to request ads (consent given, or consent not required). */
    val canRequestAds: Boolean
        get() = consentInformation.canRequestAds()

    /**
     * True when a re-openable "Privacy options" entry point must be offered to the user (required for
     * EEA users under the IAB TCF). Only show a privacy button when this is true.
     */
    val isPrivacyOptionsRequired: Boolean
        get() = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /**
     * Runs the full consent flow. Call ONCE, early, from a foreground [Activity] (splash or main
     * screen). [onConsentResolved] is delivered on the main thread after consent is gathered (or
     * determined not required, or on error) — [error] is null on success.
     *
     * Regardless of [error], check [canRequestAds] afterwards: a returning, already-consented user
     * resolves with no form shown and [canRequestAds] == true.
     */
    fun gatherConsent(activity: Activity, onConsentResolved: (error: FormError?) -> Unit) {
        val params = ConsentRequestParameters.Builder()
            // Set true only for apps directed at children / users under the age of consent.
            .setTagForUnderAgeOfConsent(false)
            // To TEST the EEA form from a non-EEA device, uncomment and supply your device's hashed id
            // (printed to Logcat by UMP on the first run):
            // .setConsentDebugSettings(debugSettings(activity))
            .build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                // Info updated: show the form if UMP requires it, otherwise this returns with no UI.
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form dismissed with error: ${formError.message} (${formError.errorCode})")
                    } else {
                        Log.i(TAG, "Consent resolved. canRequestAds=$canRequestAds")
                    }
                    onConsentResolved(formError)
                }
            },
            { requestError ->
                Log.w(TAG, "Consent info update failed: ${requestError.message} (${requestError.errorCode})")
                // Still call back; the caller falls back to canRequestAds (which may already be true
                // from a previously stored consent decision).
                onConsentResolved(requestError)
            }
        )
    }

    /**
     * Re-opens the consent form so the user can change their choices. Wire this to a "Privacy options"
     * button, and only surface that button while [isPrivacyOptionsRequired] is true.
     */
    fun showPrivacyOptionsForm(activity: Activity, onDismiss: (error: FormError?) -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) {
                Log.w(TAG, "Privacy options form error: ${formError.message} (${formError.errorCode})")
            }
            onDismiss(formError)
        }
    }

    /** Clears all stored consent state. TESTING ONLY — lets you re-trigger the form on next launch. */
    fun reset() {
        consentInformation.reset()
    }

    /**
     * Forces the EEA experience on a specific test device. Referenced from [gatherConsent] (commented
     * out) so it is available when you need to verify the form without shipping to an EEA region.
     */
    @Suppress("unused")
    private fun debugSettings(activity: Activity): ConsentDebugSettings =
        ConsentDebugSettings.Builder(activity)
            .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
            // Replace with the hashed device id printed to Logcat on the first run.
            .addTestDeviceHashedId("TEST-DEVICE-HASHED-ID")
            .build()
}
