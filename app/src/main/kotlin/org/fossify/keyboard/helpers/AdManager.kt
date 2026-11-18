package org.fossify.keyboard.helpers

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import org.fossify.keyboard.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Play-policy compliant AdMob & Google UMP Consent manager.
 * IMPORTANT: Ads are ONLY shown inside app activities (Main, Settings, Clipboard Manager)
 * and NEVER inside SimpleKeyboardIME or any system keyboard overlay.
 */
object AdManager {
    private const val TAG = "KeynovaAds"
    private const val INTERSTITIAL_COOLDOWN_MS = 90_000L

    private val isMobileAdsInitialized = AtomicBoolean(false)
    private val isLoadingInterstitial = AtomicBoolean(false)
    private var interstitialAd: InterstitialAd? = null
    private var lastInterstitialShownElapsedMs: Long = 0L

    fun gatherConsentAndInitialize(activity: Activity, onCanRequestAds: () -> Unit) {
        if (!BuildConfig.ADS_ENABLED) return

        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "UMP consent form error: ${formError.message}")
                    }
                    if (consentInformation.canRequestAds()) {
                        initializeMobileAds(activity.applicationContext) {
                            onCanRequestAds()
                        }
                    }
                }
            },
            { requestError ->
                Log.w(TAG, "UMP consent info update failed: ${requestError.message}")
                if (consentInformation.canRequestAds()) {
                    initializeMobileAds(activity.applicationContext) {
                        onCanRequestAds()
                    }
                }
            }
        )

        if (consentInformation.canRequestAds()) {
            initializeMobileAds(activity.applicationContext) {
                onCanRequestAds()
            }
        }
    }

    fun isPrivacyOptionsRequired(context: Context): Boolean {
        if (!BuildConfig.ADS_ENABLED) return false
        return try {
            UserMessagingPlatform.getConsentInformation(context).privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        } catch (_: Exception) {
            false
        }
    }

    fun showPrivacyOptionsForm(activity: Activity) {
        if (!BuildConfig.ADS_ENABLED) return
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) {
                Log.w(TAG, "Privacy options form error: ${formError.message}")
            }
        }
    }

    private fun initializeMobileAds(appContext: Context, onReady: () -> Unit) {
        if (isMobileAdsInitialized.compareAndSet(false, true)) {
            Thread {
                try {
                    MobileAds.initialize(appContext) {
                        preloadInterstitial(appContext)
                        onReady()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "MobileAds init error", e)
                    isMobileAdsInitialized.set(false)
                }
            }.start()
        } else {
            onReady()
        }
    }

    fun loadAdaptiveBanner(activity: Activity, container: ViewGroup): AdView? {
        if (!BuildConfig.ADS_ENABLED) {
            container.visibility = View.GONE
            return null
        }

        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        if (!consentInformation.canRequestAds()) {
            return null
        }

        container.removeAllViews()
        val adSize = getAdaptiveBannerSize(activity, container)
        val adView = AdView(activity).apply {
            adUnitId = BuildConfig.ADMOB_BANNER_ID
            setAdSize(adSize)
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    container.visibility = View.VISIBLE
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.d(TAG, "Banner failed to load: ${error.message}")
                }
            }
        }

        container.addView(adView)
        adView.loadAd(AdRequest.Builder().build())
        return adView
    }

    fun preloadInterstitial(context: Context) {
        if (!BuildConfig.ADS_ENABLED) return
        if (interstitialAd != null || !isLoadingInterstitial.compareAndSet(false, true)) return

        val appContext = context.applicationContext
        val consentInfo = UserMessagingPlatform.getConsentInformation(appContext)
        if (!consentInfo.canRequestAds()) {
            isLoadingInterstitial.set(false)
            return
        }

        InterstitialAd.load(
            appContext,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoadingInterstitial.set(false)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.d(TAG, "Interstitial failed to load: ${error.message}")
                    interstitialAd = null
                    isLoadingInterstitial.set(false)
                }
            }
        )
    }

    fun maybeShowInterstitial(activity: Activity, onComplete: () -> Unit) {
        if (!BuildConfig.ADS_ENABLED) {
            onComplete()
            return
        }

        val now = SystemClock.elapsedRealtime()
        val readyAd = interstitialAd
        if (readyAd == null || (lastInterstitialShownElapsedMs != 0L && now - lastInterstitialShownElapsedMs < INTERSTITIAL_COOLDOWN_MS)) {
            preloadInterstitial(activity)
            onComplete()
            return
        }

        interstitialAd = null
        lastInterstitialShownElapsedMs = now
        readyAd.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preloadInterstitial(activity)
                onComplete()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.d(TAG, "Interstitial failed to show: ${error.message}")
                preloadInterstitial(activity)
                onComplete()
            }
        }
        readyAd.show(activity)
    }

    private fun getAdaptiveBannerSize(activity: Activity, container: ViewGroup): AdSize {
        val density = activity.resources.displayMetrics.density
        var adWidthPixels = container.width.toFloat()
        if (adWidthPixels <= 0f) {
            adWidthPixels = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.windowManager.currentWindowMetrics.bounds.width().toFloat()
            } else {
                activity.resources.displayMetrics.widthPixels.toFloat()
            }
        }
        val adWidthDp = (adWidthPixels / density).toInt().coerceAtLeast(320)
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidthDp)
    }
}
