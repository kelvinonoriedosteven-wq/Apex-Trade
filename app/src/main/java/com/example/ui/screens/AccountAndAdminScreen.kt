package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.BrokerConfig
import com.example.data.model.EmailJob
import com.example.data.model.EmailTemplate
import com.example.data.model.NotificationPreferences
import com.example.data.model.SupportTicket
import com.example.data.model.UserAccount
import com.example.ui.components.formatQuantity
import com.example.ui.components.formatUsdPrice
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.ChampagneGoldDim
import com.example.ui.theme.CrimsonLoss
import com.example.ui.theme.CrimsonLossDim
import com.example.ui.theme.EmeraldAlpha
import com.example.ui.theme.EmeraldAlphaDim
import com.example.ui.theme.MonoPriceStyle
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.viewmodel.BrokerUiState
import kotlin.math.roundToInt

@Composable
fun AccountAndAdminScreen(
    uiState: BrokerUiState,
    onToggleTraderMode: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onEnablePin: (String) -> Unit = {},
    onDisablePin: () -> Unit = {},
    onRevokeSession: (String) -> Unit = {},
    onUpdateNotifPrefs: (NotificationPreferences) -> Unit = {},
    onApplyReferral: (String) -> Unit = {},
    onCreateTicket: (String, String, String, String, () -> Unit) -> Unit = { _, _, _, _, _ -> },
    onAskCustomerAi: (String) -> Unit = {},
    onUnlockAdmin: (passcode: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onLockAdmin: () -> Unit,
    onAdminReviewKyc: (targetUserId: String, approve: Boolean, reason: String?) -> Unit,
    onAdminReviewDeposit: (depositId: String, approve: Boolean, note: String?) -> Unit,
    onAdminProcessWithdrawal: (withdrawalId: String, markPaid: Boolean, payoutRef: String, note: String?) -> Unit,
    onAdminDecideMakerChecker: (String, Boolean, String?) -> Unit = { _, _, _ -> },
    onAdminUpdateUser: (targetUid: String, status: String, balanceUsd: Double) -> Unit,
    onAdminSaveConfig: (BrokerConfig) -> Unit,
    onAdminCancelPendingTrade: (String) -> Unit = {},
    onAdminResolveAml: (String, String, String) -> Unit = { _, _, _ -> },
    onAdminAssignStaffRole: (UserAccount, String) -> Unit = { _, _ -> },
    onAdminReplyTicket: (SupportTicket, String, String) -> Unit = { _, _, _ -> },
    onAdminPublishAnnouncement: (String, String, String, String) -> Unit = { _, _, _, _ -> },
    onAdminDeactivateAnnouncement: (String) -> Unit = {},
    onAdminSaveEmailTemplate: (EmailTemplate) -> Unit = {},
    onAdminRetryEmailJob: (EmailJob) -> Unit = {},
    onAdminSendTestEmail: (String, String) -> Unit = { _, _ -> },
    onAdminAddSuppression: (String, String) -> Unit = { _, _ -> },
    onAdminRemoveSuppression: (String) -> Unit = {},
    onAskAdminAi: (String) -> Unit = {},
    onSignOut: () -> Unit
) {
    var showAdminLockDialog by remember { mutableStateOf(false) }
    var passcodeInput by remember { mutableStateOf("") }
    var passcodeError by remember { mutableStateOf<String?>(null) }

    if (uiState.isAdminUnlocked) {
        AdminDashboardPanel(
            uiState = uiState,
            onLockAdmin = onLockAdmin,
            onAdminReviewKyc = onAdminReviewKyc,
            onAdminReviewDeposit = onAdminReviewDeposit,
            onAdminProcessWithdrawal = onAdminProcessWithdrawal,
            onAdminDecideMakerChecker = onAdminDecideMakerChecker,
            onAdminUpdateUser = onAdminUpdateUser,
            onAdminSaveConfig = onAdminSaveConfig,
            onAdminCancelPendingTrade = onAdminCancelPendingTrade,
            onAdminResolveAml = onAdminResolveAml,
            onAdminAssignStaffRole = onAdminAssignStaffRole,
            onAdminReplyTicket = onAdminReplyTicket,
            onAdminPublishAnnouncement = onAdminPublishAnnouncement,
            onAdminDeactivateAnnouncement = onAdminDeactivateAnnouncement,
            onAdminSaveEmailTemplate = onAdminSaveEmailTemplate,
            onAdminRetryEmailJob = onAdminRetryEmailJob,
            onAdminSendTestEmail = onAdminSendTestEmail,
            onAdminAddSuppression = onAdminAddSuppression,
            onAdminRemoveSuppression = onAdminRemoveSuppression,
            onAskAdminAi = onAskAdminAi
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Trader Account",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            NotificationBellButton(
                unreadCount = uiState.unreadNotificationCount,
                onClick = onOpenNotifications
            )
        }

        // User Profile Card with Account & KYC Status
        val account = uiState.userAccount
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = ObsidianSurface,
            border = BorderStroke(1.dp, ObsidianOutline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(EmeraldAlphaDim),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = EmeraldAlpha,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account?.displayName ?: "Global Trader",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = account?.email ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val isSuspended = account?.accountStatus == "suspended"
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSuspended) CrimsonLossDim else EmeraldAlphaDim
                        ) {
                            Text(
                                text = if (isSuspended) "SUSPENDED" else "ACTIVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSuspended) CrimsonLoss else EmeraldAlpha,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        val kyc = account?.kycStatus ?: "not_submitted"
                        val kycColor = when (kyc) {
                            "approved" -> EmeraldAlpha
                            "rejected" -> CrimsonLoss
                            else -> ChampagneGold
                        }
                        val kycBg = when (kyc) {
                            "approved" -> EmeraldAlphaDim
                            "rejected" -> CrimsonLossDim
                            else -> ChampagneGoldDim
                        }
                        Surface(shape = RoundedCornerShape(6.dp), color = kycBg) {
                            Text(
                                text = "KYC: ${kyc.replace("_", " ").uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = kycColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // Beginner vs Pro Experience Switch
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = ObsidianSurface,
            border = BorderStroke(1.dp, ObsidianOutline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Pro Trader Terminal",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (uiState.isProMode) {
                            "Enabled: Candlesticks, Moving Averages, Limit Orders & Volume depth."
                        } else {
                            "Disabled: Clean Simple Mode tailored for everyday investors."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = uiState.isProMode,
                    onCheckedChange = { onToggleTraderMode() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianBg,
                        checkedTrackColor = ChampagneGold
                    )
                )
            }
        }

        // Live Fee Schedule Preview
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = ObsidianSurface,
            border = BorderStroke(1.dp, ObsidianOutline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Live Brokerage Commission Schedule",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                FeeRow("Global Stocks", uiState.brokerConfig.stockCommissionBps)
                FeeRow("Global ETFs", uiState.brokerConfig.etfCommissionBps)
                FeeRow("Cryptocurrencies (24/7)", uiState.brokerConfig.cryptoCommissionBps)
                FeeRow("Indices", uiState.brokerConfig.indexCommissionBps)
                FeeRow("Bonds & Fixed Income", uiState.brokerConfig.bondCommissionBps)
                FeeRow("Futures & Commodities", uiState.brokerConfig.futuresCommissionBps)
                FeeRow("Forex Pairs", uiState.brokerConfig.forexCommissionBps)
            }
        }

        CustomerSecurityAndPrefsSection(
            uiState = uiState,
            onEnablePin = onEnablePin,
            onDisablePin = onDisablePin,
            onRevokeSession = onRevokeSession,
            onUpdateNotifPrefs = onUpdateNotifPrefs,
            onApplyReferral = onApplyReferral
        )

        CustomerSupportAndAiSection(
            uiState = uiState,
            onCreateTicket = onCreateTicket,
            onAskCustomerAi = onAskCustomerAi
        )

        // Locked Admin Dashboard Gate
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = ObsidianSurface,
            border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.55f)),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    passcodeInput = ""
                    passcodeError = null
                    showAdminLockDialog = true
                }
                .testTag("open_admin_lock_button")
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ChampagneGoldDim),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked Admin Panel",
                        tint = ChampagneGold
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Admin Control Dashboard",
                        style = MaterialTheme.typography.titleMedium,
                        color = ChampagneGold
                    )
                    Text(
                        text = "Password-locked panel to approve KYC & deposit proofs, pay withdrawals manually, set deposit wallets, and manage commissions.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        OutlinedButton(
            onClick = onSignOut,
            border = BorderStroke(1.dp, CrimsonLoss.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("sign_out_button")
        ) {
            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = CrimsonLoss)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sign Out", color = CrimsonLoss, fontWeight = FontWeight.SemiBold)
        }
    }

    if (showAdminLockDialog) {
        AlertDialog(
            onDismissRequest = { showAdminLockDialog = false },
            containerColor = ObsidianSurface,
            icon = {
                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(32.dp))
            },
            title = {
                Text("Admin Security Gate", style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Column {
                    Text(
                        text = "Enter the 8-digit Admin Password to unlock real-time brokerage controls.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passcodeInput,
                        onValueChange = {
                            passcodeInput = it
                            passcodeError = null
                        },
                        label = { Text("Admin Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true,
                        isError = passcodeError != null,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ChampagneGold,
                            unfocusedBorderColor = ObsidianOutline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_passcode_input")
                    )
                    if (passcodeError != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = passcodeError ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = CrimsonLoss
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUnlockAdmin(passcodeInput) { ok, err ->
                            if (ok) {
                                showAdminLockDialog = false
                            } else {
                                passcodeError = err ?: "Incorrect Admin Password"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ChampagneGold, contentColor = ObsidianBg),
                    modifier = Modifier.testTag("admin_unlock_button")
                ) {
                    Text("Unlock Panel", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdminLockDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AdminDashboardPanel(
    uiState: BrokerUiState,
    onLockAdmin: () -> Unit,
    onAdminReviewKyc: (String, Boolean, String?) -> Unit,
    onAdminReviewDeposit: (String, Boolean, String?) -> Unit,
    onAdminProcessWithdrawal: (String, Boolean, String, String?) -> Unit,
    onAdminDecideMakerChecker: (String, Boolean, String?) -> Unit,
    onAdminUpdateUser: (String, String, Double) -> Unit,
    onAdminSaveConfig: (BrokerConfig) -> Unit,
    onAdminCancelPendingTrade: (String) -> Unit,
    onAdminResolveAml: (String, String, String) -> Unit,
    onAdminAssignStaffRole: (UserAccount, String) -> Unit,
    onAdminReplyTicket: (SupportTicket, String, String) -> Unit,
    onAdminPublishAnnouncement: (String, String, String, String) -> Unit,
    onAdminDeactivateAnnouncement: (String) -> Unit,
    onAdminSaveEmailTemplate: (EmailTemplate) -> Unit,
    onAdminRetryEmailJob: (EmailJob) -> Unit,
    onAdminSendTestEmail: (String, String) -> Unit,
    onAdminAddSuppression: (String, String) -> Unit,
    onAdminRemoveSuppression: (String) -> Unit,
    onAskAdminAi: (String) -> Unit
) {
    BackHandler(onBack = onLockAdmin)

    var selectedTab by remember { mutableIntStateOf(0) }

    val pendingKycCount = uiState.allKycForAdmin.count { it.status == "pending" }
    val pendingDepositCount = uiState.allDepositsForAdmin.count { it.status == "pending" }
    val pendingWdCount = uiState.allWithdrawalsForAdmin.count { it.status == "pending" }
    val openMcCount = uiState.pendingApprovalsForAdmin.count { it.status == "pending_checker" }
    val openAmlCount = uiState.allAmlAlertsForAdmin.count { it.status == "open" || it.status == "escalated" }
    val openTicketsCount = uiState.allTicketsForAdmin.count { it.status == "open" || it.status == "in_progress" }

    val tabTitles = listOf(
        "KYC ($pendingKycCount)",
        "Deposits ($pendingDepositCount)",
        "Payouts ($pendingWdCount)",
        "Wallets",
        "Rates",
        "Users",
        "Trades",
        "Overview",
        "Approvals ($openMcCount)",
        "Risk/AML ($openAmlCount)",
        "Support ($openTicketsCount)",
        "Broadcasts",
        "Email",
        "Audit",
        "AI Ops"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        // Admin Header
        Surface(
            color = ObsidianSurface,
            border = BorderStroke(1.dp, ChampagneGold.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LockOpen, contentDescription = null, tint = ChampagneGold)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Admin Command Center",
                            style = MaterialTheme.typography.titleLarge,
                            color = ChampagneGold
                        )
                        Text(
                            text = "${uiState.allUsersForAdmin.size} Users • ${pendingKycCount + pendingDepositCount + pendingWdCount + openMcCount} Pending Reviews",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                OutlinedButton(
                    onClick = onLockAdmin,
                    border = BorderStroke(1.dp, ChampagneGold),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("lock_admin_button")
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Lock", color = ChampagneGold)
                }
            }
        }

        // Scrollable Admin Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabTitles.forEachIndexed { idx, title ->
                val active = selectedTab == idx
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (active) ChampagneGold.copy(alpha = 0.18f) else ObsidianSurface,
                    border = BorderStroke(1.dp, if (active) ChampagneGold else ObsidianOutline),
                    modifier = Modifier
                        .clickable { selectedTab = idx }
                        .testTag("admin_tab_$idx")
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) ChampagneGold else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        when (selectedTab) {
            0 -> AdminKycTab(uiState.allKycForAdmin, onAdminReviewKyc)
            1 -> AdminDepositsTab(uiState.allDepositsForAdmin, onAdminReviewDeposit)
            2 -> AdminWithdrawalsTab(uiState.allWithdrawalsForAdmin, onAdminProcessWithdrawal)
            3 -> AdminWalletsTab(uiState.brokerConfig, onAdminSaveConfig)
            4 -> AdminCommissionTab(uiState, onAdminSaveConfig)
            5 -> AdminUsersTab(uiState.allUsersForAdmin, onAdminUpdateUser)
            6 -> AdminTradesMonitorTab(uiState, onAdminCancelPendingTrade)
            7 -> AdminOverviewTab(uiState, onAdminAssignStaffRole)
            8 -> AdminMakerCheckerTab(uiState, onAdminDecideMakerChecker)
            9 -> AdminAmlRiskTab(uiState, onAdminResolveAml)
            10 -> AdminSupportDeskTab(uiState, onAdminReplyTicket)
            11 -> AdminAnnouncementsTab(uiState, onAdminPublishAnnouncement, onAdminDeactivateAnnouncement)
            12 -> AdminEmailCenterTab(uiState, onAdminSaveEmailTemplate, onAdminRetryEmailJob, onAdminSendTestEmail, onAdminAddSuppression, onAdminRemoveSuppression)
            13 -> AdminAuditLogsTab(uiState)
            else -> AdminAiCopilotTab(uiState, onAskAdminAi)
        }
    }
}

@Composable
private fun AdminCommissionTab(
    uiState: BrokerUiState,
    onSaveConfig: (BrokerConfig) -> Unit
) {
    val cfg = uiState.brokerConfig
    var stockBps by remember(cfg.stockCommissionBps) { mutableDoubleStateOf(cfg.stockCommissionBps) }
    var etfBps by remember(cfg.etfCommissionBps) { mutableDoubleStateOf(cfg.etfCommissionBps) }
    var cryptoBps by remember(cfg.cryptoCommissionBps) { mutableDoubleStateOf(cfg.cryptoCommissionBps) }
    var forexBps by remember(cfg.forexCommissionBps) { mutableDoubleStateOf(cfg.forexCommissionBps) }
    var commodityBps by remember(cfg.commodityCommissionBps) { mutableDoubleStateOf(cfg.commodityCommissionBps) }
    var indexBps by remember(cfg.indexCommissionBps) { mutableDoubleStateOf(cfg.indexCommissionBps) }
    var bondBps by remember(cfg.bondCommissionBps) { mutableDoubleStateOf(cfg.bondCommissionBps) }
    var futuresBps by remember(cfg.futuresCommissionBps) { mutableDoubleStateOf(cfg.futuresCommissionBps) }
    var thresholdText by remember(cfg.makerCheckerThresholdUsd) { mutableStateOf(cfg.makerCheckerThresholdUsd.roundToInt().toString()) }
    var haltedSymbolsText by remember(cfg.haltedSymbols) { mutableStateOf(cfg.haltedSymbols.joinToString(", ")) }
    var halted by remember(cfg.tradingHalted) { mutableStateOf(cfg.tradingHalted) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CommissionSliderCard("Equities / Stocks Commission", stockBps) { stockBps = it }
        CommissionSliderCard("ETFs Commission", etfBps) { etfBps = it }
        CommissionSliderCard("Cryptocurrency Commission", cryptoBps) { cryptoBps = it }
        CommissionSliderCard("Global Indices Commission", indexBps) { indexBps = it }
        CommissionSliderCard("Bonds & Fixed Income Commission", bondBps) { bondBps = it }
        CommissionSliderCard("Futures & Derivatives Commission", futuresBps) { futuresBps = it }
        CommissionSliderCard("Commodities Commission", commodityBps) { commodityBps = it }
        CommissionSliderCard("Forex Spread / Commission", forexBps) { forexBps = it }

        OutlinedTextField(
            value = thresholdText,
            onValueChange = { thresholdText = it },
            label = { Text("Maker-Checker Dual Control Threshold (USD)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ChampagneGold, unfocusedBorderColor = ObsidianOutline),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = haltedSymbolsText,
            onValueChange = { haltedSymbolsText = it },
            label = { Text("Halted Symbols (comma-separated, e.g. BTC-USD, TSLA)") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = CrimsonLoss, unfocusedBorderColor = ObsidianOutline),
            modifier = Modifier.fillMaxWidth()
        )

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = ObsidianSurface,
            border = BorderStroke(1.dp, if (halted) CrimsonLoss else ObsidianOutline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Emergency Global Trading Halt",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (halted) CrimsonLoss else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Immediately pause order execution across all global markets",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = halted,
                    onCheckedChange = { halted = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = CrimsonLoss)
                )
            }
        }

        Button(
            onClick = {
                val parsedThreshold = thresholdText.toDoubleOrNull()?.coerceAtLeast(100.0) ?: cfg.makerCheckerThresholdUsd
                val parsedHaltedSymbols = haltedSymbolsText.split(",").map { it.trim().uppercase() }.filter { it.isNotBlank() }
                onSaveConfig(
                    cfg.copy(
                        stockCommissionBps = stockBps,
                        etfCommissionBps = etfBps,
                        cryptoCommissionBps = cryptoBps,
                        forexCommissionBps = forexBps,
                        commodityCommissionBps = commodityBps,
                        indexCommissionBps = indexBps,
                        bondCommissionBps = bondBps,
                        futuresCommissionBps = futuresBps,
                        makerCheckerThresholdUsd = parsedThreshold,
                        haltedSymbols = parsedHaltedSymbols,
                        tradingHalted = halted
                    )
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = ChampagneGold, contentColor = ObsidianBg),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("admin_save_rates_button")
        ) {
            Text("Publish Real-Time Commission Rates & Controls", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CommissionSliderCard(
    title: String,
    valueBps: Double,
    onValueChange: (Double) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianOutline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    text = "${valueBps.roundToInt()} bps (${String.format("%.2f%%", valueBps / 100.0)})",
                    style = MonoPriceStyle.copy(
                        fontSize = MaterialTheme.typography.labelLarge.fontSize,
                        fontWeight = FontWeight.Bold
                    ),
                    color = ChampagneGold
                )
            }
            Slider(
                value = valueBps.toFloat(),
                onValueChange = { onValueChange(it.roundToInt().toDouble()) },
                valueRange = 0f..200f,
                colors = SliderDefaults.colors(
                    thumbColor = ChampagneGold,
                    activeTrackColor = ChampagneGold
                )
            )
        }
    }
}

@Composable
private fun AdminUsersTab(
    users: List<UserAccount>,
    onAdminUpdateUser: (String, String, Double) -> Unit
) {
    var editingUser by remember { mutableStateOf<UserAccount?>(null) }
    var balanceText by remember { mutableStateOf("") }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(users, key = { it.userId }) { user ->
            val suspended = user.accountStatus == "suspended"
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, if (suspended) CrimsonLoss.copy(alpha = 0.6f) else ObsidianOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(user.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(user.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (suspended) CrimsonLossDim else EmeraldAlphaDim
                        ) {
                            Text(
                                text = "${user.accountStatus.uppercase()} • KYC ${user.kycStatus.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (suspended) CrimsonLoss else EmeraldAlpha,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cash: ${formatUsdPrice(user.cashBalanceUsd)}",
                            style = MonoPriceStyle.copy(fontWeight = FontWeight.Bold),
                            color = EmeraldAlpha
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    editingUser = user
                                    balanceText = String.format("%.2f", user.cashBalanceUsd)
                                },
                                border = BorderStroke(1.dp, ObsidianOutline),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Adjust Cash")
                            }
                            Button(
                                onClick = {
                                    val nextStatus = if (suspended) "active" else "suspended"
                                    onAdminUpdateUser(user.userId, nextStatus, user.cashBalanceUsd)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (suspended) EmeraldAlpha else CrimsonLoss,
                                    contentColor = if (suspended) ObsidianBg else Color.White
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (suspended) "Activate" else "Suspend")
                            }
                        }
                    }
                }
            }
        }
    }

    editingUser?.let { target ->
        AlertDialog(
            onDismissRequest = { editingUser = null },
            containerColor = ObsidianSurface,
            title = { Text("Adjust Cash Balance") },
            text = {
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it },
                    label = { Text("New Cash Balance (USD)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newBal = balanceText.toDoubleOrNull()
                        if (newBal != null && newBal >= 0.0) {
                            onAdminUpdateUser(target.userId, target.accountStatus, newBal)
                            editingUser = null
                        }
                    }
                ) { Text("Update") }
            },
            dismissButton = {
                TextButton(onClick = { editingUser = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AdminTradesMonitorTab(
    uiState: BrokerUiState,
    onCancelPendingTrade: (String) -> Unit
) {
    val trades = uiState.allTradesForAdmin
    val totalVolume = trades.sumOf { it.grossAmountUsd }
    val totalFees = trades.sumOf { it.commissionUsd }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianSurface,
                    border = BorderStroke(1.dp, ObsidianOutline),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Platform Volume", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = formatUsdPrice(totalVolume),
                            style = MonoPriceStyle.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = ObsidianSurface,
                    border = BorderStroke(1.dp, ObsidianOutline),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Commissions Earned", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = formatUsdPrice(totalFees),
                            style = MonoPriceStyle.copy(fontWeight = FontWeight.Bold),
                            color = ChampagneGold
                        )
                    }
                }
            }
        }

        items(trades, key = { it.id.ifBlank { "${it.userId}_${it.symbol}_${it.createdAt?.nanoseconds}" } }) { trade ->
            val isBuy = trade.side == "BUY"
            val isPending = trade.status == "PENDING"
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, if (isPending) ChampagneGold.copy(alpha = 0.5f) else ObsidianOutline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${trade.side} ${trade.symbol} (${trade.orderType}) • ${trade.userDisplayName}",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isBuy) EmeraldAlpha else CrimsonLoss
                            )
                            Text(
                                text = "${trade.userEmail ?: trade.userId} • ${formatQuantity(trade.quantity)} @ ${formatUsdPrice(trade.executionPriceUsd)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = formatUsdPrice(trade.grossAmountUsd),
                                style = MonoPriceStyle.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "${trade.status} • Fee ${formatUsdPrice(trade.commissionUsd)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isPending) ChampagneGold else EmeraldAlpha
                            )
                        }
                    }
                    if (isPending && trade.id.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = { onCancelPendingTrade(trade.id) },
                            border = BorderStroke(1.dp, CrimsonLoss),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Admin Cancel Pending Order & Refund", color = CrimsonLoss) }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeeRow(label: String, bps: Double) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = "${bps.roundToInt()} bps (${String.format("%.2f%%", bps / 100.0)})",
            style = MonoPriceStyle.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
