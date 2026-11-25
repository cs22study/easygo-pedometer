package com.cs22study.easygopedometer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.cs22study.easygopedometer.calc.BarItem
import com.cs22study.easygopedometer.calc.HourlyPoint
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

private val ChartPlotBg = Color.White.copy(alpha = 0.22f)
private val ChartGridLine = Color.White.copy(alpha = 0.28f)
private val ChartLabelColor = android.graphics.Color.argb(210, 35, 62, 102)

private fun computeNiceYMax(maxVal: Long, defaultMax: Long = 12000L): Long {
    if (maxVal <= defaultMax) return defaultMax
    val step = 4000L
    return ((maxVal + step - 1) / step) * step
}

@Composable
fun DayCumulativeLineChart(
    points: List<HourlyPoint>,
    modifier: Modifier = Modifier,
) {
    val maxData = points.maxOfOrNull { it.cumulativeSteps } ?: 0L
    val yMax = computeNiceYMax(maxData, 12000L)

    Box(modifier = modifier.fillMaxWidth().height(230.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val leftPad = 56.dp.toPx()
            val rightPad = 14.dp.toPx()
            val topPad = 12.dp.toPx()
            val bottomPad = 28.dp.toPx()

            val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
            val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

            drawRect(
                color = ChartPlotBg,
                topLeft = Offset(leftPad, topPad),
                size = Size(plotW, plotH),
            )

            val labelPaint = android.graphics.Paint().apply {
                color = ChartLabelColor
                textSize = 11.dp.toPx()
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.RIGHT
            }

            // 6 horizontal gridlines (0 .. yMax)
            val levels = 6
            for (i in 0..levels) {
                val frac = i / levels.toFloat()
                val y = topPad + plotH * (1f - frac)
                drawLine(
                    color = ChartGridLine,
                    start = Offset(leftPad, y),
                    end = Offset(leftPad + plotW, y),
                    strokeWidth = 1.dp.toPx(),
                )
                if (i > 0) {
                    val valLabel = ((yMax * i) / levels).toString()
                    drawContext.canvas.nativeCanvas.drawText(
                        valLabel,
                        leftPad - 6.dp.toPx(),
                        y + 4.dp.toPx(),
                        labelPaint,
                    )
                }
            }

            // X-axis labels 2, 4, ..., 24
            val xPaint = android.graphics.Paint().apply {
                color = ChartLabelColor
                textSize = 11.dp.toPx()
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            for (h in 2..24 step 2) {
                val x = leftPad + (h / 24f) * plotW
                drawContext.canvas.nativeCanvas.drawText(
                    h.toString(),
                    x,
                    topPad + plotH + 18.dp.toPx(),
                    xPaint,
                )
            }

            if (points.isEmpty()) return@Canvas

            val linePath = Path()
            val fillPath = Path()
            val coords = points.map { pt ->
                val x = leftPad + (pt.hour / 24f) * plotW
                val y = topPad + plotH * (1f - (pt.cumulativeSteps.toFloat() / yMax.toFloat()).coerceIn(0f, 1f))
                Offset(x, y)
            }

            coords.forEachIndexed { idx, offset ->
                if (idx == 0) {
                    linePath.moveTo(offset.x, offset.y)
                    fillPath.moveTo(offset.x, topPad + plotH)
                    fillPath.lineTo(offset.x, offset.y)
                } else {
                    linePath.lineTo(offset.x, offset.y)
                    fillPath.lineTo(offset.x, offset.y)
                }
            }
            val last = coords.last()
            fillPath.lineTo(last.x, topPad + plotH)
            fillPath.close()

            drawPath(fillPath, color = Color.White.copy(alpha = 0.25f))
            drawPath(
                linePath,
                color = Color.White,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            )
            coords.forEach { offset ->
                drawCircle(color = Color.White, radius = 3.2.dp.toPx(), center = offset)
            }
        }
    }
}

@Composable
fun PeriodBarChart(
    bars: List<BarItem>,
    defaultYMax: Long = 12000L,
    modifier: Modifier = Modifier,
) {
    val maxData = bars.maxOfOrNull { it.steps } ?: 0L
    val yMax = computeNiceYMax(maxData, defaultYMax)

    Box(modifier = modifier.fillMaxWidth().height(230.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val leftPad = 56.dp.toPx()
            val rightPad = 14.dp.toPx()
            val topPad = 12.dp.toPx()
            val bottomPad = 28.dp.toPx()

            val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
            val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

            drawRect(
                color = ChartPlotBg,
                topLeft = Offset(leftPad, topPad),
                size = Size(plotW, plotH),
            )

            val labelPaint = android.graphics.Paint().apply {
                color = ChartLabelColor
                textSize = 11.dp.toPx()
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.RIGHT
            }

            val levels = 6
            for (i in 0..levels) {
                val frac = i / levels.toFloat()
                val y = topPad + plotH * (1f - frac)
                drawLine(
                    color = ChartGridLine,
                    start = Offset(leftPad, y),
                    end = Offset(leftPad + plotW, y),
                    strokeWidth = 1.dp.toPx(),
                )
                if (i > 0) {
                    val valLabel = ((yMax * i) / levels).toString()
                    drawContext.canvas.nativeCanvas.drawText(
                        valLabel,
                        leftPad - 6.dp.toPx(),
                        y + 4.dp.toPx(),
                        labelPaint,
                    )
                }
            }

            if (bars.isEmpty()) return@Canvas

            val slotW = plotW / bars.size.toFloat()
            val barW = (slotW * 0.62f).coerceAtLeast(2.dp.toPx())
            val xPaint = android.graphics.Paint().apply {
                color = ChartLabelColor
                textSize = if (bars.size == 12) 9.dp.toPx() else 10.5.dp.toPx()
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }

            bars.forEachIndexed { idx, item ->
                val cx = leftPad + slotW * (idx + 0.5f)
                val barH = plotH * (item.steps.toFloat() / yMax.toFloat()).coerceIn(0f, 1f)
                if (barH > 0f) {
                    drawRect(
                        color = Color.White.copy(alpha = 0.88f),
                        topLeft = Offset(cx - barW / 2f, topPad + plotH - barH),
                        size = Size(barW, barH),
                    )
                }
                if (item.label.isNotBlank()) {
                    drawContext.canvas.nativeCanvas.drawText(
                        item.label,
                        cx,
                        topPad + plotH + 18.dp.toPx(),
                        xPaint,
                    )
                }
            }
        }
    }
}

@Composable
fun FootprintIcon(modifier: Modifier = Modifier.size(36.dp), tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Left sole
        drawOval(color = tint, topLeft = Offset(w * 0.14f, h * 0.12f), size = Size(w * 0.28f, h * 0.44f))
        drawOval(color = tint, topLeft = Offset(w * 0.18f, h * 0.58f), size = Size(w * 0.20f, h * 0.22f))
        // Right sole (slightly offset lower)
        drawOval(color = tint, topLeft = Offset(w * 0.56f, h * 0.22f), size = Size(w * 0.28f, h * 0.44f))
        drawOval(color = tint, topLeft = Offset(w * 0.58f, h * 0.68f), size = Size(w * 0.20f, h * 0.22f))
    }
}

@Composable
fun FlameIcon(modifier: Modifier = Modifier.size(22.dp), tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.52f, h * 0.08f)
            cubicTo(w * 0.80f, h * 0.34f, w * 0.88f, h * 0.65f, w * 0.68f, h * 0.86f)
            cubicTo(w * 0.58f, h * 0.95f, w * 0.40f, h * 0.95f, w * 0.30f, h * 0.84f)
            cubicTo(w * 0.14f, h * 0.68f, w * 0.20f, h * 0.40f, w * 0.40f, h * 0.28f)
            cubicTo(w * 0.36f, h * 0.42f, w * 0.48f, h * 0.46f, w * 0.52f, h * 0.08f)
            close()
        }
        drawPath(path, color = tint)
    }
}

@Composable
fun RulerIcon(modifier: Modifier = Modifier.size(22.dp), tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.08f, h * 0.28f),
            size = Size(w * 0.84f, h * 0.44f),
            cornerRadius = CornerRadius(2.dp.toPx()),
            style = Stroke(width = stroke),
        )
        for (i in 1..4) {
            val x = w * (0.08f + i * 0.168f)
            val tickTop = if (i % 2 == 0) h * 0.28f else h * 0.38f
            drawLine(tint, Offset(x, tickTop), Offset(x, h * 0.54f), strokeWidth = stroke * 0.8f)
        }
    }
}

@Composable
fun StopwatchIcon(modifier: Modifier = Modifier.size(22.dp), tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        drawLine(tint, Offset(w * 0.40f, h * 0.10f), Offset(w * 0.60f, h * 0.10f), strokeWidth = stroke)
        drawCircle(color = tint, radius = w * 0.36f, center = Offset(w * 0.5f, h * 0.56f), style = Stroke(stroke))
        drawLine(tint, Offset(w * 0.5f, h * 0.56f), Offset(w * 0.5f, h * 0.34f), strokeWidth = stroke)
        drawLine(tint, Offset(w * 0.5f, h * 0.56f), Offset(w * 0.66f, h * 0.60f), strokeWidth = stroke)
    }
}

@Composable
fun SpeedometerIcon(modifier: Modifier = Modifier.size(22.dp), tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        drawArc(
            color = tint,
            startAngle = 150f,
            sweepAngle = 240f,
            useCenter = false,
            topLeft = Offset(w * 0.12f, h * 0.18f),
            size = Size(w * 0.76f, h * 0.76f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        val angleRad = Math.toRadians(-45.0)
        val cx = w * 0.5f
        val cy = h * 0.58f
        drawLine(
            color = tint,
            start = Offset(cx, cy),
            end = Offset(cx + (w * 0.24f * cos(angleRad)).toFloat(), cy + (w * 0.24f * sin(angleRad)).toFloat()),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawCircle(tint, radius = 2.2.dp.toPx(), center = Offset(cx, cy))
    }
}

@Composable
fun CameraIcon(modifier: Modifier = Modifier.size(22.dp), tint: Color = Color.White) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.10f, h * 0.28f),
            size = Size(w * 0.80f, h * 0.54f),
            cornerRadius = CornerRadius(3.dp.toPx()),
            style = Stroke(width = stroke),
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.34f, h * 0.16f),
            size = Size(w * 0.32f, h * 0.12f),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )
        drawCircle(color = tint, radius = w * 0.15f, center = Offset(w * 0.5f, h * 0.55f), style = Stroke(stroke))
    }
}
