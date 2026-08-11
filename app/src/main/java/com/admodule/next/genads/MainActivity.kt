package com.admodule.next.genads

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.admodule.next.genads.adsImp.manager.AdsManager
import com.admodule.next.genads.adsImp.manager.AppOpenManager
import com.admodule.next.genads.adsImp.manager.ConsentManager
import com.admodule.next.genads.adsImp.manager.InterstitialManager
import com.admodule.next.genads.adsImp.manager.RewardedManager
import com.admodule.next.genads.adsImp.manager.RewardedInterstitialManager
import com.admodule.next.genads.adsImp.ui.AdLoadingDialog
import com.admodule.next.genads.adsImp.ui.AdStartingDialog
import com.admodule.next.genads.adsImp.ui.AdaptiveBannerAdView
import com.admodule.next.genads.adsImp.ui.BannerAdView
import com.admodule.next.genads.adsImp.ui.CollapsibleBannerAdView
import com.admodule.next.genads.adsImp.ui.NativeCollapsibleAdView
import com.admodule.next.genads.adsImp.ui.NativeMediumAdView
import com.admodule.next.genads.adsImp.ui.NativeSmallAdView
import com.admodule.next.genads.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val adsManager: AdsManager
        get() = BaseApplication.instance.adsManager

    private val appOpenManager: AppOpenManager
        get() = BaseApplication.instance.appOpenManager

    private val interstitialManager: InterstitialManager
        get() = BaseApplication.instance.interstitialManager

    private val rewardedManager: RewardedManager
        get() = BaseApplication.instance.rewardedManager

    private val rewardedInterstitialManager: RewardedInterstitialManager
        get() = BaseApplication.instance.rewardedInterstitialManager

    private val consentManager: ConsentManager
        get() = BaseApplication.instance.consentManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                // Top-level screen switch. Starts on the dashboard; the interstitial's
                // onDismiss advances us to "next".
                var currentScreen by remember { mutableStateOf("dashboard") }

                // Surfaces a re-openable "Privacy options" button once consent resolves (EEA users).
                var privacyOptionsRequired by remember { mutableStateOf(false) }

                // GDPR consent gate — runs once on entry. Ads are initialized ONLY after the UMP flow
                // resolves and canRequestAds is true. This is the "call it from the main screen" hook;
                // the identical call works from a dedicated splash Activity's onCreate().
                LaunchedEffect(Unit) {
                    consentManager.gatherConsent(this@MainActivity) { _ ->
                        privacyOptionsRequired = consentManager.isPrivacyOptionsRequired
                        if (consentManager.canRequestAds) {
                            adsManager.initialize()
                        }
                    }
                }

                when (currentScreen) {
                    "next" -> NextScreen(onBack = { currentScreen = "dashboard" })

                    else -> {
                        var currentSection by remember { mutableStateOf("status") }
                        val scrollState = rememberScrollState()
                        val coroutineScope = rememberCoroutineScope()

                        Scaffold(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFFF7F9FF)),
                            topBar = {
                                CustomTopBar()
                            },
                            bottomBar = {
                                BottomNavigationBar(
                                    currentSection = currentSection,
                                    onSectionSelected = { section ->
                                        currentSection = section
                                        coroutineScope.launch {
                                            when (section) {
                                                "status" -> scrollState.animateScrollTo(0)
                                                "units" -> scrollState.animateScrollTo(420)
                                                "config" -> scrollState.animateScrollTo(2000)
                                            }
                                        }
                                    }
                                )
                            }
                        ) { innerPadding ->
                            DashboardScreen(
                                scrollState = scrollState,
                                onNavigateNext = { currentScreen = "next" },
                                privacyOptionsRequired = privacyOptionsRequired,
                                onPrivacyOptions = { consentManager.showPrivacyOptionsForm(this@MainActivity) },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun CustomTopBar() {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .border(width = 1.dp, color = Color(0xFFE1E2EC))
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "AdsModule Architect",
                    color = Color(0xFF005FB0),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFF4CAF50), CircleShape)
                    )
                    Text(
                        text = "SDK V23.6.0 ACTIVE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = Color(0xFF44474E),
                        letterSpacing = 1.5.sp
                    )
                }
            }

            // Initials Avatar "SA" matching mockup exactly
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFFD1E1FF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "SA",
                    color = Color(0xFF005FB0),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }

    @Composable
    fun DashboardScreen(
        scrollState: ScrollState,
        onNavigateNext: () -> Unit,
        privacyOptionsRequired: Boolean,
        onPrivacyOptions: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        val context = LocalContext.current
        val isInitialized by adsManager.isInitialized.collectAsStateWithLifecycle()
        val isOnline by adsManager.networkMonitor.isOnline.collectAsStateWithLifecycle(initialValue = true)

        // Local state to simulate virtual user loyalty point system
        var virtualCoins by remember { mutableStateOf(100) }
        var currentBannerTab by remember { mutableStateOf(0) } // 0: Adaptive, 1: Collapsible, 2: Standard
        var currentNativeTab by remember { mutableStateOf(0) } // 0: Small, 1: Medium

        val isAppOpenAvailable by appOpenManager.isAdLoaded.collectAsStateWithLifecycle()
        val isInterstitialAvailable by interstitialManager.isAdLoaded.collectAsStateWithLifecycle()
        val isRewardedAvailable by rewardedManager.isAdLoaded.collectAsStateWithLifecycle()
        val isRewardedInterstitialAvailable by rewardedInterstitialManager.isAdLoaded.collectAsStateWithLifecycle()

        // PRELOAD strategy loader: relies on the cached interstitial (preloaded at app start / via the
        // Preload button). Shows instantly if cached, otherwise waits briefly for an in-flight preload.
        var showInterstitialLoader by remember { mutableStateOf(false) }
        if (showInterstitialLoader) {
            AdStartingDialog(
                isAdReady = { interstitialManager.isAdAvailable() },
                onReadyToShow = {
                    showInterstitialLoader = false
                    interstitialManager.showAd(this@MainActivity) {
                        // Ad closed → advance to the next screen.
                        onNavigateNext()
                    }
                }
            )
        }

        // ON-DEMAND strategy loader: loads a FRESH interstitial at click time (independent of the
        // preload cache) and displays it as soon as it is ready. Visibility is driven by the
        // manager's onLoading callback.
        var showInterstitialOnDemandLoader by remember { mutableStateOf(false) }
        if (showInterstitialOnDemandLoader) {
            AdLoadingDialog(message = "Loading ad")
        }

        // Same overlay, shown before the App Open ad.
        var showAppOpenLoader by remember { mutableStateOf(false) }
        if (showAppOpenLoader) {
            AdStartingDialog(
                isAdReady = { appOpenManager.isAdAvailable() },
                onReadyToShow = {
                    showAppOpenLoader = false
                    appOpenManager.showAd(this@MainActivity) {
                        Toast.makeText(context, "App Open Closed.", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // Same overlay, shown before the Rewarded ad.
        var showRewardedLoader by remember { mutableStateOf(false) }
        if (showRewardedLoader) {
            AdStartingDialog(
                isAdReady = { rewardedManager.isAdAvailable() },
                onReadyToShow = {
                    showRewardedLoader = false
                    rewardedManager.showAd(
                        activity = this@MainActivity,
                        onUserEarnedReward = { label, amount ->
                            virtualCoins += amount
                            Toast.makeText(context, "Earned: $amount $label!", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            )
        }

        // Same overlay, shown before the Rewarded Interstitial ad.
        var showRewardedInterstitialLoader by remember { mutableStateOf(false) }
        if (showRewardedInterstitialLoader) {
            AdStartingDialog(
                isAdReady = { rewardedInterstitialManager.isAdAvailable() },
                onReadyToShow = {
                    showRewardedInterstitialLoader = false
                    rewardedInterstitialManager.showAd(
                        activity = this@MainActivity,
                        onUserEarnedReward = { label, amount ->
                            virtualCoins += amount
                            Toast.makeText(context, "Earned: $amount $label!", Toast.LENGTH_LONG).show()
                        }
                    )
                }
            )
        }

        Column(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFFF7F9FF))
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status & Currency Summary Block (The Header area equivalent)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFFE1E2EC)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Integration Status",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1C1B1F)
                            )
                            Text(
                                text = "Client state & point ledger",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF44474E)
                            )
                        }

                        // Point ledger pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .background(Color(0xFFD1E1FF), CircleShape)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Points Icon",
                                tint = Color(0xFF005FB0),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "$virtualCoins Pts",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF005FB0),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0xFFE1E2EC))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatusBadge(
                            label = "GMA SDK",
                            isActive = isInitialized,
                            activeIcon = Icons.Default.Done,
                            inactiveIcon = Icons.Default.Refresh,
                            modifier = Modifier.weight(1f)
                        )
                        StatusBadge(
                            label = "Internet",
                            isActive = isOnline,
                            activeIcon = Icons.Default.Check,
                            inactiveIcon = Icons.Default.Close,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Quick Operations Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        appOpenManager.loadAd()
                        interstitialManager.loadAd()
                        rewardedManager.loadAd()
                        rewardedInterstitialManager.loadAd()
                        Toast.makeText(context, "Loaded ad cache updates requested.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).testTag("preload_cache_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF005FB0)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Preload", modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Preload Cache", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // GDPR "Privacy options" entry point — only shown when UMP requires it (EEA users),
                // letting them re-open the consent form to change their choices.
                if (privacyOptionsRequired) {
                    OutlinedButton(
                        onClick = onPrivacyOptions,
                        modifier = Modifier.weight(1f).testTag("privacy_options_button"),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text("Privacy Options", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Section Headline: Full-Screen Ad Units Area (The Professional Polish card grid)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FULL-SCREEN FORMTS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF44474E),
                    letterSpacing = 1.5.sp
                )
            }

            // The two interstitial strategies, side by side and handled separately.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // PRELOAD: shows the interstitial that was cached ahead of time (instant).
                FullScreenAdCard(
                    title = "Inter (Preload)",
                    isLoaded = isInterstitialAvailable,
                    isLoading = !isInterstitialAvailable && isInitialized,
                    expiryLabel = "Instant · cached",
                    onClick = {
                        // Only proceed when enabled (remote-config flag) AND online; else nothing shows.
                        if (interstitialManager.isEnabled && isOnline) {
                            interstitialManager.loadAd()
                            showInterstitialLoader = true
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("interstitial_ad_card")
                )

                // ON-DEMAND: loads a fresh interstitial at tap time (independent of the cache).
                FullScreenAdCard(
                    title = "Inter (On-Demand)",
                    isLoaded = isInitialized,
                    isLoading = false,
                    expiryLabel = "Loads on tap",
                    onClick = {
                        // Only proceed when enabled (remote-config flag) AND online; else nothing shows.
                        if (interstitialManager.isEnabled && isOnline) {
                            showInterstitialOnDemandLoader = true
                            interstitialManager.loadAndShow(
                                activity = this@MainActivity,
                                onLoading = { loading -> showInterstitialOnDemandLoader = loading },
                                onDismiss = { onNavigateNext() }
                            )
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("interstitial_ondemand_ad_card")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FullScreenAdCard(
                    title = "App Open Ad",
                    isLoaded = isAppOpenAvailable,
                    isLoading = !isAppOpenAvailable && isInitialized,
                    expiryLabel = "4m exp.",
                    onClick = {
                        // Only proceed when enabled (remote-config flag) AND online; else nothing shows.
                        if (appOpenManager.isEnabled && isOnline) {
                            appOpenManager.loadAd()
                            showAppOpenLoader = true
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("app_open_ad_card")
                )

                FullScreenAdCard(
                    title = "Rewarded Ad",
                    isLoaded = isRewardedAvailable,
                    isLoading = !isRewardedAvailable && isInitialized,
                    expiryLabel = "Points value",
                    onClick = {
                        // Only proceed when enabled (remote-config flag) AND online; else nothing shows.
                        if (rewardedManager.isEnabled && isOnline) {
                            rewardedManager.loadAd()
                            showRewardedLoader = true
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("rewarded_ad_card")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FullScreenAdCard(
                    title = "Rewarded Inter",
                    isLoaded = isRewardedInterstitialAvailable,
                    isLoading = !isRewardedInterstitialAvailable && isInitialized,
                    expiryLabel = "Transition model",
                    onClick = {
                        // Only proceed when enabled (remote-config flag) AND online; else nothing shows.
                        if (rewardedInterstitialManager.isEnabled && isOnline) {
                            rewardedInterstitialManager.loadAd()
                            showRewardedInterstitialLoader = true
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("rewarded_interstitial_ad_card")
                )

                // Keeps the final (odd) card half-width to match the rest of the grid.
                Spacer(modifier = Modifier.weight(1f))
            }

            // Section Headline: Native showcase matching mockup classes
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "NATIVE AD SHOWCASE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF44474E),
                    letterSpacing = 1.5.sp
                )
            }

            NativeShowcaseCard(
                title = when (currentNativeTab) {
                    0 -> "Native Small Preview"
                    1 -> "Native Medium Preview"
                    else -> "Native Collapsible Preview"
                },
                selectedTab = currentNativeTab,
                onTabSelected = { currentNativeTab = it }
            ) {
                // Do not build/load ad views until the SDK is initialized (which only happens after
                // GDPR consent is resolved). Loading before init crashes the Next Gen SDK.
                if (isInitialized) {
                    when (currentNativeTab) {
                        0 -> NativeSmallAdView(
                            adsManager = adsManager,
                            modifier = Modifier.testTag("native_small_ad_view")
                        )
                        1 -> NativeMediumAdView(
                            adsManager = adsManager,
                            modifier = Modifier.testTag("native_medium_ad_view")
                        )
                        else -> NativeCollapsibleAdView(
                            adsManager = adsManager,
                            modifier = Modifier.testTag("native_collapsible_ad_view")
                        )
                    }
                } else {
                    AdConsentGatePlaceholder()
                }
            }

            // Section Headline: Banners Layout
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BANNER AD VIEWS SHOWCASE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF44474E),
                    letterSpacing = 1.5.sp
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color(0xFFE1E2EC)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TabRow(
                        selectedTabIndex = currentBannerTab,
                        containerColor = Color(0xFFF0F4FF),
                        contentColor = Color(0xFF005FB0),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Tab(
                            selected = currentBannerTab == 0,
                            onClick = { currentBannerTab = 0 },
                            text = { Text("Adaptive", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = currentBannerTab == 1,
                            onClick = { currentBannerTab = 1 },
                            text = { Text("Collapsible", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = currentBannerTab == 2,
                            onClick = { currentBannerTab = 2 },
                            text = { Text("Standard", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp)
                            .testTag("banner_showcase_container"),
                        contentAlignment = Alignment.Center
                    ) {
                        // Same init gate as the native showcase — never load a banner before the SDK
                        // is initialized (post-consent), or the Next Gen SDK throws.
                        if (isInitialized) {
                            when (currentBannerTab) {
                                0 -> AdaptiveBannerAdView(adsManager = adsManager)
                                1 -> CollapsibleBannerAdView(adsManager = adsManager, direction = "bottom")
                                2 -> BannerAdView(adsManager = adsManager)
                            }
                        } else {
                            AdConsentGatePlaceholder()
                        }
                    }
                }
            }

        }
    }

    @Composable
    fun NextScreen(onBack: () -> Unit) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF7F9FF)),
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .border(width = 1.dp, color = Color(0xFFE1E2EC))
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(onClick = onBack)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "←",
                            color = Color(0xFF005FB0),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                    Text(
                        text = "Next Screen",
                        color = Color(0xFF005FB0),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF7F9FF))
                    .padding(innerPadding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(Color(0xFFD1E1FF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF005FB0),
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    text = "You're on the next screen",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1B1F),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "The interstitial ad was closed, so we brought you here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF44474E),
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF005FB0)),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text("Back to Dashboard", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    @Composable
    fun AdConsentGatePlaceholder(modifier: Modifier = Modifier) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Waiting for consent…",
                fontSize = 12.sp,
                color = Color(0xFF90A4AE),
                fontWeight = FontWeight.Medium
            )
        }
    }

    @Composable
    fun StatusBadge(
        label: String,
        isActive: Boolean,
        activeIcon: ImageVector,
        inactiveIcon: ImageVector,
        modifier: Modifier = Modifier
    ) {
        val color = if (isActive) Color(0xFF4CAF50) else Color(0xFFF44336)
        val containerColor = color.copy(alpha = 0.08f)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = modifier
                .background(containerColor, RoundedCornerShape(12.dp))
                .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Icon(
                imageVector = if (isActive) activeIcon else inactiveIcon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$label: ${if (isActive) "Active" else "Offline"}",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }

    @Composable
    fun FullScreenAdCard(
        title: String,
        isLoaded: Boolean,
        isLoading: Boolean = false,
        expiryLabel: String? = null,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
    ) {
        Card(
            modifier = modifier
                .height(108.dp)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE1E2EC)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF44474E),
                    letterSpacing = 0.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLoaded) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFE8F5E9), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFC8E6C9), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                    text = "Ready",
                                    color = Color(0xFF2E7D32),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (isLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFD1E1FF), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                        text = "Preloading",
                                        color = Color(0xFF005FB0),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                )
                            }
                            CircularProgressIndicator(
                                modifier = Modifier.size(10.dp),
                                color = Color(0xFF005FB0),
                                strokeWidth = 1.5.dp
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFFEBEE), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFFFFCDD2), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                    text = "Cached",
                                    color = Color(0xFFC62828),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (expiryLabel != null) {
                        Text(
                            text = expiryLabel,
                            fontSize = 10.sp,
                            color = Color(0xFF90A4AE),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun NativeShowcaseCard(
        title: String,
        selectedTab: Int,
        onTabSelected: (Int) -> Unit,
        content: @Composable () -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE1E2EC)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Mock-Up header class elements styled cleanly with Material equivalents
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF0F4FF))
                        .border(width = 1.dp, color = Color(0xFFE1E2EC))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF005FB0),
                        letterSpacing = 1.sp
                    )
                    Box(
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0xFFE1E2EC), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "AD",
                            fontSize = 9.sp,
                            color = Color(0xFF44474E),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Inner Tab switcher representing mock navigation
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.White,
                    contentColor = Color(0xFF005FB0),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { onTabSelected(0) },
                        text = { Text("Native Small", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { onTabSelected(1) },
                        text = { Text("Native Medium", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { onTabSelected(2) },
                        text = { Text("Collapsible", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }

                // Real integrated View
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    content()
                }
            }
        }
    }

    @Composable
    fun BottomNavigationBar(
        currentSection: String,
        onSectionSelected: (String) -> Unit
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(Color.White)
                .border(width = 1.dp, color = Color(0xFFE1E2EC))
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val navItems = listOf(
                NavItem("Status", "📊", "status"),
                NavItem("Units", "🧩", "units"),
                NavItem("Config", "⚙️", "config")
            )

            navItems.forEach { item ->
                val isSelected = currentSection == item.key
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSectionSelected(item.key) }
                        .padding(vertical = 4.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Pill background accent indicator
                    Box(
                        modifier = Modifier
                            .size(width = 56.dp, height = 30.dp)
                            .background(
                                color = if (isSelected) Color(0xFFD1E1FF) else Color.Transparent,
                                shape = RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.icon,
                            fontSize = 18.sp,
                            modifier = Modifier.alpha(if (isSelected) 1f else 0.4f)
                        )
                    }
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (isSelected) Color(0xFF1C1B1F) else Color(0xFF1C1B1F).copy(alpha = 0.4f)
                    )
                }
            }
        }
    }

    data class NavItem(val label: String, val icon: String, val key: String)
}
