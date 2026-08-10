package com.example

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.adsImp.manager.AdsManager
import com.example.adsImp.manager.AppOpenManager
import com.example.adsImp.manager.InterstitialManager
import com.example.adsImp.manager.RewardedManager
import com.example.adsImp.manager.RewardedInterstitialManager
import com.example.adsImp.ui.AdStartingDialog
import com.example.adsImp.ui.AdaptiveBannerAdView
import com.example.adsImp.ui.BannerAdView
import com.example.adsImp.ui.CollapsibleBannerAdView
import com.example.adsImp.ui.NativeCollapsibleAdView
import com.example.adsImp.ui.NativeMediumAdView
import com.example.adsImp.ui.NativeSmallAdView
import com.example.ui.theme.MyApplicationTheme
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
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
                        modifier = Modifier.padding(innerPadding)
                    )
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

        // Drives the brief full-screen "Ad starting…" overlay shown before the interstitial.
        var showInterstitialLoader by remember { mutableStateOf(false) }
        if (showInterstitialLoader) {
            AdStartingDialog(
                isAdReady = { interstitialManager.isAdAvailable() },
                onReadyToShow = {
                    showInterstitialLoader = false
                    interstitialManager.showAd(this@MainActivity) {
                        Toast.makeText(context, "Interstitial Closed.", Toast.LENGTH_SHORT).show()
                    }
                }
            )
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
                    title = "Interstitial",
                    isLoaded = isInterstitialAvailable,
                    isLoading = !isInterstitialAvailable && isInitialized,
                    expiryLabel = "High revenue",
                    onClick = {
                        // Only proceed when enabled (remote-config flag) AND online; else nothing shows.
                        if (interstitialManager.isEnabled && isOnline) {
                            interstitialManager.loadAd()
                            showInterstitialLoader = true
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("interstitial_ad_card")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
                        when (currentBannerTab) {
                            0 -> AdaptiveBannerAdView(adsManager = adsManager)
                            1 -> CollapsibleBannerAdView(adsManager = adsManager, direction = "bottom")
                            2 -> BannerAdView(adsManager = adsManager)
                        }
                    }
                }
            }

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
