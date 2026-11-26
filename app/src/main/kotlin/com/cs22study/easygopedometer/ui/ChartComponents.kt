package com.cs22study.easygopedometer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

val BrightYellow = Color(0xFFFFD600)

/**
 * Compact bar sparkline inspired by dark trend cards.
 */
@Composable
fun MiniSparklineBars(
    values: List<Long>,
    accentColor: Color = BrightYellow,
    modifier: Modifier = Modifier.size(width = 88.dp, height = 36.dp),
) {
    Canvas(modifier = modifier) {
        val count = values.size.coerceAtLeast(1)
        val maxVal = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
        val slotW = size.width / count.toFloat()
        val barW = (slotW * 0.54f).coerceAtLeast(2.dp.toPx())
        val baseY = size.height

        values.forEachIndexed { idx, v ->
            val cx = slotW * (idx + 0.5f)
            if (v <= 0L) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.22f),
                    radius = 1.3.dp.toPx(),
                    center = Offset(cx, baseY - 2.dp.toPx()),
                )
            } else {
                val h = (size.height * (v.toFloat() / maxVal.toFloat())).coerceIn(4.dp.toPx(), size.height)
                val barColor = if (v == maxVal || idx == values.lastIndex) {
                    accentColor
                } else {
                    accentColor.copy(alpha = 0.55f)
                }
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(cx - barW / 2f, baseY - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }
    }
}

/**
 * Dual-display analog needle + digital speedometer dial (270-degree sweep with 40 ticks & 5 labels).
 */
@Composable
fun SpeedometerGauge(
    speedKmh: Double,
    maxScaleKmh: Float = 60f,
    tickLabels: List<Int> = listOf(0, 15, 30, 45, 60),
    modifier: Modifier = Modifier.size(258.dp),
) {
    val targetFraction = (speedKmh.toFloat() / maxScaleKmh).coerceIn(0f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = tween(durationMillis = 420),
        label = "speedNeedleAnim",
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val startAngle = 135f
            val sweepTotal = 270f
            val strokeW = 10.dp.toPx()
            val outerPad = 18.dp.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            val arcRadius = (size.minDimension / 2f) - outerPad
            val arcTopLeft = Offset(center.x - arcRadius, center.y - arcRadius)
            val arcSize = Size(arcRadius * 2f, arcRadius * 2f)

            // Subtle inner dark face
            drawCircle(
                color = Color(0xFF141922),
                radius = arcRadius,
                center = center,
            )

            // Background arc track
            drawArc(
                color = Color(0xFF28303D),
                startAngle = startAngle,
                sweepAngle = sweepTotal,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round),
            )

            // Active progress arc
            if (animatedFraction > 0f) {
                drawArc(
                    color = BrightYellow,
                    startAngle = startAngle,
                    sweepAngle = sweepTotal * animatedFraction,
                    useCenter = false,
                    topLeft = arcTopLeft,
                    size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round),
                )
            }

            // 40 tick marks (every 10th is a major tick)
            val totalTicks = 40
            for (i in 0..totalTicks) {
                val frac = i / totalTicks.toFloat()
                val deg = startAngle + sweepTotal * frac
                val rad = Math.toRadians(deg.toDouble())
                val isMajor = i % 10 == 0
                val isMid = i % 5 == 0
                val tickOuter = arcRadius - strokeW / 2f - 4.dp.toPx()
                val tickLen = when {
                    isMajor -> 12.dp.toPx()
                    isMid -> 8.dp.toPx()
                    else -> 5.dp.toPx()
                }
                val tickInner = tickOuter - tickLen
                val activeTick = frac <= animatedFraction + 0.002f
                drawLine(
                    color = when {
                        activeTick -> BrightYellow
                        isMajor -> Color.White.copy(alpha = 0.72f)
                        else -> Color.White.copy(alpha = 0.28f)
                    },
                    start = Offset(
                        center.x + (tickInner * cos(rad)).toFloat(),
                        center.y + (tickInner * sin(rad)).toFloat(),
                    ),
                    end = Offset(
                        center.x + (tickOuter * cos(rad)).toFloat(),
                        center.y + (tickOuter * sin(rad)).toFloat(),
                    ),
                    strokeWidth = if (isMajor) 2.2.dp.toPx() else 1.2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // 5 major scale labels inside the ring
            val labelPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(230, 255, 214, 0)
                textSize = 11.5.dp.toPx()
                isFakeBoldText = true
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val labelRadius = arcRadius - 30.dp.toPx()
            val labelCount = (tickLabels.size - 1).coerceAtLeast(1)
            tickLabels.forEachIndexed { idx, value ->
                val frac = idx / labelCount.toFloat()
                val rad = Math.toRadians((startAngle + sweepTotal * frac).toDouble())
                val lx = center.x + (labelRadius * cos(rad)).toFloat()
                val ly = center.y + (labelRadius * sin(rad)).toFloat() + 4.dp.toPx()
                drawContext.canvas.nativeCanvas.drawText(value.toString(), lx, ly, labelPaint)
            }

            // Tapered Analog Needle rotating around center pivot
            val needleAngleRad = Math.toRadians((startAngle + sweepTotal * animatedFraction).toDouble())
            val perpRad = needleAngleRad + Math.PI / 2.0
            val needleLen = arcRadius - 22.dp.toPx()
            val tailLen = 14.dp.toPx()
            val baseHalfW = 4.5.dp.toPx()

            val tip = Offset(
                center.x + (needleLen * cos(needleAngleRad)).toFloat(),
                center.y + (needleLen * sin(needleAngleRad)).toFloat(),
            )
            val leftBase = Offset(
                center.x - (tailLen * cos(needleAngleRad)).toFloat() + (baseHalfW * cos(perpRad)).toFloat(),
                center.y - (tailLen * sin(needleAngleRad)).toFloat() + (baseHalfW * sin(perpRad)).toFloat(),
            )
            val rightBase = Offset(
                center.x - (tailLen * cos(needleAngleRad)).toFloat() - (baseHalfW * cos(perpRad)).toFloat(),
                center.y - (tailLen * sin(needleAngleRad)).toFloat() - (baseHalfW * sin(perpRad)).toFloat(),
            )

            val needlePath = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(leftBase.x, leftBase.y)
                lineTo(rightBase.x, rightBase.y)
                close()
            }
            drawPath(needlePath, color = Color(0xFFFF5252))

            // Center pivot hub
            drawCircle(color = BrightYellow, radius = 10.dp.toPx(), center = center)
            drawCircle(color = Color(0xFF141922), radius = 4.5.dp.toPx(), center = center)
        }

        // Digital numeric speed readout positioned cleanly in the bottom-center gap below pivot
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "%.1f".format(speedKmh),
                color = BrightYellow,
                style = TextStyle(
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = "tnum",
                ),
            )
            Text(
                text = "km/h",
                color = BrightYellow.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun FootprintIcon(modifier: Modifier = Modifier.size(22.dp), tint: Color = BrightYellow) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawOval(color = tint, topLeft = Offset(w * 0.14f, h * 0.12f), size = Size(w * 0.28f, h * 0.44f))
        drawOval(color = tint, topLeft = Offset(w * 0.18f, h * 0.58f), size = Size(w * 0.20f, h * 0.22f))
        drawOval(color = tint, topLeft = Offset(w * 0.56f, h * 0.22f), size = Size(w * 0.28f, h * 0.44f))
        drawOval(color = tint, topLeft = Offset(w * 0.58f, h * 0.68f), size = Size(w * 0.20f, h * 0.22f))
    }
}

@Composable
fun FlameIcon(modifier: Modifier = Modifier.size(22.dp), tint: Color = Color(0xFFFF9800)) {
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
fun RulerIcon(modifier: Modifier = Modifier.size(22.dp), tint: Color = Color(0xFF60A5FA)) {
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
fun StopwatchIcon(modifier: Modifier = Modifier.size(22.dp), tint: Color = Color(0xFF4ADE80)) {
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
fun BikeIcon(modifier: Modifier = Modifier.size(24.dp), tint: Color = BrightYellow) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        val wheelR = w * 0.17f
        val leftWheel = Offset(w * 0.24f, h * 0.68f)
        val rightWheel = Offset(w * 0.76f, h * 0.68f)
        drawCircle(tint, radius = wheelR, center = leftWheel, style = Stroke(stroke))
        drawCircle(tint, radius = wheelR, center = rightWheel, style = Stroke(stroke))
        val crank = Offset(w * 0.48f, h * 0.68f)
        val seat = Offset(w * 0.42f, h * 0.38f)
        val stem = Offset(w * 0.65f, h * 0.38f)
        drawLine(tint, leftWheel, crank, strokeWidth = stroke)
        drawLine(tint, leftWheel, seat, strokeWidth = stroke)
        drawLine(tint, crank, seat, strokeWidth = stroke)
        drawLine(tint, seat, stem, strokeWidth = stroke)
        drawLine(tint, crank, stem, strokeWidth = stroke)
        drawLine(tint, stem, rightWheel, strokeWidth = stroke)
        drawLine(tint, Offset(w * 0.36f, h * 0.36f), Offset(w * 0.48f, h * 0.36f), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.62f, h * 0.32f), Offset(w * 0.72f, h * 0.32f), strokeWidth = stroke, cap = StrokeCap.Round)
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
