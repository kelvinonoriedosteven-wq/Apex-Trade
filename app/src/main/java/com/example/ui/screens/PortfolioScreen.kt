package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AssetClass
import com.example.data.model.DepositRequest
import com.example.data.model.FundingMethod
import com.example.data.model.LedgerEntry
import com.example.data.model.MarketAsset
import com.example.data.model.TradeOrder
import com.example.data.model.WithdrawalRequest
import com.example.ui.components.AllocationDonutChart
import com.example.ui.components.AllocationSlice
import com.example.ui.components.AssetLogoBadge
import com.example.ui.components.formatQuantity
import com.example.ui.components.formatSignedPercent
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
import com.example.ui.theme.SkySignal
import com.example.ui.viewmodel.BrokerUiState
import com.example.ui.viewmodel.EnrichedHolding
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

@Composable
fun PortfolioScreen(
    uiState: BrokerUiState,
    onSubmitKyc: (
        fullLegalName: String,
        country: String,
        dateOfBirth: String,
        idType: String,
        idNumber: String,
        docImageBase64: String,
        onComplete: () -> Unit
    ) -> Unit,
    onSubmitDeposit: (
        amountUsd: Double,
        method: FundingMethod,
        walletAddressUsed: String,
        txHash: String,
        proofImageBase64: String,
        onComplete: () -> Unit
    ) -> Unit,
    onSubmitWithdrawal: (
        amountUsd: Double,
        method: FundingMethod,
        destinationDetails: String,
        accountHolderName: String,
        onComplete: () -> Unit
    ) -> Unit,
    onAssetClick: (MarketAsset) -> Unit,
    onExploreMarkets: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onExportStatement: () -> Unit = {}
) {
    val summary = uiState.portfolioSummary
    val kycStatus = uiState.userAccount?.kycStatus ?: "not_submitted"
    val isKycApproved = kycStatus == "approved"

    var showKycDialog by remember { mutableStateOf(false) }
    var showDepositDialog by remember { mutableStateOf(false) }
    var showWithdrawDialog by remember { mutableStateOf(false) }

    val slices = remember(summary) {
        val list = mutableListOf<AllocationSlice>()
        if (summary.cashBalanceUsd > 0) {
            list.add(AllocationSlice("USD Cash", summary.cashBalanceUsd, SkySignal))
        }
        summary.allocationByClass.forEach { (assetClass, value) ->
            val color = when (assetClass) {
                AssetClass.STOCK -> EmeraldAlpha
                AssetClass.ETF -> Color(0xFF10B981)
                AssetClass.CRYPTO -> ChampagneGold
                AssetClass.INDEX -> Color(0xFF8B5CF6)
                AssetClass.BOND -> Color(0xFF3B82F6)
                AssetClass.FUTURES -> Color(0xFFEC4899)
                AssetClass.COMMODITY -> Color(0xFFF97316)
                AssetClass.FOREX -> Color(0xFF06B6D4)
            }
            list.add(AllocationSlice(assetClass.displayLabel, value, color))
        }
        list
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Total Equity Hero & Funding Actions Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(ObsidianSurface)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_market_hero),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(0.18f)
                )
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "USER DASHBOARD & PORTFOLIO",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = ObsidianBg.copy(alpha = 0.7f),
                                border = BorderStroke(1.dp, ObsidianOutline),
                                modifier = Modifier
                                    .clickable { onExportStatement() }
                                    .testTag("export_statement_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                ) {
                                    Icon(Icons.Default.Download, contentDescription = "Export CSV Statement", tint = EmeraldAlpha, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("CSV", style = MaterialTheme.typography.labelSmall, color = EmeraldAlpha)
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            NotificationBellButton(
                                unreadCount = uiState.unreadNotificationCount,
                                onClick = onOpenNotifications
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = formatUsdPrice(summary.totalEquityUsd),
                        style = MonoPriceStyle.copy(
                            fontSize = MaterialTheme.typography.displaySmall.fontSize,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val pnlPositive = summary.totalPnlUsd >= 0.0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (pnlPositive) EmeraldAlphaDim else CrimsonLossDim
                        ) {
                            Text(
                                text = "${if (pnlPositive) "+" else "-"}${formatUsdPrice(abs(summary.totalPnlUsd))} (${formatSignedPercent(summary.totalPnlPct)})",
                                style = MonoPriceStyle.copy(
                                    fontSize = MaterialTheme.typography.labelMedium.fontSize,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = if (pnlPositive) EmeraldAlpha else CrimsonLoss,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "All-time return",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Available Cash (USD)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatUsdPrice(summary.cashBalanceUsd),
                                style = MonoPriceStyle.copy(
                                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = EmeraldAlpha
                            )
                            if (summary.pendingWithdrawalUsd > 0.0) {
                                Text(
                                    text = "Pending Payout: ${formatUsdPrice(summary.pendingWithdrawalUsd)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ChampagneGold
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Invested Assets",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatUsdPrice(summary.investedValueUsd),
                                style = MonoPriceStyle.copy(
                                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                if (isKycApproved) showDepositDialog = true else showKycDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldAlpha,
                                contentColor = ObsidianBg
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("deposit_funds_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Deposit", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                if (isKycApproved) showWithdrawDialog = true else showKycDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ChampagneGold,
                                contentColor = ObsidianBg
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("withdraw_funds_button")
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Withdraw", fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onExploreMarkets,
                        border = BorderStroke(1.dp, ObsidianOutline),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Trade Global Markets", color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        // KYC Compliance Gate Card
        item {
            KycComplianceBannerCard(
                kycStatus = kycStatus,
                rejectionReason = uiState.userAccount?.kycRejectionReason ?: uiState.userKyc?.rejectionReason,
                onActionClick = { showKycDialog = true }
            )
        }

        // Pending & Recent Deposits / Withdrawals Ledger
        if (uiState.userDeposits.isNotEmpty() || uiState.userWithdrawals.isNotEmpty()) {
            item {
                Text(
                    text = "Deposits & Withdrawals",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)
                )
            }
            items(uiState.userDeposits, key = { "dep_${it.id}_${it.txHash}" }) { dep ->
                UserDepositCard(dep)
            }
            items(uiState.userWithdrawals, key = { "wd_${it.id}_${it.amountUsd}" }) { wd ->
                UserWithdrawalCard(wd)
            }
        }

        // Real-Time Portfolio Analytics
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, ObsidianOutline),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PieChart, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Real-Time Allocation Analytics",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        AllocationDonutChart(
                            slices = slices,
                            modifier = Modifier.size(110.dp)
                        )
                        Spacer(modifier = Modifier.width(18.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            val total = summary.totalEquityUsd.coerceAtLeast(1.0)
                            if (slices.isEmpty()) {
                                Text(
                                    text = "Complete KYC and deposit funds to build your global multi-asset portfolio.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                slices.forEach { slice ->
                                    val pct = (slice.amountUsd / total) * 100.0
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(slice.color)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = slice.label,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Text(
                                            text = String.format(Locale.US, "%.1f%%", pct),
                                            style = MonoPriceStyle.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Open Positions Header
        item {
            Text(
                text = "Open Positions (${summary.enrichedHoldings.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp)
            )
        }

        if (summary.enrichedHoldings.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ObsidianSurface,
                    border = BorderStroke(1.dp, ObsidianOutline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No open positions yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap any global stock, index, commodity, or crypto in Markets to place your first trade.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(summary.enrichedHoldings, key = { it.holding.symbol }) { item ->
                HoldingRow(
                    item = item,
                    onClick = { item.asset?.let(onAssetClick) }
                )
            }
        }
    }

    if (showKycDialog) {
        KycSubmissionDialog(
            initialFullName = uiState.userAccount?.displayName ?: "",
            isSubmitting = uiState.isSubmittingAction,
            onDismiss = { showKycDialog = false },
            onSubmitKyc = onSubmitKyc
        )
    }

    if (showDepositDialog) {
        DepositProofDialog(
            brokerConfig = uiState.brokerConfig,
            isSubmitting = uiState.isSubmittingAction,
            onDismiss = { showDepositDialog = false },
            onSubmitDeposit = onSubmitDeposit
        )
    }

    if (showWithdrawDialog) {
        WithdrawalRequestDialog(
            availableCashUsd = summary.cashBalanceUsd,
            defaultHolderName = uiState.userKyc?.fullLegalName ?: uiState.userAccount?.displayName ?: "",
            isSubmitting = uiState.isSubmittingAction,
            onDismiss = { showWithdrawDialog = false },
            onSubmitWithdrawal = onSubmitWithdrawal
        )
    }
}

private data class KycBannerUi(
    val bg: Color,
    val borderColor: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val subtitle: String,
    val buttonLabel: String?
)

@Composable
private fun KycComplianceBannerCard(
    kycStatus: String,
    rejectionReason: String?,
    onActionClick: () -> Unit
) {
    val banner = when (kycStatus) {
        "approved" -> KycBannerUi(
            bg = EmeraldAlphaDim.copy(alpha = 0.35f),
            borderColor = EmeraldAlpha.copy(alpha = 0.45f),
            icon = Icons.Default.CheckCircle,
            title = "KYC Identity Verified",
            subtitle = "Your account is verified for Admin-wallet deposits & withdrawals.",
            buttonLabel = null
        )
        "pending" -> KycBannerUi(
            bg = ChampagneGoldDim.copy(alpha = 0.6f),
            borderColor = ChampagneGold.copy(alpha = 0.6f),
            icon = Icons.Default.Schedule,
            title = "KYC Verification Pending",
            subtitle = "System will review and approve within 24 hours. Deposits & withdrawals unlock once approved.",
            buttonLabel = null
        )
        "rejected" -> KycBannerUi(
            bg = CrimsonLossDim.copy(alpha = 0.6f),
            borderColor = CrimsonLoss.copy(alpha = 0.6f),
            icon = Icons.Default.ErrorOutline,
            title = "KYC Verification Rejected",
            subtitle = rejectionReason ?: "Please resubmit clearer identity documentation.",
            buttonLabel = "Resubmit KYC"
        )
        else -> KycBannerUi(
            bg = ObsidianSurface,
            borderColor = ChampagneGold.copy(alpha = 0.55f),
            icon = Icons.Default.Badge,
            title = "KYC Verification Required",
            subtitle = "Submit KYC before making a deposit or withdrawal. System will review and approve within 24 hours.",
            buttonLabel = "Submit KYC"
        )
    }

    val tintColor = when (kycStatus) {
        "approved" -> EmeraldAlpha
        "rejected" -> CrimsonLoss
        else -> ChampagneGold
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = banner.bg,
        border = BorderStroke(1.dp, banner.borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("kyc_status_card")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = banner.icon,
                contentDescription = null,
                tint = tintColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = banner.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = tintColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = banner.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (banner.buttonLabel != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onActionClick,
                    colors = ButtonDefaults.buttonColors(containerColor = ChampagneGold, contentColor = ObsidianBg),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("open_kyc_button")
                ) {
                    Text(banner.buttonLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun UserDepositCard(deposit: DepositRequest) {
    val isPending = deposit.status == "pending"
    val isApproved = deposit.status == "approved"
    val statusColor = when {
        isApproved -> EmeraldAlpha
        isPending -> ChampagneGold
        else -> CrimsonLoss
    }
    val statusBg = when {
        isApproved -> EmeraldAlphaDim
        isPending -> ChampagneGoldDim
        else -> CrimsonLossDim
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, if (isPending) ChampagneGold.copy(alpha = 0.4f) else ObsidianOutline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = EmeraldAlpha, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Deposit • ${FundingMethod.fromWire(deposit.method).displayName}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(shape = RoundedCornerShape(6.dp), color = statusBg) {
                    Text(
                        text = deposit.status.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = formatUsdPrice(deposit.amountUsd),
                style = MonoPriceStyle.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Ref / TxHash: ${deposit.txHash}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isPending) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "System will review and approve within 24 hours.",
                    style = MaterialTheme.typography.labelSmall,
                    color = ChampagneGold
                )
            } else if (!deposit.adminNote.isNullOrBlank()) {
                Text(
                    text = "Admin note: ${deposit.adminNote}",
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
private fun UserWithdrawalCard(wd: WithdrawalRequest) {
    val isPending = wd.status == "pending"
    val isPaid = wd.status == "paid"
    val statusColor = when {
        isPaid -> EmeraldAlpha
        isPending -> ChampagneGold
        else -> CrimsonLoss
    }
    val statusBg = when {
        isPaid -> EmeraldAlphaDim
        isPending -> ChampagneGoldDim
        else -> CrimsonLossDim
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, if (isPending) ChampagneGold.copy(alpha = 0.4f) else ObsidianOutline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = ChampagneGold, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Withdrawal • ${FundingMethod.fromWire(wd.method).displayName}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(shape = RoundedCornerShape(6.dp), color = statusBg) {
                    Text(
                        text = wd.status.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = formatUsdPrice(wd.amountUsd),
                style = MonoPriceStyle.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "To (${wd.accountHolderName}): ${wd.destinationDetails}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isPending) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "System will review and approve within 24 hours.",
                    style = MaterialTheme.typography.labelSmall,
                    color = ChampagneGold
                )
            } else {
                if (!wd.payoutReference.isNullOrBlank()) {
                    Text(
                        text = "Payout Tx/Ref: ${wd.payoutReference}",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldAlpha
                    )
                }
                if (!wd.adminNote.isNullOrBlank()) {
                    Text(
                        text = "Admin note: ${wd.adminNote}",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor
                    )
                }
            }
        }
    }
}

@Composable
private fun HoldingRow(
    item: EnrichedHolding,
    onClick: () -> Unit
) {
    val pnlPos = item.unrealizedPnlUsd >= 0.0
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianOutline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.asset != null) {
                AssetLogoBadge(asset = item.asset, size = 40.dp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.holding.symbol,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${formatQuantity(item.holding.quantity)} units @ ${formatUsdPrice(item.holding.avgBuyPriceUsd)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatUsdPrice(item.marketValueUsd),
                    style = MonoPriceStyle.copy(
                        fontSize = MaterialTheme.typography.titleMedium.fontSize,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${if (pnlPos) "+" else "-"}${formatUsdPrice(abs(item.unrealizedPnlUsd))} (${formatSignedPercent(item.unrealizedPnlPct)})",
                    style = MonoPriceStyle.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
                    color = if (pnlPos) EmeraldAlpha else CrimsonLoss
                )
            }
        }
    }
}

@Composable
fun OrdersHistoryScreen(
    trades: List<TradeOrder>,
    ledgerEntries: List<LedgerEntry> = emptyList(),
    onCancelPendingOrder: (String) -> Unit = {}
) {
    val dateFmt = remember { SimpleDateFormat("MMM d, HH:mm:ss", Locale.US) }
    var selectedMode by remember { mutableIntStateOf(0) } // 0=All Orders, 1=Pending, 2=Double-Entry Ledger
    val pendingTrades = remember(trades) { trades.filter { it.status == "PENDING" } }
    val shownTrades = if (selectedMode == 1) pendingTrades else trades

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "Execution & Cash Ledger",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Real-time brokerage orders, stop/limit queue & immutable cash ledger",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Orders (${trades.size})", "Pending (${pendingTrades.size})", "Ledger (${ledgerEntries.size})").forEachIndexed { idx, label ->
                    val active = selectedMode == idx
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (active) EmeraldAlphaDim else ObsidianSurface,
                        border = BorderStroke(1.dp, if (active) EmeraldAlpha else ObsidianOutline),
                        modifier = Modifier.clickable { selectedMode = idx }
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) EmeraldAlpha else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (selectedMode == 2) {
            if (ledgerEntries.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = ObsidianSurface,
                        border = BorderStroke(1.dp, ObsidianOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No double-entry cash ledger movements posted yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            } else {
                items(ledgerEntries, key = { it.id.ifBlank { "${it.referenceId}_${it.createdAt?.nanoseconds}" } }) { entry ->
                    val isCredit = entry.direction == "CREDIT"
                    val isSettle = entry.direction == "SETTLE"
                    val dirColor = when {
                        isCredit -> EmeraldAlpha
                        isSettle -> ChampagneGold
                        else -> CrimsonLoss
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = ObsidianSurface,
                        border = BorderStroke(1.dp, ObsidianOutline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${entry.entryType} • ${entry.direction}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = dirColor,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = entry.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Before: ${formatUsdPrice(entry.balanceBeforeUsd)} -> After: ${formatUsdPrice(entry.balanceAfterUsd)} • ${entry.createdAt?.toDate()?.let { dateFmt.format(it) } ?: "Synced"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${if (isCredit) "+" else if (isSettle) "" else "-"}${formatUsdPrice(entry.amountUsd)}",
                                style = MonoPriceStyle.copy(fontWeight = FontWeight.Bold),
                                color = dirColor
                            )
                        }
                    }
                }
            }
        } else if (shownTrades.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ObsidianSurface,
                    border = BorderStroke(1.dp, ObsidianOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (selectedMode == 1) "No pending limit or stop orders." else "No executed trades yet. Place an order in Markets to see your ledger.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(shownTrades, key = { it.id.ifBlank { "${it.symbol}_${it.createdAt?.nanoseconds}" } }) { trade ->
                val isBuy = trade.side == "BUY"
                val isPending = trade.status == "PENDING"
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = ObsidianSurface,
                    border = BorderStroke(1.dp, if (isPending) ChampagneGold.copy(alpha = 0.5f) else ObsidianOutline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isBuy) EmeraldAlphaDim else CrimsonLossDim
                                    ) {
                                        Text(
                                            text = trade.side,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isBuy) EmeraldAlpha else CrimsonLoss,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = trade.symbol,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = trade.orderType.replace("_", "-"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                val trigNote = trade.triggerPriceUsd?.let { " • Trigger ${formatUsdPrice(it)}" }.orEmpty()
                                Text(
                                    text = "${formatQuantity(trade.quantity)} @ ${formatUsdPrice(trade.executionPriceUsd)}$trigNote",
                                    style = MonoPriceStyle.copy(fontSize = MaterialTheme.typography.bodyMedium.fontSize),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Fee: ${formatUsdPrice(trade.commissionUsd)} (${trade.commissionBps.toInt()} bps) • ${trade.createdAt?.toDate()?.let { dateFmt.format(it) } ?: "Just now"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = formatUsdPrice(trade.netTotalUsd),
                                    style = MonoPriceStyle.copy(
                                        fontSize = MaterialTheme.typography.titleMedium.fontSize,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = trade.status,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (trade.status) {
                                        "FILLED" -> EmeraldAlpha
                                        "PENDING" -> ChampagneGold
                                        else -> CrimsonLoss
                                    }
                                )
                            }
                        }
                        if (isPending && trade.id.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { onCancelPendingOrder(trade.id) },
                                border = BorderStroke(1.dp, CrimsonLoss.copy(alpha = 0.7f)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cancel Pending Order & Unlock Funds", color = CrimsonLoss, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
