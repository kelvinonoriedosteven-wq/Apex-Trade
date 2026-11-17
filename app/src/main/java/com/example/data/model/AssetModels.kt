package com.example.data.model

enum class AssetClass(val wireValue: String, val displayLabel: String) {
    STOCK("stock", "Stocks"),
    ETF("etf", "ETFs"),
    CRYPTO("crypto", "Crypto"),
    INDEX("index", "Indices"),
    BOND("bond", "Bonds"),
    FUTURES("futures", "Futures"),
    COMMODITY("commodity", "Commodities"),
    FOREX("forex", "Forex");

    companion object {
        fun fromWire(value: String): AssetClass =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: STOCK
    }
}

enum class OrderType(val wireValue: String, val displayLabel: String) {
    MARKET("MARKET", "Market"),
    LIMIT("LIMIT", "Limit"),
    STOP("STOP", "Stop"),
    STOP_LIMIT("STOP_LIMIT", "Stop-Limit");

    companion object {
        fun fromWire(value: String): OrderType =
            entries.firstOrNull { it.wireValue.equals(value, ignoreCase = true) } ?: MARKET
    }
}

enum class MarketRegion(val displayName: String, val badge: String) {
    ALL("All Markets", "GLB"),
    NORTH_AMERICA("North America", "NA"),
    EUROPE("Europe", "EU"),
    ASIA_PACIFIC("Asia-Pacific", "APAC"),
    LATAM_MEA("LatAm & MEA", "EM"),
    CRYPTO("Crypto 24/7", "24/7"),
    GLOBAL_MACRO("Indices & FX", "MACRO")
}

enum class ChartTimeframe(val label: String, val pointsCount: Int, val yahooRange: String, val yahooInterval: String, val coingeckoDays: String) {
    ONE_DAY("1D", 48, "1d", "15m", "1"),
    ONE_WEEK("1W", 42, "5d", "1h", "7"),
    ONE_MONTH("1M", 30, "1mo", "1d", "30"),
    THREE_MONTHS("3M", 45, "3mo", "1d", "90"),
    ONE_YEAR("1Y", 52, "1y", "1wk", "365"),
    ALL("ALL", 60, "5y", "1mo", "max")
}

data class PricePoint(
    val timestampMillis: Long,
    val label: String,
    val price: Double,
    val volume: Double = 0.0
)

data class CandlePoint(
    val timestampMillis: Long,
    val label: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

data class MarketAsset(
    val symbol: String,
    val name: String,
    val assetClass: AssetClass,
    val region: MarketRegion,
    val countryCode: String,
    val countryFlag: String,
    val countryName: String,
    val exchange: String,
    val sector: String,
    val currentPriceUsd: Double,
    val previousCloseUsd: Double,
    val dayHighUsd: Double,
    val dayLowUsd: Double,
    val week52HighUsd: Double,
    val week52LowUsd: Double,
    val marketCapUsd: Double,
    val volume24hUsd: Double,
    val peRatio: Double?,
    val dividendYieldPct: Double?,
    val description: String,
    val logoUrl: String,
    val brandHex: Long,
    val coinGeckoId: String? = null,
    val yahooSymbol: String? = null,
    val sparkline: List<Double> = emptyList()
) {
    val changeUsd: Double
        get() = currentPriceUsd - previousCloseUsd

    val changePercent: Double
        get() = if (previousCloseUsd > 0.0) ((currentPriceUsd - previousCloseUsd) / previousCloseUsd) * 100.0 else 0.0

    val isPositive: Boolean
        get() = changeUsd >= 0.0

    val sanitizedDocId: String
        get() = symbol.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
}
