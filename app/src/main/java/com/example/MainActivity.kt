package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.repository.AiAssistantRepository
import com.example.data.repository.BrokerRepository
import com.example.data.repository.EmailDeliveryRepository
import com.example.data.repository.EnterpriseOpsRepository
import com.example.data.repository.MarketDataRepository
import com.example.data.repository.UserPreferencesStore
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.screens.AccountAndAdminScreen
import com.example.ui.screens.AssetDetailScreen
import com.example.ui.screens.MarketsScreen
import com.example.ui.screens.NotificationCentreModal
import com.example.ui.screens.OrdersHistoryScreen
import com.example.ui.screens.PinLockOverlay
import com.example.ui.screens.PortfolioScreen
import com.example.ui.theme.ApexTradeTheme
import com.example.ui.theme.EmeraldAlpha
import com.example.ui.theme.EmeraldAlphaDim
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.viewmodel.BrokerViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        setContent {
            ApexTradeTheme {
                ApexTradeRoot()
            }
        }
    }
}

@Composable
private fun ApexTradeRoot(auth: FirebaseAuth = Firebase.auth) {
    var currentUser by remember { mutableStateOf(auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            currentUser = firebaseAuth.currentUser
        }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = auth.currentUser }
        )
    } else {
        key(user.uid) {
            ApexTradeMainShell(
                currentUserId = user.uid,
                displayName = user.displayName ?: user.email?.substringBefore("@") ?: "Global Trader",
                email = user.email ?: "",
                photoUrl = user.photoUrl?.toString()
            )
        }
    }
}

private enum class BrokerTab(
    val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    MARKETS(R.string.nav_markets, Icons.AutoMirrored.Filled.ShowChart, Icons.AutoMirrored.Outlined.ShowChart),
    PORTFOLIO(R.string.nav_portfolio, Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    ORDERS(R.string.nav_trades, Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    ACCOUNT(R.string.nav_account, Icons.Filled.ManageAccounts, Icons.Outlined.ManageAccounts)
}

@Composable
private fun ApexTradeMainShell(
    currentUserId: String,
    displayName: String,
    email: String,
    photoUrl: String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: BrokerViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val emailRepo = EmailDeliveryRepository(db)
                val opsRepo = EnterpriseOpsRepository(db, emailRepo = emailRepo)
                val brokerRepo = BrokerRepository(db, emailRepo = emailRepo, opsRepo = opsRepo)
                BrokerViewModel(
                    brokerRepository = brokerRepo,
                    marketRepository = MarketDataRepository(),
                    opsRepository = opsRepo,
                    emailRepository = emailRepo,
                    aiRepository = AiAssistantRepository(),
                    prefsStore = UserPreferencesStore(app),
                    currentUserId = currentUserId,
                    displayName = displayName,
                    email = email,
                    photoUrl = photoUrl
                )
            }
        }
    )

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val chartBundle by viewModel.selectedAssetChart.collectAsStateWithLifecycle()
    val isLoadingChart by viewModel.isLoadingChart.collectAsStateWithLifecycle()

    var currentTab by remember { mutableIntStateOf(0) }
    var selectedSymbol by remember { mutableStateOf<String?>(null) }
    var showNotificationsSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.bannerMessage, uiState.errorMessage) {
        val msg = uiState.errorMessage ?: uiState.bannerMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    if (uiState.localSecurityState.pinEnabled && !uiState.isPinUnlocked) {
        PinLockOverlay(onVerifyPin = viewModel::verifySecurityPin)
        return
    }

    val selectedAsset = selectedSymbol?.let { sym ->
        uiState.marketAssets.firstOrNull { it.symbol == sym }
    }

    Scaffold(
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (selectedAsset == null) {
                NavigationBar(
                    containerColor = ObsidianSurface,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    BrokerTab.entries.forEachIndexed { idx, tab ->
                        val selected = currentTab == idx
                        NavigationBarItem(
                            selected = selected,
                            onClick = { currentTab = idx },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = stringResource(tab.titleRes)
                                )
                            },
                            label = {
                                Text(
                                    text = stringResource(tab.titleRes),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = EmeraldAlpha,
                                selectedTextColor = EmeraldAlpha,
                                indicatorColor = EmeraldAlphaDim,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (selectedAsset != null) {
                val holding = uiState.portfolioSummary.enrichedHoldings
                    .firstOrNull { it.holding.symbol == selectedAsset.symbol }
                    ?.holding
                AssetDetailScreen(
                    asset = selectedAsset,
                    isWatched = selectedAsset.symbol in uiState.watchlistSymbols,
                    isProMode = uiState.isProMode,
                    cashBalanceUsd = uiState.portfolioSummary.cashBalanceUsd,
                    holding = holding,
                    brokerConfig = uiState.brokerConfig,
                    chartBundle = chartBundle,
                    isLoadingChart = isLoadingChart,
                    isSubmitting = uiState.isSubmittingAction,
                    onLoadChart = { tf -> viewModel.loadChartForAsset(selectedAsset, tf) },
                    onToggleWatchlist = { viewModel.toggleWatchlist(selectedAsset) },
                    onToggleTraderMode = { viewModel.toggleTraderMode() },
                    onExecuteOrder = { side, orderType, qty, price, triggerPrice ->
                        viewModel.executeTrade(selectedAsset, side, orderType, qty, price, triggerPrice) { }
                    },
                    onBack = { selectedSymbol = null }
                )
            } else {
                when (BrokerTab.entries[currentTab]) {
                    BrokerTab.MARKETS -> MarketsScreen(
                        uiState = uiState,
                        onSelectRegion = viewModel::selectRegion,
                        onSelectAssetClass = viewModel::selectAssetClass,
                        onSearchChange = viewModel::updateSearchQuery,
                        onToggleWatchlistOnly = viewModel::toggleWatchlistOnly,
                        onToggleWatchlistAsset = viewModel::toggleWatchlist,
                        onToggleTraderMode = viewModel::toggleTraderMode,
                        onRefreshMarkets = viewModel::refreshMarkets,
                        onOpenNotifications = { showNotificationsSheet = true },
                        onAssetClick = { asset -> selectedSymbol = asset.symbol }
                    )
                    BrokerTab.PORTFOLIO -> PortfolioScreen(
                        uiState = uiState,
                        onSubmitKyc = viewModel::submitKyc,
                        onSubmitDeposit = viewModel::submitDepositProof,
                        onSubmitWithdrawal = viewModel::submitWithdrawalRequest,
                        onAssetClick = { asset -> selectedSymbol = asset.symbol },
                        onExploreMarkets = { currentTab = 0 },
                        onOpenNotifications = { showNotificationsSheet = true },
                        onExportStatement = {
                            val csv = viewModel.buildStatementCsvText()
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_SUBJECT, "ApexTrade Account Statement")
                                putExtra(Intent.EXTRA_TEXT, csv)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Export Account Statement (CSV)"))
                        }
                    )
                    BrokerTab.ORDERS -> OrdersHistoryScreen(
                        trades = uiState.userTrades,
                        ledgerEntries = uiState.userLedgerEntries,
                        onCancelPendingOrder = viewModel::cancelPendingOrder
                    )
                    BrokerTab.ACCOUNT -> AccountAndAdminScreen(
                        uiState = uiState,
                        onToggleTraderMode = viewModel::toggleTraderMode,
                        onOpenNotifications = { showNotificationsSheet = true },
                        onEnablePin = viewModel::enableSecurityPin,
                        onDisablePin = viewModel::disableSecurityPin,
                        onRevokeSession = viewModel::revokeSession,
                        onUpdateNotifPrefs = viewModel::updateNotificationPrefs,
                        onApplyReferral = viewModel::applyReferralCode,
                        onCreateTicket = viewModel::createSupportTicket,
                        onAskCustomerAi = viewModel::askCustomerAi,
                        onUnlockAdmin = viewModel::unlockAdmin,
                        onLockAdmin = viewModel::lockAdmin,
                        onAdminReviewKyc = viewModel::adminReviewKyc,
                        onAdminReviewDeposit = viewModel::adminReviewDeposit,
                        onAdminProcessWithdrawal = viewModel::adminProcessWithdrawal,
                        onAdminDecideMakerChecker = viewModel::adminDecideMakerChecker,
                        onAdminUpdateUser = viewModel::adminUpdateUser,
                        onAdminSaveConfig = viewModel::adminSaveConfig,
                        onAdminCancelPendingTrade = viewModel::cancelPendingOrder,
                        onAdminResolveAml = viewModel::adminResolveAmlAlert,
                        onAdminAssignStaffRole = viewModel::adminAssignStaffRole,
                        onAdminReplyTicket = viewModel::adminReplyTicket,
                        onAdminPublishAnnouncement = viewModel::adminPublishAnnouncement,
                        onAdminDeactivateAnnouncement = viewModel::adminDeactivateAnnouncement,
                        onAdminSaveEmailTemplate = viewModel::adminSaveEmailTemplate,
                        onAdminRetryEmailJob = viewModel::adminRetryEmailJob,
                        onAdminSendTestEmail = viewModel::adminSendTestEmail,
                        onAdminAddSuppression = viewModel::adminAddSuppression,
                        onAdminRemoveSuppression = viewModel::adminRemoveSuppression,
                        onAskAdminAi = viewModel::askAdminAi,
                        onSignOut = {
                            viewModel.lockAdmin()
                            signOut(credentialManager, scope)
                        }
                    )
                }
            }
        }
    }

    if (showNotificationsSheet) {
        NotificationCentreModal(
            notifications = uiState.userNotifications,
            onMarkRead = viewModel::markNotificationRead,
            onMarkAllRead = viewModel::markAllNotificationsRead,
            onArchive = viewModel::archiveNotification,
            onDismiss = { showNotificationsSheet = false }
        )
    }
}
