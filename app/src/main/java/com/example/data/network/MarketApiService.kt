package com.example.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class CoinGeckoMarketDto(
    val id: String,
    val symbol: String,
    val name: String,
    val image: String?,
    @Json(name = "current_price") val currentPrice: Double?,
    @Json(name = "market_cap") val marketCap: Double?,
    @Json(name = "total_volume") val totalVolume: Double?,
    @Json(name = "high_24h") val high24h: Double?,
    @Json(name = "low_24h") val low24h: Double?,
    @Json(name = "price_change_percentage_24h") val priceChangePct24h: Double?,
    @Json(name = "sparkline_in_7d") val sparklineIn7d: SparklineDto?
)

@JsonClass(generateAdapter = true)
data class SparklineDto(
    val price: List<Double>?
)

@JsonClass(generateAdapter = true)
data class CoinGeckoChartDto(
    val prices: List<List<Double>>?,
    @Json(name = "total_volumes") val totalVolumes: List<List<Double>>?
)

@JsonClass(generateAdapter = true)
data class YahooChartResponse(
    val chart: YahooChartBody?
)

@JsonClass(generateAdapter = true)
data class YahooChartBody(
    val result: List<YahooChartResult>?
)

@JsonClass(generateAdapter = true)
data class YahooChartResult(
    val meta: YahooChartMeta?,
    val timestamp: List<Long>?,
    val indicators: YahooIndicators?
)

@JsonClass(generateAdapter = true)
data class YahooChartMeta(
    val symbol: String?,
    val regularMarketPrice: Double?,
    val chartPreviousClose: Double?,
    val previousClose: Double?,
    val regularMarketDayHigh: Double?,
    val regularMarketDayLow: Double?
)

@JsonClass(generateAdapter = true)
data class YahooIndicators(
    val quote: List<YahooQuoteSeries>?
)

@JsonClass(generateAdapter = true)
data class YahooQuoteSeries(
    val open: List<Double?>?,
    val high: List<Double?>?,
    val low: List<Double?>?,
    val close: List<Double?>?,
    val volume: List<Long?>?
)

interface CoinGeckoApi {
    @GET("api/v3/coins/markets")
    suspend fun getMarkets(
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("ids") ids: String,
        @Query("sparkline") sparkline: Boolean = true,
        @Query("price_change_percentage") priceChangePct: String = "24h"
    ): List<CoinGeckoMarketDto>

    @GET("api/v3/coins/{id}/market_chart")
    suspend fun getMarketChart(
        @Path("id") id: String,
        @Query("vs_currency") vsCurrency: String = "usd",
        @Query("days") days: String = "7"
    ): CoinGeckoChartDto
}

interface YahooFinanceApi {
    @GET("v8/finance/chart/{symbol}")
    suspend fun getChart(
        @Path("symbol") symbol: String,
        @Query("range") range: String = "1d",
        @Query("interval") interval: String = "15m"
    ): YahooChartResponse
}

object MarketNetworkClient {
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Android; ApexTrade/1.0)")
                    .build()
                chain.proceed(req)
            }
            .build()
    }

    private val moshi by lazy {
        com.squareup.moshi.Moshi.Builder().build()
    }

    val coinGeckoApi: CoinGeckoApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.coingecko.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(CoinGeckoApi::class.java)
    }

    val yahooFinanceApi: YahooFinanceApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://query1.finance.yahoo.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(YahooFinanceApi::class.java)
    }
}
