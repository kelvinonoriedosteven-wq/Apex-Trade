package com.example.ui.viewmodel

import com.example.data.model.AmlRiskAlert
import com.example.data.model.AppNotification
import com.example.data.model.AssetClass
import com.example.data.model.AuditLogEntry
import com.example.data.model.BrokerConfig
import com.example.data.model.DepositRequest
import com.example.data.model.EmailJob
import com.example.data.model.EmailSuppression
import com.example.data.model.EmailTemplate
import com.example.data.model.KycSubmission
import com.example.data.model.LedgerEntry
import com.example.data.model.MakerCheckerApproval
import com.example.data.model.MarketAsset
import com.example.data.model.MarketRegion
import com.example.data.model.NotificationPreferences
import com.example.data.model.PlatformAnnouncement
import com.example.data.model.PortfolioHolding
import com.example.data.model.StaffRoleAssignment
import com.example.data.model.SupportTicket
import com.example.data.model.TradeOrder
import com.example.data.model.UserAccount
import com.example.data.model.UserSessionRecord
import com.example.data.model.WithdrawalRequest
import com.example.data.repository.AiChatMessage
import com.example.data.repository.AiUiState
import com.example.data.repository.EmailProviderStatus
import com.example.data.repository.LedgerReconciliationReport
import com.example.data.repository.LocalSecurityState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class EnrichedHolding(
    val holding: PortfolioHolding,
    val livePriceUsd: Double,
    val marketValueUsd: Double,
    val costBasisUsd: Double,
    val unrealizedPnlUsd: Double,
    val unrealizedPnlPct: Double,
    val asset: MarketAsset?
)

data class PortfolioSummary(
    val cashBalanceUsd: Double = 0.0,
    val pendingWithdrawalUsd: Double = 0.0,
    val investedValueUsd: Double = 0.0,
    val totalEquityUsd: Double = 0.0,
    val totalDepositedUsd: Double = 0.0,
    val totalPnlUsd: Double = 0.0,
    val totalPnlPct: Double = 0.0,
    val dayPnlUsd: Double = 0.0,
    val enrichedHoldings: List<EnrichedHolding> = emptyList(),
    val allocationByClass: Map<AssetClass, Double> = emptyMap(),
    val allocationByCountry: Map<String, Double> = emptyMap()
)

data class BrokerUiState(
    val userAccount: UserAccount? = null,
    val userKyc: KycSubmission? = null,
    val userDeposits: List<DepositRequest> = emptyList(),
    val userWithdrawals: List<WithdrawalRequest> = emptyList(),
    val isProMode: Boolean = false,
    val marketAssets: List<MarketAsset> = emptyList(),
    val filteredAssets: List<MarketAsset> = emptyList(),
    val selectedRegion: MarketRegion = MarketRegion.ALL,
    val selectedAssetClass: AssetClass? = null,
    val searchQuery: String = "",
    val watchlistOnly: Boolean = false,
    val watchlistSymbols: Set<String> = emptySet(),
    val portfolioSummary: PortfolioSummary = PortfolioSummary(),
    val userTrades: List<TradeOrder> = emptyList(),
    val userLedgerEntries: List<LedgerEntry> = emptyList(),
    val userNotifications: List<AppNotification> = emptyList(),
    val unreadNotificationCount: Int = 0,
    val userTickets: List<SupportTicket> = emptyList(),
    val activeAnnouncements: List<PlatformAnnouncement> = emptyList(),
    val userSessions: List<UserSessionRecord> = emptyList(),
    val currentDeviceSessionId: String = "",
    val notificationPreferences: NotificationPreferences = NotificationPreferences(),
    val localSecurityState: LocalSecurityState = LocalSecurityState(),
    val isPinUnlocked: Boolean = true,
    val brokerConfig: BrokerConfig = BrokerConfig(),
    val isLiveFeedConnected: Boolean = false,
    val lastSyncTimestamp: Long = System.currentTimeMillis(),
    val isAdminUnlocked: Boolean = false,
    val allUsersForAdmin: List<UserAccount> = emptyList(),
    val allTradesForAdmin: List<TradeOrder> = emptyList(),
    val allKycForAdmin: List<KycSubmission> = emptyList(),
    val allDepositsForAdmin: List<DepositRequest> = emptyList(),
    val allWithdrawalsForAdmin: List<WithdrawalRequest> = emptyList(),
    val allLedgerForAdmin: List<LedgerEntry> = emptyList(),
    val allAmlAlertsForAdmin: List<AmlRiskAlert> = emptyList(),
    val pendingApprovalsForAdmin: List<MakerCheckerApproval> = emptyList(),
    val auditLogsForAdmin: List<AuditLogEntry> = emptyList(),
    val staffRolesForAdmin: List<StaffRoleAssignment> = emptyList(),
    val allTicketsForAdmin: List<SupportTicket> = emptyList(),
    val emailJobsForAdmin: List<EmailJob> = emptyList(),
    val emailTemplatesForAdmin: List<EmailTemplate> = emptyList(),
    val emailSuppressionsForAdmin: List<EmailSuppression> = emptyList(),
    val emailProviderStatus: EmailProviderStatus = EmailProviderStatus(
        isConfigured = false,
        providerName = "Resend REST API",
        fromAddress = "onboarding@resend.dev",
        statusDetail = "Awaiting RESEND_API_KEY in AI Studio Secrets."
    ),
    val ledgerReconciliationReport: LedgerReconciliationReport = LedgerReconciliationReport(0, 0, emptyList()),
    val customerAiState: AiUiState = AiUiState.Idle,
    val customerAiMessages: List<AiChatMessage> = emptyList(),
    val adminAiState: AiUiState = AiUiState.Idle,
    val adminAiMessages: List<AiChatMessage> = emptyList(),
    val bannerMessage: String? = null,
    val errorMessage: String? = null,
    val isSubmittingAction: Boolean = false
)

internal fun buildCustomerAiContext(state: BrokerUiState): String {
    val summary = state.portfolioSummary
    val holdingsDesc = if (summary.enrichedHoldings.isEmpty()) {
        "No open positions."
    } else {
        summary.enrichedHoldings.joinToString("\n") { h ->
            "- ${h.holding.symbol} (${h.holding.assetClass}): qty=${String.format(Locale.US, "%.4f", h.holding.quantity)}, avg=$${String.format(Locale.US, "%.2f", h.holding.avgBuyPriceUsd)}, live=$${String.format(Locale.US, "%.2f", h.livePriceUsd)}, value=$${String.format(Locale.US, "%.2f", h.marketValueUsd)}, PnL=${String.format(Locale.US, "%.2f%%", h.unrealizedPnlPct)}"
        }
    }
    return """
        Trader: ${state.userAccount?.displayName ?: "Global Trader"} | KYC Status: ${state.userAccount?.kycStatus ?: "not_submitted"} | Mode: ${if (state.isProMode) "PRO" else "SIMPLE"}
        Cash Balance: $${String.format(Locale.US, "%.2f", summary.cashBalanceUsd)} | Pending Payouts: $${String.format(Locale.US, "%.2f", summary.pendingWithdrawalUsd)}
        Invested Assets: $${String.format(Locale.US, "%.2f", summary.investedValueUsd)} | Total Equity: $${String.format(Locale.US, "%.2f", summary.totalEquityUsd)}
        Total Deposited: $${String.format(Locale.US, "%.2f", summary.totalDepositedUsd)} | All-time PnL: $${String.format(Locale.US, "%.2f", summary.totalPnlUsd)} (${String.format(Locale.US, "%.2f%%", summary.totalPnlPct)})
        Pending Deposits: ${state.userDeposits.count { it.status == "pending" }} | Pending Withdrawals: ${state.userWithdrawals.count { it.status == "pending" }} | Open Pending Orders: ${state.userTrades.count { it.status == "PENDING" }}
        Open Holdings:
        $holdingsDesc
    """.trimIndent()
}

internal fun buildAdminAiContext(state: BrokerUiState): String {
    val totalCash = state.allUsersForAdmin.sumOf { it.cashBalanceUsd }
    val pendingKyc = state.allKycForAdmin.count { it.status == "pending" }
    val pendingDep = state.allDepositsForAdmin.count { it.status == "pending" }
    val pendingWd = state.allWithdrawalsForAdmin.count { it.status == "pending" }
    val openMc = state.pendingApprovalsForAdmin.count { it.status == "pending_checker" }
    val openAml = state.allAmlAlertsForAdmin.count { it.status == "open" || it.status == "escalated" }
    val openTickets = state.allTicketsForAdmin.count { it.status == "open" || it.status == "in_progress" }
    val tradeVol = state.allTradesForAdmin.sumOf { it.grossAmountUsd }
    val fees = state.allTradesForAdmin.sumOf { it.commissionUsd }
    val recon = state.ledgerReconciliationReport
    return """
        Total Users: ${state.allUsersForAdmin.size} | Total Client Cash Custody: $${String.format(Locale.US, "%.2f", totalCash)}
        Pending Queues: KYC=$pendingKyc, Deposits=$pendingDep, Withdrawals=$pendingWd, MakerChecker=$openMc, OpenAMLAlerts=$openAml, OpenTickets=$openTickets
        Trading Volume: $${String.format(Locale.US, "%.2f", tradeVol)} | Commission Revenue: $${String.format(Locale.US, "%.2f", fees)}
        Global Halt: ${state.brokerConfig.tradingHalted} | Halted Symbols: ${state.brokerConfig.haltedSymbols.joinToString(", ").ifBlank { "None" }}
        Maker-Checker Threshold: $${String.format(Locale.US, "%.2f", state.brokerConfig.makerCheckerThresholdUsd)}
        Ledger Reconciliation: Checked=${recon.totalUsersChecked}, Matched=${recon.matchedUsers}, Mismatched=${recon.mismatchedUsers.size}
        Email Provider: ${state.emailProviderStatus.providerName} (${if (state.emailProviderStatus.isConfigured) "Configured" else "Awaiting RESEND_API_KEY"})
    """.trimIndent()
}

internal fun buildAccountStatementCsv(state: BrokerUiState): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val sb = StringBuilder()
    val acct = state.userAccount
    sb.appendLine("# ApexTrade Global Brokerage - Official Account Statement")
    sb.appendLine("# Generated: ${fmt.format(Date())}")
    sb.appendLine("# Client: ${acct?.displayName ?: ""} (${acct?.email ?: ""})")
    sb.appendLine("# Cash USD: ${String.format(Locale.US, "%.2f", state.portfolioSummary.cashBalanceUsd)}, Equity USD: ${String.format(Locale.US, "%.2f", state.portfolioSummary.totalEquityUsd)}")
    sb.appendLine()
    sb.appendLine("RecordType,Timestamp,SymbolOrMethod,Action,QuantityOrDirection,PriceOrBeforeUsd,FeeUsd,AmountOrNetUsd,BalanceAfterUsd,Status,Reference")
    state.userLedgerEntries.forEach { e ->
        val ts = e.createdAt?.toDate()?.let { fmt.format(it) } ?: "PendingSync"
        sb.appendLine(
            "LEDGER,\"$ts\",\"${e.referenceType}\",\"${e.entryType}\",\"${e.direction}\",${String.format(Locale.US, "%.2f", e.balanceBeforeUsd)},0.00,${String.format(Locale.US, "%.2f", e.amountUsd)},${String.format(Locale.US, "%.2f", e.balanceAfterUsd)},POSTED,\"${e.referenceId}\""
        )
    }
    state.userTrades.forEach { t ->
        val ts = t.createdAt?.toDate()?.let { fmt.format(it) } ?: "PendingSync"
        sb.appendLine(
            "TRADE,\"$ts\",\"${t.symbol}\",\"${t.side}_${t.orderType}\",${String.format(Locale.US, "%.6f", t.quantity)},${String.format(Locale.US, "%.4f", t.executionPriceUsd)},${String.format(Locale.US, "%.2f", t.commissionUsd)},${String.format(Locale.US, "%.2f", t.netTotalUsd)},\"\",${t.status},\"${t.id}\""
        )
    }
    return sb.toString()
}
