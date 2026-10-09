package com.admodule.next.genads.adsImp.manager

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
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
 *   0. If [canRequestAds] is ALREADY true (a returning user's stored decision), initialize the Ads SDK
 *      immediately, in parallel with step 1 — don't make every launch wait on the network.
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

    private val mainHandler = Handler(Looper.getMainLooper())

    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    /**
     * QA ONLY — forces the EEA (GDPR) consent experience on the listed test devices so the form can be
     * verified from outside the EEA. Use the hashed device id UMP prints to Logcat on the first run
     * ("Use new ConsentDebugSettings.Builder().addTestDeviceHashedId(...)"). Emulators need no id.
     * Leave empty (the default) in release builds — UMP ignores debug settings on non-test devices.
     */
    var eeaDebugTestDeviceIds: List<String> = emptyList()

    /** QA ONLY — force the EEA experience on emulators / listed devices even with no ids given. */
    var forceEeaDebugGeography: Boolean = false

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
        requestConsentUpdate(activity, attempt = 0, onConsentResolved)
    }

    private fun requestConsentUpdate(
        activity: Activity,
        attempt: Int,
        onConsentResolved: (error: FormError?) -> Unit
    ) {
        val params = ConsentRequestParameters.Builder()
            // Set true only for apps directed at children / users under the age of consent.
            .setTagForUnderAgeOfConsent(false)
            .apply {
                if (forceEeaDebugGeography || eeaDebugTestDeviceIds.isNotEmpty()) {
                    setConsentDebugSettings(debugSettings(activity))
                }
            }
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
                // A NEW user with no stored decision can't get ads until this succeeds, so retry a few
                // times (e.g. a flaky first-launch network) instead of leaving ads off all session.
                // Users with a stored decision resolve right away — canRequestAds is already true.
                // Configuration errors (e.g. a missing App ID) can't be fixed by retrying.
                val isTransient = requestError.errorCode in TRANSIENT_ERROR_CODES
                if (isTransient && !canRequestAds && attempt < MAX_CONSENT_RETRIES && activity.isAlive()) {
                    val delayMs = CONSENT_RETRY_BASE_MS shl attempt
                    Log.d(TAG, "Retrying consent info update in $delayMs ms (retry #${attempt + 1})")
                    mainHandler.postDelayed({
                        if (activity.isAlive()) {
                            requestConsentUpdate(activity, attempt + 1, onConsentResolved)
                        } else {
                            onConsentResolved(requestError)
                        }
                    }, delayMs)
                } else {
                    // The caller falls back to canRequestAds (which may already be true from a
                    // previously stored consent decision).
                    onConsentResolved(requestError)
                }
            }
        )
    }

    private fun Activity.isAlive(): Boolean = !isFinishing && !isDestroyed

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

    /** Forces the EEA experience on the configured test devices (see [eeaDebugTestDeviceIds]). */
    private fun debugSettings(activity: Activity): ConsentDebugSettings =
        ConsentDebugSettings.Builder(activity)
            .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
            .apply { eeaDebugTestDeviceIds.forEach { addTestDeviceHashedId(it) } }
            .build()

    private companion object {
        const val MAX_CONSENT_RETRIES = 3
        const val CONSENT_RETRY_BASE_MS = 2000L
        val TRANSIENT_ERROR_CODES = setOf(
            FormError.ErrorCode.INTERNAL_ERROR,
            FormError.ErrorCode.INTERNET_ERROR,
            FormError.ErrorCode.TIME_OUT
        )
    }
}
