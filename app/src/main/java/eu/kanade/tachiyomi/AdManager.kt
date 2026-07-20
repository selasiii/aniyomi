package eu.kanade.tachiyomi

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import java.util.Date

object AdManager {
    private const val AD_UNIT_ID = "ca-app-pub-5950316238873760/8036316380"
    private const val BANNER_AD_UNIT_ID = "ca-app-pub-5950316238873760/1481295135"
    private const val APP_OPEN_AD_UNIT_ID = "ca-app-pub-5950316238873760/9892624851"

    // Interstitial state
    private var interstitialAd: InterstitialAd? = null
    private var isAdLoading = false

    // App Open Ad state
    private var appOpenAd: AppOpenAd? = null
    private var isShowingAd = false
    private var isAppOpenAdLoading = false
    private var loadTime: Long = 0

    fun init(context: Context) {
        try {
            MobileAds.initialize(context) {}
            loadInterstitial(context)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to initialize MobileAds" }
        }
    }

    fun loadInterstitial(context: Context) {
        if (interstitialAd != null || isAdLoading) return

        isAdLoading = true
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context,
            AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isAdLoading = false
                    logcat(LogPriority.INFO) { "AdMob Interstitial Ad Loaded successfully." }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    interstitialAd = null
                    isAdLoading = false
                    logcat(LogPriority.ERROR) { "AdMob Interstitial Ad Failed to Load: ${loadAdError.message}" }
                }
            }
        )
    }

    fun showInterstitial(activity: Activity, onAdClosed: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) {
            onAdClosed()
            return
        }
        val ad = interstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitialAd = null
                    isShowingAd = false
                    onAdClosed()
                    loadInterstitial(activity.applicationContext)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    interstitialAd = null
                    isShowingAd = false
                    onAdClosed()
                    loadInterstitial(activity.applicationContext)
                }

                override fun onAdShowedFullScreenContent() {
                    isShowingAd = true
                }
            }
            ad.show(activity)
        } else {
            onAdClosed()
            loadInterstitial(activity.applicationContext)
        }
    }

    // ── App Open Ad ──────────────────────────────────────────────────────────

    private fun isAppOpenAdExpired(): Boolean {
        val fourHoursMillis = 4 * 3600_000L
        return Date().time - loadTime > fourHoursMillis
    }

    fun loadAppOpenAd(context: Context) {
        if (appOpenAd != null || isAppOpenAdLoading) return
        isAppOpenAdLoading = true
        AppOpenAd.load(
            context,
            APP_OPEN_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    loadTime = Date().time
                    isAppOpenAdLoading = false
                    logcat(LogPriority.INFO) { "App Open Ad loaded successfully." }
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isAppOpenAdLoading = false
                    logcat(LogPriority.ERROR) { "App Open Ad failed to load: ${loadAdError.message}" }
                }
            },
        )
    }

    fun showAppOpenAdIfAvailable(activity: Activity) {
        if (isShowingAd) {
            logcat(LogPriority.INFO) { "App Open Ad skipped — another ad is showing." }
            return
        }
        if (activity.isFinishing || activity.isDestroyed) return

        val ad = appOpenAd
        if (ad == null || isAppOpenAdExpired()) {
            logcat(LogPriority.INFO) { "App Open Ad not ready, loading a new one." }
            loadAppOpenAd(activity.applicationContext)
            return
        }

        isShowingAd = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                logcat(LogPriority.INFO) { "App Open Ad showed successfully." }
            }

            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowingAd = false
                loadAppOpenAd(activity.applicationContext)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                appOpenAd = null
                isShowingAd = false
                logcat(LogPriority.ERROR) { "App Open Ad failed to show: ${adError.message}" }
                loadAppOpenAd(activity.applicationContext)
            }
        }
        ad.show(activity)
    }

    @Composable
    fun BannerAdView(modifier: Modifier = Modifier) {
        AndroidView(
            modifier = modifier,
            factory = { context ->
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = BANNER_AD_UNIT_ID
                    loadAd(AdRequest.Builder().build())
                }
            },
            update = { adView ->
                adView.loadAd(AdRequest.Builder().build())
            },
        )
    }
}
