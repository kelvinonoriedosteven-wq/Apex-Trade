package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.data.model.CandlePoint
import com.example.data.model.PricePoint
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CrimsonLoss
import com.example.ui.theme.EmeraldAlpha
import com.example.ui.theme.ObsidianOutline
import com.example.ui.theme.SkySignal
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun MiniSparklineChart(
    prices: List<Double>,
    isPositive: Boolean,
    modifier: Modifier = Modifier
) {
    val lineColor = if (isPositive) EmeraldAlpha else CrimsonLoss
    Canvas(modifier = modifier) {
        if (prices.size < 2) return@Canvas
        val minP = prices.min()
        val maxP = prices.max()
        val range = (maxP - minP).coerceAtLeast(minP * 0.001 + 1e-6)
        val stepX = size.width / (prices.size - 1)
        val path = Path()
        val fillPath = Path()

        prices.forEachIndexed { idx, price ->
            val x = idx * stepX
            val normalizedY = ((price - minP) / range).toFloat()
            val y = size.height - (normalizedY * (size.height * 0.84f)) - (size.height * 0.08f)
            if (idx == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, size.height)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo(size.width, size.height)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent)
            )
        )
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun InteractiveMarketChart(
    linePoints: List<PricePoint>,
    candles: List<CandlePoint>,
    previousClose: Double,
    isPositive: Boolean,
    showCandlesticks: Boolean,
    showMovingAverage: Boolean,
    scrubIndex: Int?,
    onScrubIndexChanged: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (isPositive) EmeraldAlpha else CrimsonLoss

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(linePoints.size, candles.size, showCandlesticks) {
                    val count = if (showCandlesticks) candles.size else linePoints.size
                    if (count < 2) return@pointerInput
                    detectTapGestures(
                        onPress = { offset ->
                            val idx = ((offset.x / size.width) * (count - 1)).roundToInt().coerceIn(0, count - 1)
                            onScrubIndexChanged(idx)
                            tryAwaitRelease()
                            onScrubIndexChanged(null)
                        }
                    )
                }
                .pointerInput(linePoints.size, candles.size, showCandlesticks) {
                    val count = if (showCandlesticks) candles.size else linePoints.size
                    if (count < 2) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            val idx = ((offset.x / size.width) * (count - 1)).roundToInt().coerceIn(0, count - 1)
                            onScrubIndexChanged(idx)
                        },
                        onHorizontalDrag = { change, _ ->
                            val idx = ((change.position.x / size.width) * (count - 1)).roundToInt().coerceIn(0, count - 1)
                            onScrubIndexChanged(idx)
                        },
                        onDragEnd = { onScrubIndexChanged(null) },
                        onDragCancel = { onScrubIndexChanged(null) }
                    )
                }
        ) {
            val chartHeight = size.height * 0.80f
            val volTop = size.height * 0.84f
            val volHeight = size.height * 0.16f

            // Subtle horizontal grid lines
            for (i in 1..3) {
                val y = chartHeight * (i / 4f)
                drawLine(
                    color = ObsidianOutline.copy(alpha = 0.55f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            if (showCandlesticks && candles.size >= 2) {
                val minP = min(candles.minOf { it.low }, previousClose)
                val maxP = max(candles.maxOf { it.high }, previousClose)
                val range = (maxP - minP).coerceAtLeast(minP * 0.001 + 1e-6)
                val maxVol = candles.maxOf { it.volume }.coerceAtLeast(1.0)
                val slotW = size.width / candles.size
                val bodyW = (slotW * 0.58f).coerceIn(3.dp.toPx(), 14.dp.toPx())

                fun yForPrice(p: Double): Float {
                    val norm = ((p - minP) / range).toFloat()
                    return chartHeight - (norm * (chartHeight * 0.88f)) - (chartHeight * 0.06f)
                }

                // Previous close dashed line
                val prevY = yForPrice(previousClose)
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(0f, prevY),
                    end = Offset(size.width, prevY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )

                candles.forEachIndexed { i, candle ->
                    val cx = i * slotW + slotW / 2f
                    val isBull = candle.close >= candle.open
                    val color = if (isBull) EmeraldAlpha else CrimsonLoss

                    // Wick
                    drawLine(
                        color = color,
                        start = Offset(cx, yForPrice(candle.high)),
                        end = Offset(cx, yForPrice(candle.low)),
                        strokeWidth = 1.6.dp.toPx()
                    )

                    // Candle body
                    val topY = yForPrice(max(candle.open, candle.close))
                    val botY = yForPrice(min(candle.open, candle.close))
                    drawRect(
                        color = color,
                        topLeft = Offset(cx - bodyW / 2f, topY),
                        size = Size(bodyW, (botY - topY).coerceAtLeast(2.dp.toPx()))
                    )

                    // Volume bar
                    val vH = ((candle.volume / maxVol) * volHeight).toFloat().coerceAtLeast(2f)
                    drawRect(
                        color = color.copy(alpha = 0.35f),
                        topLeft = Offset(cx - bodyW / 2f, size.height - vH),
                        size = Size(bodyW, vH)
                    )
                }

                if (showMovingAverage && candles.size >= 6) {
                    val maPath = Path()
                    val window = 5
                    candles.forEachIndexed { idx, _ ->
                        val startIdx = (idx - window + 1).coerceAtLeast(0)
                        val avg = candles.subList(startIdx, idx + 1).map { it.close }.average()
                        val cx = idx * slotW + slotW / 2f
                        val cy = yForPrice(avg)
                        if (idx == 0) maPath.moveTo(cx, cy) else maPath.lineTo(cx, cy)
                    }
                    drawPath(
                        path = maPath,
                        color = ChampagneGold,
                        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                scrubIndex?.let { idx ->
                    val safeIdx = idx.coerceIn(0, candles.lastIndex)
                    val cx = safeIdx * slotW + slotW / 2f
                    val cy = yForPrice(candles[safeIdx].close)
                    drawLine(
                        color = Color.White.copy(alpha = 0.65f),
                        start = Offset(cx, 0f),
                        end = Offset(cx, size.height),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )
                    drawCircle(color = Color.White, radius = 5.dp.toPx(), center = Offset(cx, cy))
                    drawCircle(color = accent, radius = 3.dp.toPx(), center = Offset(cx, cy))
                }
            } else if (linePoints.size >= 2) {
                val minP = min(linePoints.minOf { it.price }, previousClose)
                val maxP = max(linePoints.maxOf { it.price }, previousClose)
                val range = (maxP - minP).coerceAtLeast(minP * 0.001 + 1e-6)
                val maxVol = linePoints.maxOf { it.volume }.coerceAtLeast(1.0)
                val stepX = size.width / (linePoints.size - 1)

                fun yForPrice(p: Double): Float {
                    val norm = ((p - minP) / range).toFloat()
                    return chartHeight - (norm * (chartHeight * 0.88f)) - (chartHeight * 0.06f)
                }

                val prevY = yForPrice(previousClose)
                drawLine(
                    color = Color.White.copy(alpha = 0.25f),
                    start = Offset(0f, prevY),
                    end = Offset(size.width, prevY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                )

                val linePath = Path()
                val fillPath = Path()
                linePoints.forEachIndexed { idx, pt ->
                    val x = idx * stepX
                    val y = yForPrice(pt.price)
                    if (idx == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, chartHeight)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }

                    // Volume histogram bar
                    val barW = (stepX * 0.55f).coerceIn(2.dp.toPx(), 10.dp.toPx())
                    val vH = ((pt.volume / maxVol) * volHeight).toFloat().coerceAtLeast(2f)
                    drawRect(
                        color = accent.copy(alpha = 0.28f),
                        topLeft = Offset((x - barW / 2f).coerceAtLeast(0f), size.height - vH),
                        size = Size(barW, vH)
                    )
                }
                fillPath.lineTo(size.width, chartHeight)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(accent.copy(alpha = 0.34f), Color.Transparent),
                        startY = 0f,
                        endY = chartHeight
                    )
                )
                drawPath(
                    path = linePath,
                    color = accent,
                    style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                if (showMovingAverage && linePoints.size >= 6) {
                    val maPath = Path()
                    val window = 6
                    linePoints.forEachIndexed { idx, _ ->
                        val startIdx = (idx - window + 1).coerceAtLeast(0)
                        val avg = linePoints.subList(startIdx, idx + 1).map { it.price }.average()
                        val x = idx * stepX
                        val y = yForPrice(avg)
                        if (idx == 0) maPath.moveTo(x, y) else maPath.lineTo(x, y)
                    }
                    drawPath(
                        path = maPath,
                        color = ChampagneGold,
                        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Live endpoint pulse dot
                val lastX = (linePoints.size - 1) * stepX
                val lastY = yForPrice(linePoints.last().price)
                drawCircle(color = accent.copy(alpha = 0.28f), radius = 8.dp.toPx(), center = Offset(lastX, lastY))
                drawCircle(color = accent, radius = 4.dp.toPx(), center = Offset(lastX, lastY))

                scrubIndex?.let { idx ->
                    val safeIdx = idx.coerceIn(0, linePoints.lastIndex)
                    val cx = safeIdx * stepX
                    val cy = yForPrice(linePoints[safeIdx].price)
                    drawLine(
                        color = Color.White.copy(alpha = 0.65f),
                        start = Offset(cx, 0f),
                        end = Offset(cx, size.height),
                        strokeWidth = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )
                    drawCircle(color = Color.White, radius = 6.dp.toPx(), center = Offset(cx, cy))
                    drawCircle(color = accent, radius = 3.5.dp.toPx(), center = Offset(cx, cy))
                }
            }
        }
    }
}

data class AllocationSlice(
    val label: String,
    val amountUsd: Double,
    val color: Color
)

@Composable
fun AllocationDonutChart(
    slices: List<AllocationSlice>,
    modifier: Modifier = Modifier
) {
    val total = slices.sumOf { it.amountUsd }.coerceAtLeast(1.0)
    Canvas(modifier = modifier) {
        val strokeWidth = 18.dp.toPx()
        val diameter = min(size.width, size.height) - strokeWidth
        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
        val arcSize = Size(diameter, diameter)

        if (slices.isEmpty() || slices.all { it.amountUsd <= 0.0 }) {
            drawArc(
                color = SkySignal.copy(alpha = 0.25f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            return@Canvas
        }

        var startAngle = -90f
        slices.filter { it.amountUsd > 0.0 }.forEach { slice ->
            val sweep = ((slice.amountUsd / total) * 360.0).toFloat()
            drawArc(
                color = slice.color,
                startAngle = startAngle,
                sweepAngle = (sweep - 2f).coerceAtLeast(2f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            startAngle += sweep
        }
    }
}
