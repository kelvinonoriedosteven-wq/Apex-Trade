package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class FundingMethod(val wireValue: String, val displayName: String, val networkHint: String) {
    USDT_TRC20("USDT_TRC20", "USDT (TRC-20)", "TRON Network • Low Fee"),
    USDT_ERC20("USDT_ERC20", "USDT (ERC-20)", "Ethereum Mainnet"),
    BTC("BTC", "Bitcoin (BTC)", "Bitcoin Network"),
    ETH("ETH", "Ethereum (ETH)", "Ethereum Mainnet"),
    BANK_WIRE("BANK_WIRE", "Institutional Wire", "SWIFT / IBAN / Fedwire");

    companion object {
        fun fromWire(value: String): FundingMethod =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: USDT_TRC20
    }
}

@IgnoreExtraProperties
data class UserAccount(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val cashBalanceUsd: Double = 0.0,
    val totalDepositedUsd: Double = 0.0,
    val pendingWithdrawalUsd: Double = 0.0,
    val kycStatus: String = "not_submitted",
    val kycRejectionReason: String? = null,
    val traderMode: String = "beginner",
    val accountStatus: String = "active",
    val referralCode: String = "",
    val referredBy: String? = null,
    val twoFactorPinEnabled: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class KycSubmission(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val fullLegalName: String = "",
    val country: String = "",
    val dateOfBirth: String = "",
    val idType: String = "Passport",
    val idNumber: String = "",
    val docImageBase64: String = "",
    val status: String = "pending",
    val rejectionReason: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class DepositRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userDisplayName: String = "",
    val userEmail: String = "",
    val amountUsd: Double = 0.0,
    val method: String = FundingMethod.USDT_TRC20.wireValue,
    val walletAddressUsed: String = "",
    val txHash: String = "",
    val proofImageBase64: String = "",
    val status: String = "pending",
    val adminNote: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class WithdrawalRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userDisplayName: String = "",
    val userEmail: String = "",
    val amountUsd: Double = 0.0,
    val method: String = FundingMethod.USDT_TRC20.wireValue,
    val destinationDetails: String = "",
    val accountHolderName: String = "",
    val payoutReference: String? = null,
    val status: String = "pending",
    val adminNote: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PortfolioHolding(
    @DocumentId val id: String = "",
    val userId: String = "",
    val symbol: String = "",
    val assetName: String = "",
    val assetClass: String = "stock",
    val countryCode: String = "US",
    val exchange: String = "NASDAQ",
    val quantity: Double = 0.0,
    val avgBuyPriceUsd: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class WatchlistEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val symbol: String = "",
    @ServerTimestamp val addedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class TradeOrder(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userDisplayName: String = "",
    val userEmail: String? = null,
    val symbol: String = "",
    val assetName: String = "",
    val assetClass: String = "stock",
    val countryCode: String = "US",
    val exchange: String = "NASDAQ",
    val side: String = "BUY",
    val orderType: String = "MARKET",
    val quantity: Double = 0.0,
    val executionPriceUsd: Double = 0.0,
    val triggerPriceUsd: Double? = null,
    val grossAmountUsd: Double = 0.0,
    val commissionBps: Double = 10.0,
    val commissionUsd: Double = 0.0,
    val netTotalUsd: Double = 0.0,
    val status: String = "FILLED",
    val cancelReason: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class BrokerConfig(
    @DocumentId val id: String = "global",
    val stockCommissionBps: Double = 10.0,
    val etfCommissionBps: Double = 8.0,
    val cryptoCommissionBps: Double = 25.0,
    val forexCommissionBps: Double = 5.0,
    val commodityCommissionBps: Double = 15.0,
    val indexCommissionBps: Double = 8.0,
    val bondCommissionBps: Double = 6.0,
    val futuresCommissionBps: Double = 12.0,
    val minOrderUsd: Double = 1.0,
    val makerCheckerThresholdUsd: Double = 5000.0,
    val tradingHalted: Boolean = false,
    val haltedSymbols: List<String> = emptyList(),
    val usdtTrc20Address: String = "TQn9Y2khEsLJW1ChVWFMSMeRDow5KcbLSE",
    val usdtErc20Address: String = "0x742d35Cc6634C0532925a3b844Bc454e4438f44e",
    val btcWalletAddress: String = "bc1qxy2kgdygjrsqtzq2n0yrf2493p83kkfjhx0wlh",
    val ethWalletAddress: String = "0x742d35Cc6634C0532925a3b844Bc454e4438f44e",
    val bankWireDetails: String = "ApexTrade Institutional Custody • SWIFT: APEXUS33 • Acct: 8820194421",
    val updatedByUid: String = "",
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun commissionBpsFor(assetClass: AssetClass): Double = when (assetClass) {
        AssetClass.STOCK -> stockCommissionBps
        AssetClass.ETF -> etfCommissionBps
        AssetClass.CRYPTO -> cryptoCommissionBps
        AssetClass.FOREX -> forexCommissionBps
        AssetClass.COMMODITY -> commodityCommissionBps
        AssetClass.INDEX -> indexCommissionBps
        AssetClass.BOND -> bondCommissionBps
        AssetClass.FUTURES -> futuresCommissionBps
    }

    fun walletFor(method: FundingMethod): String = when (method) {
        FundingMethod.USDT_TRC20 -> usdtTrc20Address
        FundingMethod.USDT_ERC20 -> usdtErc20Address
        FundingMethod.BTC -> btcWalletAddress
        FundingMethod.ETH -> ethWalletAddress
        FundingMethod.BANK_WIRE -> bankWireDetails
    }
}
