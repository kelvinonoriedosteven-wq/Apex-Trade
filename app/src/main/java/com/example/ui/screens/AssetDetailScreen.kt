package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CandlestickChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.BrokerConfig
import com.example.data.model.ChartTimeframe
import com.example.data.model.MarketAsset
import com.example.data.model.PortfolioHolding
import com.example.data.repository.AssetChartBundle
import com.example.ui.components.AssetLogoBadge
import com.example.ui.components.InteractiveMarketChart
import com.example.ui.components.formatCurrencyCompact
import com.example.ui.components.formatQuantity
import com.example.ui.components.formatSignedPercent
import com.example.ui.components.formatUsdPrice
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CrimsonLoss
import com.example.ui.theme.CrimsonLossDim
import com.example.ui.theme.EmeraldAlpha
import com.example.ui.theme.EmeraldAlphaDim
import com.example.ui.theme.MonoPriceStyle
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ObsidianSurfaceElevated
import kotlin.math.abs

@Composable
fun AssetDetailScreen(
    asset: MarketAsset,
    isWatched: Boolean,
    isProMode: Boolean,
    cashBalanceUsd: Double,
    holding: PortfolioHolding?,
    brokerConfig: BrokerConfig,
    chartBundle: AssetChartBundle?,
    isLoadingChart: Boolean,
    isSubmitting: Boolean,
    onLoadChart: (ChartTimeframe) -> Unit,
    onToggleWatchlist: () -> Unit,
    onToggleTraderMode: () -> Unit,
    onExecuteOrder: (side: String, orderType: String, quantity: Double, priceUsd: Double, triggerPriceUsd: Double?) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var selectedTimeframe by remember { mutableStateOf(ChartTimeframe.ONE_DAY) }
    var showCandlesticks by remember(isProMode) { mutableStateOf(isProMode) }
    var showMovingAvg by remember(isProMode) { mutableStateOf(isProMode) }
    var scrubIndex by remember { mutableStateOf<Int?>(null) }

    var side by remember { mutableStateOf("BUY") }
    var orderType by remember { mutableStateOf("MARKET") }
    var inputInDollars by remember { mutableStateOf(true) }
    var amountInput by remember { mutableStateOf("1000") }
    var limitPriceInput by remember(asset.symbol) { mutableStateOf(String.format("%.2f", asset.currentPriceUsd)) }
    var triggerPriceInput by remember(asset.symbol) { mutableStateOf(String.format("%.2f", asset.currentPriceUsd * 0.98)) }

    LaunchedEffect(asset.symbol, selectedTimeframe) {
        onLoadChart(selectedTimeframe)
    }

    val linePoints = chartBundle?.linePoints.orEmpty()
    val candles = chartBundle?.candles.orEmpty()
    val scrubbedPrice = scrubIndex?.let { idx ->
        if (showCandlesticks && idx in candles.indices) candles[idx].close
        else if (idx in linePoints.indices) linePoints[idx].price
        else null
    }
    val scrubbedLabel = scrubIndex?.let { idx ->
        if (showCandlesticks && idx in candles.indices) candles[idx].label
        else if (idx in linePoints.indices) linePoints[idx].label
        else null
    }

    val displayedPrice = scrubbedPrice ?: asset.currentPriceUsd
    val effectiveTriggerPrice = if (orderType == "STOP" || orderType == "STOP_LIMIT") {
        triggerPriceInput.toDoubleOrNull()?.takeIf { it > 0.0 } ?: asset.currentPriceUsd
    } else null
    val effectiveExecutionPrice = when (orderType) {
        "LIMIT", "STOP_LIMIT" -> limitPriceInput.toDoubleOrNull()?.takeIf { it > 0.0 } ?: asset.currentPriceUsd
        "STOP" -> effectiveTriggerPrice ?: asset.currentPriceUsd
        else -> asset.currentPriceUsd
    }
    val isSymbolHalted = brokerConfig.tradingHalted || asset.symbol in brokerConfig.haltedSymbols
    val parsedInput = amountInput.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val orderQty = if (inputInDollars) {
        if (effectiveExecutionPrice > 0.0) parsedInput / effectiveExecutionPrice else 0.0
    } else {
        parsedInput
    }
    val grossUsd = orderQty * effectiveExecutionPrice
    val commBps = brokerConfig.commissionBpsFor(asset.assetClass)
    val commUsd = grossUsd * (commBps / 10_000.0)
    val netTotalUsd = if (side == "BUY") grossUsd + commUsd else (grossUsd - commUsd).coerceAtLeast(0.0)

    val trendColor = if (asset.isPositive) EmeraldAlpha else CrimsonLoss

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp)
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to markets",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                AssetLogoBadge(asset = asset, size = 36.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = asset.symbol,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${asset.name} • ${asset.exchange}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isProMode) ChampagneGold.copy(alpha = 0.16f) else ObsidianSurfaceElevated,
                    border = BorderStroke(1.dp, if (isProMode) ChampagneGold else ObsidianOutline),
                    modifier = Modifier.clickable { onToggleTraderMode() }
                ) {
                    Text(
                        text = if (isProMode) "PRO" else "SIMPLE",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isProMode) ChampagneGold else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                IconButton(onClick = onToggleWatchlist) {
                    Icon(
                        imageVector = if (isWatched) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Toggle watchlist",
                        tint = if (isWatched) ChampagneGold else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Price Header
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = formatUsdPrice(displayedPrice),
                style = MonoPriceStyle.copy(
                    fontSize = MaterialTheme.typography.displaySmall.fontSize,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (scrubbedLabel != null) {
                    Text(
                        text = "Inspecting: $scrubbedLabel",
                        style = MaterialTheme.typography.labelLarge,
                        color = ChampagneGold
                    )
                } else {
                    Text(
                        text = "${if (asset.changeUsd >= 0) "+" else "-"}${formatUsdPrice(abs(asset.changeUsd))} (${formatSignedPercent(asset.changePercent)})",
                        style = MonoPriceStyle.copy(
                            fontSize = MaterialTheme.typography.labelLarge.fontSize,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = trendColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${asset.countryFlag} ${asset.countryName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Interactive Chart Card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = ObsidianSurface,
            border = BorderStroke(1.dp, ObsidianOutline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (chartBundle?.isLiveApiData == true) "Live Market Chart" else "Interactive Price & Volume",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (showMovingAvg) ChampagneGold.copy(alpha = 0.16f) else ObsidianSurfaceElevated,
                            border = BorderStroke(1.dp, if (showMovingAvg) ChampagneGold else ObsidianOutline),
                            modifier = Modifier.clickable { showMovingAvg = !showMovingAvg }
                        ) {
                            Text(
                                text = "MA",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (showMovingAvg) ChampagneGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (showCandlesticks) EmeraldAlphaDim else ObsidianSurfaceElevated,
                            border = BorderStroke(1.dp, if (showCandlesticks) EmeraldAlpha else ObsidianOutline),
                            modifier = Modifier.clickable { showCandlesticks = !showCandlesticks }
                        ) {
                            Icon(
                                imageVector = if (showCandlesticks) Icons.Default.CandlestickChart else Icons.Default.ShowChart,
                                contentDescription = "Toggle line or candlestick chart",
                                tint = if (showCandlesticks) EmeraldAlpha else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingChart && linePoints.isEmpty()) {
                        CircularProgressIndicator(color = EmeraldAlpha)
                    } else {
                        InteractiveMarketChart(
                            linePoints = linePoints,
                            candles = candles,
                            previousClose = asset.previousCloseUsd,
                            isPositive = asset.isPositive,
                            showCandlesticks = showCandlesticks,
                            showMovingAverage = showMovingAvg,
                            scrubIndex = scrubIndex,
                            onScrubIndexChanged = { scrubIndex = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Timeframe Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ChartTimeframe.entries.forEach { tf ->
                        val selected = selectedTimeframe == tf
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) EmeraldAlphaDim else Color.Transparent,
                            border = if (selected) BorderStroke(1.dp, EmeraldAlpha) else null,
                            modifier = Modifier.clickable { selectedTimeframe = tf }
                        ) {
                            Text(
                                text = tf.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) EmeraldAlpha else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Current Position Card (if owned)
        if (holding != null && holding.quantity > 0.0) {
            val marketVal = holding.quantity * asset.currentPriceUsd
            val pnl = marketVal - (holding.quantity * holding.avgBuyPriceUsd)
            val pnlPct = if (holding.avgBuyPriceUsd > 0) ((asset.currentPriceUsd - holding.avgBuyPriceUsd) / holding.avgBuyPriceUsd) * 100.0 else 0.0
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = ObsidianSurface,
                border = BorderStroke(1.dp, ObsidianOutline),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Your Position",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${formatQuantity(holding.quantity)} ${asset.symbol}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Avg Entry: ${formatUsdPrice(holding.avgBuyPriceUsd)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatUsdPrice(marketVal),
                            style = MonoPriceStyle.copy(
                                fontSize = MaterialTheme.typography.titleMedium.fontSize,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${if (pnl >= 0) "+" else "-"}${formatUsdPrice(abs(pnl))} (${formatSignedPercent(pnlPct)})",
                            style = MonoPriceStyle.copy(fontSize = MaterialTheme.typography.labelMedium.fontSize),
                            color = if (pnl >= 0) EmeraldAlpha else CrimsonLoss
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Order Execution Ticket
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = ObsidianSurface,
            border = BorderStroke(1.dp, ObsidianOutline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Buy / Sell Segmented Tabs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf("BUY", "SELL").forEach { tab ->
                        val active = side == tab
                        val activeBg = if (tab == "BUY") EmeraldAlphaDim else CrimsonLossDim
                        val activeBorder = if (tab == "BUY") EmeraldAlpha else CrimsonLoss
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (active) activeBg else ObsidianSurfaceElevated,
                            border = BorderStroke(1.dp, if (active) activeBorder else ObsidianOutline),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { side = tab }
                                .testTag("trade_side_${tab.lowercase()}")
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 11.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tab,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) activeBorder else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Order Type & Input Unit Row
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isProMode) "Order Type" else "Instant Market Order",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ObsidianSurfaceElevated,
                            border = BorderStroke(1.dp, ObsidianOutline),
                            modifier = Modifier.clickable { inputInDollars = !inputInDollars }
                        ) {
                            Text(
                                text = if (inputInDollars) "By USD ($)" else "By Units",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldAlpha,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                    if (isProMode) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("MARKET", "LIMIT", "STOP", "STOP_LIMIT").forEach { ot ->
                                val active = orderType == ot
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (active) ChampagneGold.copy(alpha = 0.16f) else ObsidianSurfaceElevated,
                                    border = BorderStroke(1.dp, if (active) ChampagneGold else ObsidianOutline),
                                    modifier = Modifier.clickable { orderType = ot }
                                ) {
                                    Text(
                                        text = ot.replace("_", "-"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (active) ChampagneGold else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text(if (inputInDollars) "Investment Amount (USD)" else "Quantity (${asset.symbol})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = EmeraldAlpha,
                        unfocusedBorderColor = ObsidianOutline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("trade_amount_input")
                )

                if (isProMode && (orderType == "STOP" || orderType == "STOP_LIMIT")) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = triggerPriceInput,
                        onValueChange = { triggerPriceInput = it },
                        label = { Text("Stop Trigger Price (USD)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ChampagneGold,
                            unfocusedBorderColor = ObsidianOutline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (isProMode && (orderType == "LIMIT" || orderType == "STOP_LIMIT")) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = limitPriceInput,
                        onValueChange = { limitPriceInput = it },
                        label = { Text("Limit Execution Price (USD)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ChampagneGold,
                            unfocusedBorderColor = ObsidianOutline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Preset Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = if (side == "BUY") {
                        listOf("100" to "$100", "500" to "$500", "1000" to "$1K", "5000" to "$5K")
                    } else {
                        val heldQty = holding?.quantity ?: 0.0
                        listOf(
                            String.format("%.4f", heldQty * 0.25) to "25%",
                            String.format("%.4f", heldQty * 0.50) to "50%",
                            String.format("%.4f", heldQty) to "MAX"
                        )
                    }
                    presets.forEach { (value, label) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ObsidianSurfaceElevated,
                            border = BorderStroke(1.dp, ObsidianOutline),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    if (side == "SELL") inputInDollars = false else inputInDollars = true
                                    amountInput = value
                                }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(vertical = 7.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = ObsidianOutline)
                Spacer(modifier = Modifier.height(12.dp))

                OrderSummaryRow("Est. Quantity", "${formatQuantity(orderQty)} ${asset.symbol}")
                OrderSummaryRow("Execution Price", formatUsdPrice(effectiveExecutionPrice))
                OrderSummaryRow(
                    "Broker Commission (${commBps.toInt()} bps)",
                    formatUsdPrice(commUsd)
                )
                OrderSummaryRow(
                    if (side == "BUY") "Total Debit" else "Net Proceeds",
                    formatUsdPrice(netTotalUsd),
                    highlight = true
                )
                OrderSummaryRow(
                    "Available Buying Power",
                    formatUsdPrice(cashBalanceUsd)
                )

                Spacer(modifier = Modifier.height(16.dp))

                val canSubmit = !isSubmitting && orderQty > 0.0 && !isSymbolHalted &&
                    (side == "BUY" || (holding != null && holding.quantity >= orderQty - 1e-6))

                if (isSymbolHalted) {
                    Text(
                        text = "Trading on ${asset.symbol} is currently halted by Brokerage Administration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CrimsonLoss
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                } else {
                    Text(
                        text = "Risk Disclosure: Financial instruments fluctuate in value. Limit/Stop orders rest as PENDING until live quote crosses target.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = {
                        onExecuteOrder(side, orderType, orderQty, effectiveExecutionPrice, effectiveTriggerPrice)
                    },
                    enabled = canSubmit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (side == "BUY") EmeraldAlpha else CrimsonLoss,
                        contentColor = if (side == "BUY") MaterialTheme.colorScheme.onPrimary else Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("execute_trade_button")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text(
                            text = if (isSymbolHalted) "Instrument Halted" else "$side ${asset.symbol}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Real-Time Market Fundamentals & Analytics Card
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = ObsidianSurface,
            border = BorderStroke(1.dp, ObsidianOutline),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Market Analytics & Fundamentals",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricItem("24h High", formatUsdPrice(asset.dayHighUsd), Modifier.weight(1f))
                    MetricItem("24h Low", formatUsdPrice(asset.dayLowUsd), Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricItem("52W High", formatUsdPrice(asset.week52HighUsd), Modifier.weight(1f))
                    MetricItem("52W Low", formatUsdPrice(asset.week52LowUsd), Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricItem(
                        "Market Cap",
                        if (asset.marketCapUsd > 0) formatCurrencyCompact(asset.marketCapUsd) else "FX Spot",
                        Modifier.weight(1f)
                    )
                    MetricItem("24h Volume", formatCurrencyCompact(asset.volume24hUsd), Modifier.weight(1f))
                }
                if (asset.peRatio != null || asset.dividendYieldPct != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MetricItem("P/E Ratio", asset.peRatio?.let { String.format("%.1fx", it) } ?: "—", Modifier.weight(1f))
                        MetricItem("Yield / Staking", asset.dividendYieldPct?.let { String.format("%.2f%%", it) } ?: "—", Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = ObsidianOutline)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = asset.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OrderSummaryRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MonoPriceStyle.copy(
                fontSize = MaterialTheme.typography.bodyMedium.fontSize,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (highlight) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MetricItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MonoPriceStyle.copy(
                fontSize = MaterialTheme.typography.titleMedium.fontSize,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
