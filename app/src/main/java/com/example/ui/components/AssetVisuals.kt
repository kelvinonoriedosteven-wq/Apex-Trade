package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.MarketAsset
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianOutline
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

@Composable
fun AssetLogoBadge(
    asset: MarketAsset,
    size: Dp = 44.dp,
    showCountryBadge: Boolean = true
) {
    val brandColor = Color(asset.brandHex)
    var imageFailed by remember(asset.logoUrl) { mutableStateOf(false) }

    Box(modifier = Modifier.size(size)) {
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.96f),
            border = BorderStroke(1.5.dp, brandColor.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxSize()
        ) {
            if (!imageFailed && asset.logoUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(asset.logoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "${asset.name} official logo",
                    contentScale = ContentScale.Crop,
                    onError = { imageFailed = true },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (asset.coinGeckoId != null) 2.dp else 5.dp)
                        .clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(brandColor, brandColor.copy(alpha = 0.65f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val initials = asset.symbol
                        .substringBefore(".")
                        .substringBefore("-")
                        .take(3)
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        fontSize = (size.value * 0.28f).sp
                    )
                }
            }
        }

        if (showCountryBadge) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = ObsidianBg,
                border = BorderStroke(1.dp, ObsidianOutline),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 3.dp, y = 2.dp)
            ) {
                Text(
                    text = asset.countryFlag,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 0.5.dp)
                )
            }
        }
    }
}

fun formatUsdPrice(price: Double): String {
    return when {
        price >= 1000.0 -> {
            val nf = NumberFormat.getNumberInstance(Locale.US).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }
            "$${nf.format(price)}"
        }
        price >= 1.0 -> String.format(Locale.US, "$%.2f", price)
        else -> String.format(Locale.US, "$%.4f", price)
    }
}

fun formatCurrencyCompact(value: Double): String {
    val absVal = abs(value)
    val sign = if (value < 0) "-" else ""
    return when {
        absVal >= 1_000_000_000_000.0 -> String.format(Locale.US, "%s$%.2fT", sign, absVal / 1_000_000_000_000.0)
        absVal >= 1_000_000_000.0 -> String.format(Locale.US, "%s$%.2fB", sign, absVal / 1_000_000_000.0)
        absVal >= 1_000_000.0 -> String.format(Locale.US, "%s$%.2fM", sign, absVal / 1_000_000.0)
        else -> {
            val nf = NumberFormat.getNumberInstance(Locale.US).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }
            "$sign$${nf.format(absVal)}"
        }
    }
}

fun formatSignedPercent(pct: Double): String {
    val prefix = if (pct >= 0.0) "+" else ""
    return String.format(Locale.US, "%s%.2f%%", prefix, pct)
}

fun formatQuantity(qty: Double): String {
    return if (qty >= 100.0) {
        String.format(Locale.US, "%.2f", qty)
    } else if (qty >= 1.0) {
        String.format(Locale.US, "%.4f", qty).trimEnd('0').trimEnd('.')
    } else {
        String.format(Locale.US, "%.6f", qty).trimEnd('0').trimEnd('.')
    }
}
