package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AmlRiskAlert
import com.example.data.model.AppNotification
import com.example.data.model.AssetClass
import com.example.data.model.AuditLogEntry
import com.example.data.model.BrokerConfig
import com.example.data.model.ChartTimeframe
import com.example.data.model.DepositRequest
import com.example.data.model.EmailJob
import com.example.data.model.EmailSuppression
import com.example.data.model.EmailTemplate
import com.example.data.model.FundingMethod
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
import com.example.data.repository.AiAssistantRepository
import com.example.data.repository.AiChatMessage
import com.example.data.repository.AiUiState
import com.example.data.repository.AssetChartBundle
import com.example.data.repository.BrokerRepository
import com.example.data.repository.EmailDeliveryRepository
import com.example.data.repository.EnterpriseOpsRepository
import com.example.data.repository.LocalSecurityState
import com.example.data.repository.MarketDataRepository
import com.example.data.repository.UserPreferencesStore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class BrokerViewModel(
    private val brokerRepository: BrokerRepository,
    private val marketRepository: MarketDataRepository,
    private val opsRepository: EnterpriseOpsRepository,
    private val emailRepository: EmailDeliveryRepository,
    private val aiRepository: AiAssistantRepository,
    private val prefsStore: UserPreferencesStore,
    private val currentUserId: String,
    private val displayName: String,
    private val email: String,
    private val photoUrl: String?
) : ViewModel() {

    private val _selectedRegion = MutableStateFlow(MarketRegion.ALL)
    private val _selectedAssetClass = MutableStateFlow<AssetClass?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _watchlistOnly = MutableStateFlow(false)
    private val _isPinUnlocked = MutableStateFlow(false)
    private val _currentSessionId = MutableStateFlow("")

    private val _isAdminUnlocked = MutableStateFlow(false)
    private val _allUsersForAdmin = MutableStateFlow<List<UserAccount>>(emptyList())
    private val _allTradesForAdmin = MutableStateFlow<List<TradeOrder>>(emptyList())
    private val _allKycForAdmin = MutableStateFlow<List<KycSubmission>>(emptyList())
    private val _allDepositsForAdmin = MutableStateFlow<List<DepositRequest>>(emptyList())
    private val _allWithdrawalsForAdmin = MutableStateFlow<List<WithdrawalRequest>>(emptyList())
    private val _allLedgerForAdmin = MutableStateFlow<List<LedgerEntry>>(emptyList())
    private val _allAmlAlertsForAdmin = MutableStateFlow<List<AmlRiskAlert>>(emptyList())
    private val _pendingApprovalsForAdmin = MutableStateFlow<List<MakerCheckerApproval>>(emptyList())
    private val _auditLogsForAdmin = MutableStateFlow<List<AuditLogEntry>>(emptyList())
    private val _staffRolesForAdmin = MutableStateFlow<List<StaffRoleAssignment>>(emptyList())
    private val _allTicketsForAdmin = MutableStateFlow<List<SupportTicket>>(emptyList())
    private val _emailJobsForAdmin = MutableStateFlow<List<EmailJob>>(emptyList())
    private val _emailTemplatesForAdmin = MutableStateFlow<List<EmailTemplate>>(emptyList())
    private val _emailSuppressionsForAdmin = MutableStateFlow<List<EmailSuppression>>(emptyList())
    private val adminJobs = mutableListOf<Job>()

    private val _customerAiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    private val _customerAiMessages = MutableStateFlow<List<AiChatMessage>>(emptyList())
    private val _adminAiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    private val _adminAiMessages = MutableStateFlow<List<AiChatMessage>>(emptyList())

    private val _bannerMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _isSubmittingAction = MutableStateFlow(false)
    private val _selectedAssetChart = MutableStateFlow<AssetChartBundle?>(null)
    val selectedAssetChart: StateFlow<AssetChartBundle?> = _selectedAssetChart.asStateFlow()
    private val _isLoadingChart = MutableStateFlow(false)
    val isLoadingChart: StateFlow<Boolean> = _isLoadingChart.asStateFlow()
    private val triggeredOrderIds = mutableSetOf<String>()

    private fun <T> kotlinx.coroutines.flow.Flow<T>.safeCatch(fallback: T) =
        catch { err -> if (unwrapFirestore(err) == null && err is CancellationException) throw err; emit(fallback) }

    private val userAccountFlow = brokerRepository.observeUserAccount(currentUserId).safeCatch(null)
    private val userKycFlow = brokerRepository.observeUserKyc(currentUserId).safeCatch(null)
    private val userDepositsFlow = brokerRepository.observeUserDeposits(currentUserId).safeCatch(emptyList())
    private val userWithdrawalsFlow = brokerRepository.observeUserWithdrawals(currentUserId).safeCatch(emptyList())
    private val holdingsFlow = brokerRepository.observeHoldings(currentUserId).safeCatch(emptyList())
    private val watchlistFlow = brokerRepository.observeWatchlist(currentUserId).safeCatch(emptySet())
    private val userTradesFlow = brokerRepository.observeUserTrades(currentUserId).safeCatch(emptyList())
    private val brokerConfigFlow = brokerRepository.observeBrokerConfig().safeCatch(BrokerConfig())
    private val notificationsFlow = opsRepository.observeUserNotifications(currentUserId).safeCatch(emptyList())
    private val ledgerFlow = opsRepository.observeUserLedger(currentUserId).safeCatch(emptyList())
    private val ticketsFlow = opsRepository.observeUserTickets(currentUserId).safeCatch(emptyList())
    private val announcementsFlow = opsRepository.observeActiveAnnouncements().safeCatch(emptyList())
    private val sessionsFlow = opsRepository.observeUserSessions(currentUserId).safeCatch(emptyList())
    private val notifPrefsFlow = opsRepository.observeNotificationPreferences(currentUserId).safeCatch(NotificationPreferences())
    private val securityStateFlow = prefsStore.securityState.safeCatch(LocalSecurityState())

    private val userCoreFlow = combine(userAccountFlow, userKycFlow, userDepositsFlow, userWithdrawalsFlow, holdingsFlow) { a, k, d, w, h ->
        UserOpsSlice(a, k, d, w, h)
    }
    private val userEnterpriseFlow = combine(notificationsFlow, ledgerFlow, ticketsFlow, announcementsFlow, sessionsFlow) { n, l, t, an, s ->
        UserEnterpriseSlice(n, l, t, an, s)
    }
    private val securitySliceFlow = combine(notifPrefsFlow, securityStateFlow, _isPinUnlocked, _currentSessionId) { np, ls, unlocked, sid ->
        SecuritySlice(np, ls, if (!ls.pinEnabled) true else unlocked, sid)
    }
    private val marketSliceFlow = combine(watchlistFlow, userTradesFlow, brokerConfigFlow, marketRepository.assets, marketRepository.isLiveFeedConnected) { wl, tr, cfg, assets, live ->
        MarketSyncSlice(wl, tr, cfg, assets, live)
    }
    private val filterSliceFlow = combine(marketRepository.lastSyncTimestamp, _selectedRegion, _selectedAssetClass, _searchQuery, _watchlistOnly) { ts, reg, cls, q, wo ->
        FilterSlice(ts, reg, cls, q, wo)
    }
    private val adminSliceAFlow = combine(_isAdminUnlocked, _allUsersForAdmin, _allTradesForAdmin, _allKycForAdmin, _allDepositsForAdmin) { u, us, tr, k, d ->
        AdminSliceA(u, us, tr, k, d)
    }
    private val adminSliceBFlow = combine(_allWithdrawalsForAdmin, _allLedgerForAdmin, _allAmlAlertsForAdmin, _pendingApprovalsForAdmin, _auditLogsForAdmin) { w, l, aml, mc, al ->
        AdminSliceB(w, l, aml, mc, al)
    }
    private val adminSliceCFlow = combine(_staffRolesForAdmin, _allTicketsForAdmin, _emailJobsForAdmin, _emailTemplatesForAdmin, _emailSuppressionsForAdmin) { sr, tk, ej, et, es ->
        AdminSliceC(sr, tk, ej, et, es)
    }
    private val feedbackAiFlow = combine(_bannerMessage, _errorMessage, _isSubmittingAction, combine(_customerAiState, _customerAiMessages, _adminAiState, _adminAiMessages) { cs, cm, asSt, am ->
        AiSlice(cs, cm, asSt, am)
    }) { banner, error, busy, ai ->
        FeedbackSlice(banner, error, busy, ai)
    }

    private val combinedUserFlow = combine(userCoreFlow, userEnterpriseFlow, securitySliceFlow) { uo, ue, sec -> Triple(uo, ue, sec) }
    private val combinedMarketFlow = combine(marketSliceFlow, filterSliceFlow) { ms, fl -> ms to fl }
    private val combinedAdminFlow = combine(adminSliceAFlow, adminSliceBFlow, adminSliceCFlow) { a, b, c -> Triple(a, b, c) }

    val uiState: StateFlow<BrokerUiState> = combine(
        combinedUserFlow,
        combinedMarketFlow,
        combinedAdminFlow,
        feedbackAiFlow
    ) { (uo, ue, sec), (ms, fl), (admA, admB, admC), fb ->
        val assetsBySymbol = ms.assets.associateBy { it.symbol }
        val filtered = ms.assets.filter { asset ->
            val matchesRegion = fl.region == MarketRegion.ALL || asset.region == fl.region
            val matchesClass = fl.assetClass == null || asset.assetClass == fl.assetClass
            val matchesWatch = !fl.watchlistOnly || asset.symbol in ms.watchlist
            val q = fl.query.trim()
            val matchesQuery = q.isEmpty() ||
                asset.symbol.contains(q, ignoreCase = true) ||
                asset.name.contains(q, ignoreCase = true) ||
                asset.countryName.contains(q, ignoreCase = true) ||
                asset.exchange.contains(q, ignoreCase = true) ||
                asset.sector.contains(q, ignoreCase = true)
            matchesRegion && matchesClass && matchesWatch && matchesQuery
        }

        val cash = uo.account?.cashBalanceUsd ?: 0.0
        val pendingWd = uo.account?.pendingWithdrawalUsd ?: 0.0
        val deposited = uo.account?.totalDepositedUsd ?: 0.0
        val enriched = uo.holdings.map { h ->
            val liveAsset = assetsBySymbol[h.symbol]
            val livePrice = liveAsset?.currentPriceUsd ?: h.avgBuyPriceUsd
            val marketVal = h.quantity * livePrice
            val costBasis = h.quantity * h.avgBuyPriceUsd
            val pnl = marketVal - costBasis
            val pnlPct = if (costBasis > 0.0) (pnl / costBasis) * 100.0 else 0.0
            EnrichedHolding(h, livePrice, marketVal, costBasis, pnl, pnlPct, liveAsset)
        }.sortedByDescending { it.marketValueUsd }

        val investedVal = enriched.sumOf { it.marketValueUsd }
        val totalEquity = cash + investedVal
        val totalPnl = totalEquity - deposited
        val totalPnlPct = if (deposited > 0.0) (totalPnl / deposited) * 100.0 else 0.0
        val dayPnl = enriched.sumOf { eh ->
            ((eh.asset?.currentPriceUsd ?: eh.livePriceUsd) - (eh.asset?.previousCloseUsd ?: eh.livePriceUsd)) * eh.holding.quantity
        }
        val recon = if (admA.unlocked) opsRepository.reconcileAccounts(admA.users, admB.ledger)
        else com.example.data.repository.LedgerReconciliationReport(0, 0, emptyList())

        BrokerUiState(
            userAccount = uo.account,
            userKyc = uo.kyc,
            userDeposits = uo.deposits,
            userWithdrawals = uo.withdrawals,
            isProMode = uo.account?.traderMode == "pro",
            marketAssets = ms.assets,
            filteredAssets = filtered,
            selectedRegion = fl.region,
            selectedAssetClass = fl.assetClass,
            searchQuery = fl.query,
            watchlistOnly = fl.watchlistOnly,
            watchlistSymbols = ms.watchlist,
            portfolioSummary = PortfolioSummary(
                cashBalanceUsd = cash,
                pendingWithdrawalUsd = pendingWd,
                investedValueUsd = investedVal,
                totalEquityUsd = totalEquity,
                totalDepositedUsd = deposited,
                totalPnlUsd = totalPnl,
                totalPnlPct = totalPnlPct,
                dayPnlUsd = dayPnl,
                enrichedHoldings = enriched,
                allocationByClass = enriched.groupBy { AssetClass.fromWire(it.holding.assetClass) }
                    .mapValues { (_, list) -> list.sumOf { it.marketValueUsd } },
                allocationByCountry = enriched.groupBy { it.holding.countryCode }
                    .mapValues { (_, list) -> list.sumOf { it.marketValueUsd } }
            ),
            userTrades = ms.trades,
            userLedgerEntries = ue.ledger,
            userNotifications = ue.notifications,
            unreadNotificationCount = ue.notifications.count { !it.isRead },
            userTickets = ue.tickets,
            activeAnnouncements = ue.announcements,
            userSessions = ue.sessions,
            currentDeviceSessionId = sec.sessionId,
            notificationPreferences = sec.notifPrefs,
            localSecurityState = sec.localSec,
            isPinUnlocked = sec.pinUnlocked,
            brokerConfig = ms.config,
            isLiveFeedConnected = ms.liveConnected,
            lastSyncTimestamp = fl.lastSync,
            isAdminUnlocked = admA.unlocked,
            allUsersForAdmin = admA.users,
            allTradesForAdmin = admA.trades,
            allKycForAdmin = admA.kycList,
            allDepositsForAdmin = admA.deposits,
            allWithdrawalsForAdmin = admB.withdrawals,
            allLedgerForAdmin = admB.ledger,
            allAmlAlertsForAdmin = admB.aml,
            pendingApprovalsForAdmin = admB.approvals,
            auditLogsForAdmin = admB.auditLogs,
            staffRolesForAdmin = admC.staffRoles,
            allTicketsForAdmin = admC.tickets,
            emailJobsForAdmin = admC.emailJobs,
            emailTemplatesForAdmin = admC.emailTemplates,
            emailSuppressionsForAdmin = admC.emailSuppressions,
            emailProviderStatus = emailRepository.getProviderStatus(),
            ledgerReconciliationReport = recon,
            customerAiState = fb.ai.customerState,
            customerAiMessages = fb.ai.customerMessages,
            adminAiState = fb.ai.adminState,
            adminAiMessages = fb.ai.adminMessages,
            bannerMessage = fb.banner,
            errorMessage = fb.error,
            isSubmittingAction = fb.busy
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = BrokerUiState(
            marketAssets = marketRepository.assets.value,
            filteredAssets = marketRepository.assets.value,
            emailProviderStatus = emailRepository.getProviderStatus()
        )
    )

    init {
        viewModelScope.launch {
            brokerRepository.ensureUserProfile(displayName, email, photoUrl)
                .onFailure { err -> _errorMessage.value = err.localizedMessage ?: "Failed to initialize trader account" }
            val sid = prefsStore.getOrCreateDeviceSessionId(currentUserId)
            _currentSessionId.value = sid
            opsRepository.registerDeviceSession(sid)
            val sec = prefsStore.securityState.first()
            if (!sec.pinEnabled) _isPinUnlocked.value = true
        }
        // Automated Trigger Engine for PENDING Limit / Stop / Stop-Limit Orders
        viewModelScope.launch {
            combine(marketRepository.assets, userTradesFlow, brokerConfigFlow) { assets, trades, cfg ->
                Triple(assets.associateBy { it.symbol }, trades.filter { it.status == "PENDING" }, cfg)
            }.collect { (assetMap, pendingOrders, cfg) ->
                if (cfg.tradingHalted) return@collect
                for (order in pendingOrders) {
                    if (order.id.isBlank() || order.id in triggeredOrderIds || order.symbol in cfg.haltedSymbols) continue
                    val asset = assetMap[order.symbol] ?: continue
                    val price = asset.currentPriceUsd
                    val trig = order.triggerPriceUsd ?: order.executionPriceUsd
                    val shouldFill = when (order.orderType) {
                        "LIMIT" -> (order.side == "BUY" && price <= order.executionPriceUsd) ||
                            (order.side == "SELL" && price >= order.executionPriceUsd)
                        "STOP" -> (order.side == "BUY" && price >= trig) ||
                            (order.side == "SELL" && price <= trig)
                        "STOP_LIMIT" -> if (order.side == "BUY") price >= trig && price <= order.executionPriceUsd
                        else price <= trig && price >= order.executionPriceUsd
                        else -> false
                    }
                    if (shouldFill) {
                        triggeredOrderIds.add(order.id)
                        brokerRepository.triggerPendingOrderFill(order, asset, price)
                            .onFailure { triggeredOrderIds.remove(order.id) }
                    }
                }
            }
        }
    }

    fun selectRegion(region: MarketRegion) { _selectedRegion.value = region }
    fun selectAssetClass(assetClass: AssetClass?) { _selectedAssetClass.value = if (_selectedAssetClass.value == assetClass) null else assetClass }
    fun updateSearchQuery(query: String) { _searchQuery.value = query }
    fun toggleWatchlistOnly() { _watchlistOnly.value = !_watchlistOnly.value }
    fun refreshMarkets() { viewModelScope.launch { marketRepository.refreshLiveQuotes() } }
    fun clearFeedback() { _bannerMessage.value = null; _errorMessage.value = null }

    fun loadChartForAsset(asset: MarketAsset, timeframe: ChartTimeframe) {
        viewModelScope.launch {
            _isLoadingChart.value = true
            _selectedAssetChart.value = marketRepository.loadAssetChart(asset, timeframe)
            _isLoadingChart.value = false
        }
    }

    fun toggleTraderMode() {
        val nextMode = if (uiState.value.isProMode) "beginner" else "pro"
        viewModelScope.launch {
            brokerRepository.updateTraderMode(nextMode)
                .onSuccess { _bannerMessage.value = if (nextMode == "pro") "Switched to Pro Trader Terminal" else "Switched to Simple Investor Mode" }
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Unable to switch mode" }
        }
    }

    fun toggleWatchlist(asset: MarketAsset) {
        val watched = asset.symbol in uiState.value.watchlistSymbols
        viewModelScope.launch {
            brokerRepository.toggleWatchlist(asset, !watched)
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Watchlist update failed" }
        }
    }

    // --- KYC, Deposit, Withdrawal & Trading ---

    fun submitKyc(fullLegalName: String, country: String, dateOfBirth: String, idType: String, idNumber: String, docImageBase64: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isSubmittingAction.value = true
            brokerRepository.submitKyc(email, fullLegalName, country, dateOfBirth, idType, idNumber, docImageBase64)
                .onSuccess { _bannerMessage.value = "KYC submitted (Pending). System will review and approve within 24 hours."; onComplete() }
                .onFailure { _errorMessage.value = it.localizedMessage ?: "KYC submission failed" }
            _isSubmittingAction.value = false
        }
    }

    fun submitDepositProof(amountUsd: Double, method: FundingMethod, walletAddressUsed: String, txHash: String, proofImageBase64: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isSubmittingAction.value = true
            brokerRepository.submitDepositRequest(amountUsd, method, walletAddressUsed, txHash, proofImageBase64)
                .onSuccess { _bannerMessage.value = "Deposit submitted (Pending). System will review and approve within 24 hours."; onComplete() }
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Deposit submission failed" }
            _isSubmittingAction.value = false
        }
    }

    fun submitWithdrawalRequest(amountUsd: Double, method: FundingMethod, destinationDetails: String, accountHolderName: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isSubmittingAction.value = true
            brokerRepository.submitWithdrawalRequest(amountUsd, method, destinationDetails, accountHolderName)
                .onSuccess { _bannerMessage.value = "Withdrawal submitted (Pending). System will review and approve within 24 hours."; onComplete() }
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Withdrawal request failed" }
            _isSubmittingAction.value = false
        }
    }

    fun executeTrade(asset: MarketAsset, side: String, orderType: String, quantity: Double, executionPriceUsd: Double, triggerPriceUsd: Double? = null, onSuccess: (TradeOrder) -> Unit = {}) {
        viewModelScope.launch {
            _isSubmittingAction.value = true; _errorMessage.value = null
            brokerRepository.executeTrade(asset, side, orderType, quantity, executionPriceUsd, uiState.value.brokerConfig, triggerPriceUsd)
                .onSuccess { o ->
                    _bannerMessage.value = "${o.side} ${o.symbol} (${o.status}) @ $${String.format(Locale.US, "%.2f", o.executionPriceUsd)}"
                    onSuccess(o)
                }
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Order rejected" }
            _isSubmittingAction.value = false
        }
    }

    fun cancelPendingOrder(tradeId: String) {
        viewModelScope.launch {
            _isSubmittingAction.value = true
            brokerRepository.cancelPendingOrder(tradeId, "Cancelled by trader")
                .onSuccess { _bannerMessage.value = "Pending order cancelled & funds unlocked." }
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to cancel order" }
            _isSubmittingAction.value = false
        }
    }

    // --- Notifications, Support, Sessions, Security PIN, Referral & Customer AI ---

    fun markNotificationRead(id: String) = viewModelScope.launch { opsRepository.markNotificationRead(id) }
    fun markAllNotificationsRead() = viewModelScope.launch { opsRepository.markAllNotificationsRead(currentUserId) }
    fun archiveNotification(id: String) = viewModelScope.launch { opsRepository.archiveNotification(id) }
    fun updateNotificationPrefs(prefs: NotificationPreferences) = viewModelScope.launch {
        opsRepository.updateNotificationPreferences(prefs)
            .onSuccess { _bannerMessage.value = "Notification preferences saved" }
            .onFailure { _errorMessage.value = it.localizedMessage }
    }

    fun revokeSession(sessionId: String) = viewModelScope.launch {
        opsRepository.revokeDeviceSession(sessionId).onSuccess { _bannerMessage.value = "Session revoked" }
    }

    fun enableSecurityPin(pin: String) = viewModelScope.launch {
        runCatching { prefsStore.setSecurityPin(currentUserId, pin) }
            .onSuccess {
                brokerRepository.updateTwoFactorPinEnabled(true)
                _isPinUnlocked.value = true
                _bannerMessage.value = "Security PIN Lock enabled on this device"
            }
            .onFailure { _errorMessage.value = it.localizedMessage ?: "Invalid PIN" }
    }

    fun disableSecurityPin() = viewModelScope.launch {
        prefsStore.disableSecurityPin()
        brokerRepository.updateTwoFactorPinEnabled(false)
        _isPinUnlocked.value = true
        _bannerMessage.value = "Security PIN Lock disabled"
    }

    fun verifySecurityPin(pin: String, onResult: (Boolean, String?) -> Unit) = viewModelScope.launch {
        prefsStore.verifySecurityPin(currentUserId, pin)
            .onSuccess { ok ->
                if (ok) { _isPinUnlocked.value = true; onResult(true, null) }
                else onResult(false, "Incorrect Security PIN")
            }
            .onFailure { onResult(false, it.localizedMessage) }
    }

    fun applyReferralCode(code: String) = viewModelScope.launch {
        _isSubmittingAction.value = true
        brokerRepository.applyReferralCode(code)
            .onSuccess { _bannerMessage.value = "Referral code linked to your profile!" }
            .onFailure { _errorMessage.value = it.localizedMessage ?: "Invalid referral code" }
        _isSubmittingAction.value = false
    }

    fun createSupportTicket(subject: String, category: String, priority: String, message: String, onDone: () -> Unit = {}) = viewModelScope.launch {
        _isSubmittingAction.value = true
        opsRepository.createSupportTicket(displayName, email, subject, category, priority, message)
            .onSuccess { _bannerMessage.value = "Support ticket submitted (24h response SLA)"; onDone() }
            .onFailure { _errorMessage.value = it.localizedMessage ?: "Ticket creation failed" }
        _isSubmittingAction.value = false
    }

    fun buildStatementCsvText(): String = buildAccountStatementCsv(uiState.value)

    fun askCustomerAi(question: String) {
        val q = question.trim()
        if (q.isBlank()) return
        val userMsg = AiChatMessage(UUID.randomUUID().toString(), true, q)
        _customerAiMessages.update { it + userMsg }
        _customerAiState.value = AiUiState.Loading
        viewModelScope.launch {
            aiRepository.askCustomerCopilot(q, buildCustomerAiContext(uiState.value))
                .onSuccess { reply ->
                    _customerAiMessages.update { it + AiChatMessage(UUID.randomUUID().toString(), false, reply) }
                    _customerAiState.value = AiUiState.Success(reply)
                }
                .onFailure { err ->
                    val msg = err.localizedMessage ?: "AI Assistant unavailable"
                    _customerAiState.value = AiUiState.Error(msg)
                    _customerAiMessages.update { it + AiChatMessage(UUID.randomUUID().toString(), false, "Error: $msg") }
                }
        }
    }

    // --- Admin Controls (25046371 Locked) ---

    fun unlockAdmin(passcode: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            _isSubmittingAction.value = true
            brokerRepository.unlockAdminWithPasscode(passcode.trim())
                .onSuccess { _isAdminUnlocked.value = true; startAdminObservers(); onResult(true, null) }
                .onFailure { _isAdminUnlocked.value = false; onResult(false, it.localizedMessage ?: "Access denied: Invalid Admin Password") }
            _isSubmittingAction.value = false
        }
    }

    fun lockAdmin() {
        _isAdminUnlocked.value = false
        adminJobs.forEach { it.cancel() }; adminJobs.clear()
        _allUsersForAdmin.value = emptyList(); _allTradesForAdmin.value = emptyList()
        _allKycForAdmin.value = emptyList(); _allDepositsForAdmin.value = emptyList()
        _allWithdrawalsForAdmin.value = emptyList(); _allLedgerForAdmin.value = emptyList()
        _allAmlAlertsForAdmin.value = emptyList(); _pendingApprovalsForAdmin.value = emptyList()
        _auditLogsForAdmin.value = emptyList(); _staffRolesForAdmin.value = emptyList()
        _allTicketsForAdmin.value = emptyList(); _emailJobsForAdmin.value = emptyList()
        _emailTemplatesForAdmin.value = emptyList(); _emailSuppressionsForAdmin.value = emptyList()
    }

    private fun startAdminObservers() {
        adminJobs.forEach { it.cancel() }; adminJobs.clear()
        adminJobs += viewModelScope.launch { brokerRepository.observeAllUsers().catch { Log.w(TAG, it) }.collect { _allUsersForAdmin.value = it } }
        adminJobs += viewModelScope.launch { brokerRepository.observeAllTrades().catch { Log.w(TAG, it) }.collect { _allTradesForAdmin.value = it } }
        adminJobs += viewModelScope.launch { brokerRepository.observeAllKycRequests().catch { Log.w(TAG, it) }.collect { _allKycForAdmin.value = it } }
        adminJobs += viewModelScope.launch { brokerRepository.observeAllDeposits().catch { Log.w(TAG, it) }.collect { _allDepositsForAdmin.value = it } }
        adminJobs += viewModelScope.launch { brokerRepository.observeAllWithdrawals().catch { Log.w(TAG, it) }.collect { _allWithdrawalsForAdmin.value = it } }
        adminJobs += viewModelScope.launch { opsRepository.observeAllLedgerEntries().catch { Log.w(TAG, it) }.collect { _allLedgerForAdmin.value = it } }
        adminJobs += viewModelScope.launch { opsRepository.observeAllAmlAlerts().catch { Log.w(TAG, it) }.collect { _allAmlAlertsForAdmin.value = it } }
        adminJobs += viewModelScope.launch { opsRepository.observePendingApprovals().catch { Log.w(TAG, it) }.collect { _pendingApprovalsForAdmin.value = it } }
        adminJobs += viewModelScope.launch { opsRepository.observeAuditLogs().catch { Log.w(TAG, it) }.collect { _auditLogsForAdmin.value = it } }
        adminJobs += viewModelScope.launch { opsRepository.observeStaffRoles().catch { Log.w(TAG, it) }.collect { _staffRolesForAdmin.value = it } }
        adminJobs += viewModelScope.launch { opsRepository.observeAllTickets().catch { Log.w(TAG, it) }.collect { _allTicketsForAdmin.value = it } }
        adminJobs += viewModelScope.launch { emailRepository.observeEmailJobs().catch { Log.w(TAG, it) }.collect { _emailJobsForAdmin.value = it } }
        adminJobs += viewModelScope.launch { emailRepository.observeEmailTemplates().catch { Log.w(TAG, it) }.collect { _emailTemplatesForAdmin.value = it } }
        adminJobs += viewModelScope.launch { emailRepository.observeEmailSuppressions().catch { Log.w(TAG, it) }.collect { _emailSuppressionsForAdmin.value = it } }
    }

    fun adminReviewKyc(targetUserId: String, approve: Boolean, reason: String?) = viewModelScope.launch {
        _isSubmittingAction.value = true
        brokerRepository.adminReviewKyc(targetUserId, approve, reason)
            .onSuccess { _bannerMessage.value = if (approve) "KYC Approved for user" else "KYC Rejected" }
            .onFailure { _errorMessage.value = it.localizedMessage ?: "KYC review failed" }
        _isSubmittingAction.value = false
    }

    fun adminReviewDeposit(depositId: String, approve: Boolean, note: String?) = viewModelScope.launch {
        _isSubmittingAction.value = true
        brokerRepository.adminReviewDeposit(depositId, approve, note)
            .onSuccess { _bannerMessage.value = if (approve) "Deposit Approved & Credited to Ledger!" else "Deposit Rejected" }
            .onFailure { _errorMessage.value = it.localizedMessage ?: "Deposit review failed" }
        _isSubmittingAction.value = false
    }

    fun adminProcessWithdrawal(withdrawalId: String, markPaid: Boolean, payoutReference: String, note: String?) = viewModelScope.launch {
        _isSubmittingAction.value = true
        brokerRepository.adminProcessWithdrawal(withdrawalId, markPaid, payoutReference, note, uiState.value.brokerConfig.makerCheckerThresholdUsd)
            .onSuccess { msg -> _bannerMessage.value = msg }
            .onFailure { _errorMessage.value = it.localizedMessage ?: "Withdrawal update failed" }
        _isSubmittingAction.value = false
    }

    fun adminDecideMakerChecker(approvalId: String, approve: Boolean, reason: String? = null) = viewModelScope.launch {
        _isSubmittingAction.value = true
        brokerRepository.adminProcessMakerCheckerApproval(approvalId, approve, reason)
            .onSuccess { _bannerMessage.value = if (approve) "Dual-Control Approval Executed" else "Maker-Checker Request Rejected" }
            .onFailure { _errorMessage.value = it.localizedMessage ?: "Checker sign-off rejected" }
        _isSubmittingAction.value = false
    }

    fun adminUpdateUser(targetUserId: String, accountStatus: String, cashBalanceUsd: Double) = viewModelScope.launch {
        _isSubmittingAction.value = true
        brokerRepository.adminUpdateUserStatusAndBalance(targetUserId, accountStatus, cashBalanceUsd)
            .onSuccess { _bannerMessage.value = "Updated user account ($accountStatus)" }
            .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to update user" }
        _isSubmittingAction.value = false
    }

    fun adminSaveConfig(config: BrokerConfig) = viewModelScope.launch {
        _isSubmittingAction.value = true
        brokerRepository.adminUpdateBrokerConfig(config)
            .onSuccess { _bannerMessage.value = "Brokerage rates, halted symbols & wallets updated" }
            .onFailure { _errorMessage.value = it.localizedMessage ?: "Failed to update config" }
        _isSubmittingAction.value = false
    }

    fun adminResolveAmlAlert(alertId: String, status: String, note: String) = viewModelScope.launch {
        opsRepository.adminResolveAmlAlert(alertId, status, note)
            .onSuccess { _bannerMessage.value = "AML alert marked $status" }
            .onFailure { _errorMessage.value = it.localizedMessage }
    }

    fun adminAssignStaffRole(user: UserAccount, role: String) = viewModelScope.launch {
        opsRepository.adminAssignStaffRole(user, role)
            .onSuccess { _bannerMessage.value = "Assigned $role to ${user.displayName}" }
            .onFailure { _errorMessage.value = it.localizedMessage }
    }

    fun adminReplyTicket(ticket: SupportTicket, status: String, reply: String) = viewModelScope.launch {
        opsRepository.adminReplySupportTicket(ticket, status, reply)
            .onSuccess { _bannerMessage.value = "Ticket updated & user notified" }
            .onFailure { _errorMessage.value = it.localizedMessage }
    }

    fun adminPublishAnnouncement(title: String, message: String, severity: String, audience: String) = viewModelScope.launch {
        opsRepository.adminPublishAnnouncement(title, message, severity, audience)
            .onSuccess { _bannerMessage.value = "Broadcast published to live user screens" }
            .onFailure { _errorMessage.value = it.localizedMessage }
    }

    fun adminDeactivateAnnouncement(id: String) = viewModelScope.launch {
        opsRepository.adminDeactivateAnnouncement(id).onSuccess { _bannerMessage.value = "Announcement archived" }
    }

    fun adminSaveEmailTemplate(template: EmailTemplate) = viewModelScope.launch {
        emailRepository.saveEmailTemplate(template)
            .onSuccess { _bannerMessage.value = "Email template ${template.templateKey} saved" }
            .onFailure { _errorMessage.value = it.localizedMessage }
    }

    fun adminRetryEmailJob(job: EmailJob) = viewModelScope.launch {
        emailRepository.dispatchExistingJob(job)
            .onSuccess { _bannerMessage.value = "Dispatched email (${it.providerMessageId ?: "sent"})" }
            .onFailure { _errorMessage.value = it.localizedMessage }
    }

    fun adminSendTestEmail(recipient: String, templateKey: String) = viewModelScope.launch {
        emailRepository.enqueueAndDispatchEmail(
            userId = currentUserId,
            recipientEmail = recipient,
            templateKey = templateKey,
            variables = mapOf("name" to displayName, "amount" to "$1,000.00", "method" to "USDT (TRC-20)", "txHash" to "TEST-REF-001", "idType" to "Passport", "payoutRef" to "PAYOUT-TEST", "reason" to "Sample check", "side" to "BUY", "symbol" to "AAPL", "status" to "FILLED", "orderType" to "MARKET", "qty" to "5.0000", "price" to "$220.00", "gross" to "$1100.00", "fee" to "$1.10", "net" to "$1101.10", "subject" to "Test Ticket", "reply" to "Verification test message", "title" to "Test Broadcast", "message" to "Enterprise Resend check"),
            idempotencyKey = "test_${System.currentTimeMillis()}"
        ).onSuccess { job -> _bannerMessage.value = "Email job status: ${job.status}" }
            .onFailure { _errorMessage.value = it.localizedMessage }
    }

    fun adminAddSuppression(emailAddr: String, reason: String) = viewModelScope.launch {
        emailRepository.addSuppression(emailAddr, reason).onSuccess { _bannerMessage.value = "Added suppression for $emailAddr" }
    }

    fun adminRemoveSuppression(id: String) = viewModelScope.launch {
        emailRepository.removeSuppression(id).onSuccess { _bannerMessage.value = "Suppression removed" }
    }

    fun askAdminAi(question: String) {
        val q = question.trim()
        if (q.isBlank()) return
        _adminAiMessages.update { it + AiChatMessage(UUID.randomUUID().toString(), true, q) }
        _adminAiState.value = AiUiState.Loading
        viewModelScope.launch {
            aiRepository.askAdminCopilot(q, buildAdminAiContext(uiState.value))
                .onSuccess { reply ->
                    _adminAiMessages.update { it + AiChatMessage(UUID.randomUUID().toString(), false, reply) }
                    _adminAiState.value = AiUiState.Success(reply)
                }
                .onFailure { err ->
                    val msg = err.localizedMessage ?: "Admin AI unavailable"
                    _adminAiState.value = AiUiState.Error(msg)
                    _adminAiMessages.update { it + AiChatMessage(UUID.randomUUID().toString(), false, "Error: $msg") }
                }
        }
    }

    private fun unwrapFirestore(error: Throwable): FirebaseFirestoreException? =
        generateSequence(error) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()

    private data class UserOpsSlice(val account: UserAccount?, val kyc: KycSubmission?, val deposits: List<DepositRequest>, val withdrawals: List<WithdrawalRequest>, val holdings: List<PortfolioHolding>)
    private data class UserEnterpriseSlice(val notifications: List<AppNotification>, val ledger: List<LedgerEntry>, val tickets: List<SupportTicket>, val announcements: List<PlatformAnnouncement>, val sessions: List<UserSessionRecord>)
    private data class SecuritySlice(val notifPrefs: NotificationPreferences, val localSec: LocalSecurityState, val pinUnlocked: Boolean, val sessionId: String)
    private data class MarketSyncSlice(val watchlist: Set<String>, val trades: List<TradeOrder>, val config: BrokerConfig, val assets: List<MarketAsset>, val liveConnected: Boolean)
    private data class FilterSlice(val lastSync: Long, val region: MarketRegion, val assetClass: AssetClass?, val query: String, val watchlistOnly: Boolean)
    private data class AdminSliceA(val unlocked: Boolean, val users: List<UserAccount>, val trades: List<TradeOrder>, val kycList: List<KycSubmission>, val deposits: List<DepositRequest>)
    private data class AdminSliceB(val withdrawals: List<WithdrawalRequest>, val ledger: List<LedgerEntry>, val aml: List<AmlRiskAlert>, val approvals: List<MakerCheckerApproval>, val auditLogs: List<AuditLogEntry>)
    private data class AdminSliceC(val staffRoles: List<StaffRoleAssignment>, val tickets: List<SupportTicket>, val emailJobs: List<EmailJob>, val emailTemplates: List<EmailTemplate>, val emailSuppressions: List<EmailSuppression>)
    private data class AiSlice(val customerState: AiUiState, val customerMessages: List<AiChatMessage>, val adminState: AiUiState, val adminMessages: List<AiChatMessage>)
    private data class FeedbackSlice(val banner: String?, val error: String?, val busy: Boolean, val ai: AiSlice)

    private companion object { const val TAG = "BrokerViewModel" }
}
