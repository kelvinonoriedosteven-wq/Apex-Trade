package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AssetClass
import com.example.data.model.MarketAsset
import com.example.data.model.MarketRegion
import com.example.ui.components.AssetLogoBadge
import com.example.ui.components.MiniSparklineChart
import com.example.ui.components.formatCurrencyCompact
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
import com.example.ui.viewmodel.BrokerUiState

@Composable
fun MarketsScreen(
    uiState: BrokerUiState,
    onSelectRegion: (MarketRegion) -> Unit,
    onSelectAssetClass: (AssetClass?) -> Unit,
    onSearchChange: (String) -> Unit,
    onToggleWatchlistOnly: () -> Unit,
    onToggleWatchlistAsset: (MarketAsset) -> Unit,
    onToggleTraderMode: () -> Unit,
    onRefreshMarkets: () -> Unit,
    onOpenNotifications: () -> Unit,
    onAssetClick: (MarketAsset) -> Unit
) {
    val featuredMovers = uiState.marketAssets
        .sortedByDescending { kotlin.math.abs(it.changePercent) }
        .take(6)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Top Header with Live Status & Beginner/Pro Toggle
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(EmeraldAlpha)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (uiState.isLiveFeedConnected) "LIVE GLOBAL FEED" else "REAL-TIME ENGINE",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldAlpha
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Worldwide Markets",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NotificationBellButton(
                            unreadCount = uiState.unreadNotificationCount,
                            onClick = onOpenNotifications
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (uiState.isProMode) ChampagneGold.copy(alpha = 0.16f) else ObsidianSurfaceElevated,
                            border = BorderStroke(
                                1.dp,
                                if (uiState.isProMode) ChampagneGold else ObsidianOutline
                            ),
                            modifier = Modifier
                                .clickable { onToggleTraderMode() }
                                .testTag("trader_mode_toggle")
                        ) {
                            Text(
                                text = if (uiState.isProMode) "PRO" else "SIMPLE",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (uiState.isProMode) ChampagneGold else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = onRefreshMarkets) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh live market quotes",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                uiState.activeAnnouncements.firstOrNull()?.let { ann ->
                    Spacer(modifier = Modifier.height(10.dp))
                    val annColor = when (ann.severity.uppercase()) {
                        "ALERT" -> CrimsonLoss
                        "WARNING" -> ChampagneGold
                        else -> EmeraldAlpha
                    }
                    Surface(
                        color = ObsidianSurface,
                        border = BorderStroke(1.dp, annColor.copy(alpha = 0.65f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = ann.title,
                                style = MaterialTheme.typography.labelLarge,
                                color = annColor,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = ann.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (uiState.brokerConfig.tradingHalted) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = CrimsonLossDim,
                        border = BorderStroke(1.dp, CrimsonLoss),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "TRADING HALTED BY ADMIN: Market orders are temporarily paused.",
                            style = MaterialTheme.typography.labelLarge,
                            color = CrimsonLoss,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = {
                        Text(
                            "Search ticker, country, exchange, or crypto...",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchChange("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search")
                                }
                            }
                            IconButton(
                                onClick = onToggleWatchlistOnly,
                                modifier = Modifier.testTag("watchlist_filter_button")
                            ) {
                                Icon(
                                    imageVector = if (uiState.watchlistOnly) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = "Filter watchlist",
                                    tint = if (uiState.watchlistOnly) ChampagneGold else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = ObsidianSurface,
                        unfocusedContainerColor = ObsidianSurface,
                        focusedBorderColor = EmeraldAlpha,
                        unfocusedBorderColor = ObsidianOutline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("market_search_input")
                )
            }
        }

        // Global Regions Carousel
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MarketRegion.entries.forEach { region ->
                    val selected = uiState.selectedRegion == region
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectRegion(region) },
                        label = {
                            Text(
                                text = region.displayName,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = ObsidianSurface,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedContainerColor = EmeraldAlphaDim,
                            selectedLabelColor = EmeraldAlpha
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = ObsidianOutline,
                            selectedBorderColor = EmeraldAlpha
                        )
                    )
                }
            }
        }

        // Asset Class Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssetClass.entries.forEach { assetClass ->
                    val selected = uiState.selectedAssetClass == assetClass
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) ChampagneGold.copy(alpha = 0.16f) else ObsidianSurfaceElevated,
                        border = BorderStroke(
                            1.dp,
                            if (selected) ChampagneGold else ObsidianOutline
                        ),
                        modifier = Modifier.clickable { onSelectAssetClass(assetClass) }
                    ) {
                        Text(
                            text = assetClass.displayLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) ChampagneGold else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Top Global Movers Carousel (when no search active)
        if (uiState.searchQuery.isBlank() && !uiState.watchlistOnly) {
            item {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        text = "Top Global Movers",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(featuredMovers, key = { "mover_${it.symbol}" }) { asset ->
                            MoverHighlightCard(
                                asset = asset,
                                onClick = { onAssetClick(asset) }
                            )
                        }
                    }
                }
            }
        }

        // Section Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${uiState.selectedRegion.displayName} (${uiState.filteredAssets.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Fee: ${uiState.brokerConfig.stockCommissionBps.toInt()}–${uiState.brokerConfig.cryptoCommissionBps.toInt()} bps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (uiState.filteredAssets.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = ObsidianSurface,
                    border = BorderStroke(1.dp, ObsidianOutline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No matching global assets",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Try clearing filters or searching another country, ticker, or cryptocurrency.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(uiState.filteredAssets, key = { it.symbol }) { asset ->
                val isWatched = asset.symbol in uiState.watchlistSymbols
                val isHalted = uiState.brokerConfig.tradingHalted || asset.symbol in uiState.brokerConfig.haltedSymbols
                AssetMarketRow(
                    asset = asset,
                    isWatched = isWatched,
                    isHalted = isHalted,
                    isProMode = uiState.isProMode,
                    onToggleWatch = { onToggleWatchlistAsset(asset) },
                    onClick = { onAssetClick(asset) }
                )
            }
        }
    }
}

@Composable
private fun MoverHighlightCard(
    asset: MarketAsset,
    onClick: () -> Unit
) {
    val trendColor = if (asset.isPositive) EmeraldAlpha else CrimsonLoss
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianOutline),
        modifier = Modifier
            .width(164.dp)
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                AssetLogoBadge(asset = asset, size = 36.dp)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (asset.isPositive) EmeraldAlphaDim else CrimsonLossDim
                ) {
                    Text(
                        text = formatSignedPercent(asset.changePercent),
                        style = MonoPriceStyle.copy(
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            fontWeight = FontWeight.Bold
                        ),
                        color = trendColor,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = asset.symbol,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${asset.exchange} • ${asset.countryName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            MiniSparklineChart(
                prices = asset.sparkline,
                isPositive = asset.isPositive,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = formatUsdPrice(asset.currentPriceUsd),
                style = MonoPriceStyle.copy(
                    fontSize = MaterialTheme.typography.titleMedium.fontSize,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AssetMarketRow(
    asset: MarketAsset,
    isWatched: Boolean,
    isHalted: Boolean,
    isProMode: Boolean,
    onToggleWatch: () -> Unit,
    onClick: () -> Unit
) {
    val trendColor = if (asset.isPositive) EmeraldAlpha else CrimsonLoss
    val trendBg = if (asset.isPositive) EmeraldAlphaDim else CrimsonLossDim

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = ObsidianSurface,
        border = BorderStroke(1.dp, ObsidianOutline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable { onClick() }
            .testTag("asset_row_${asset.symbol}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssetLogoBadge(asset = asset, size = 44.dp)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = asset.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isHalted) CrimsonLossDim else ObsidianSurfaceElevated
                    ) {
                        Text(
                            text = if (isHalted) "HALTED" else asset.exchange,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isHalted) CrimsonLoss else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = asset.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isProMode) {
                    Text(
                        text = "Vol ${formatCurrencyCompact(asset.volume24hUsd)} • ${asset.sector}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            MiniSparklineChart(
                prices = asset.sparkline,
                isPositive = asset.isPositive,
                modifier = Modifier
                    .width(56.dp)
                    .height(28.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatUsdPrice(asset.currentPriceUsd),
                    style = MonoPriceStyle.copy(
                        fontSize = MaterialTheme.typography.titleMedium.fontSize,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(3.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = trendBg
                ) {
                    Text(
                        text = formatSignedPercent(asset.changePercent),
                        style = MonoPriceStyle.copy(
                            fontSize = MaterialTheme.typography.labelSmall.fontSize,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = trendColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            IconButton(
                onClick = onToggleWatch,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (isWatched) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (isWatched) "Remove from watchlist" else "Add to watchlist",
                    tint = if (isWatched) ChampagneGold else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
