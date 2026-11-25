package com.cs22study.easygopedometer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

val BrightYellow = Color(0xFFFFD600)

/**
 * Compact bar sparkline inspired by dark trend cards (Images 3 & 4).
 */
@Composable
fun MiniSparklineBars(
    values: List<Long>,
    accentColor: Color = BrightYellow,
    modifier: Modifier = Modifier.size(width = 108.dp, height = 38.dp),
) {
    Canvas(modifier = modifier) {
        val count = values.size.coerceAtLeast(1)
        val maxVal = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
        val slotW = size.width / count.toFloat()
        val barW = (slotW * 0.56f).coerceAtLeast(2.dp.toPx())
        val baseY = size.height

        values.forEachIndexed { idx, v ->
            val cx = slotW * (idx + 0.5f)
            if (v <= 0L) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.25f),
                    radius = 1.4.dp.toPx(),
                    center = Offset(cx, baseY - 2.dp.toPx()),
                )
            } else {
                val h = (size.height * (v.toFloat() / maxVal.toFloat())).coerceIn(4.dp.toPx(), size.height)
                drawRoundRect(
                    color = accentColor,
                    topLeft = Offset(cx - barW / 2f, baseY - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }
    }
}

/**
 * Cycling speedometer arc gauge (0 - 60 km/h) with bright yellow bold center readout.
 */
@Composable
fun SpeedometerGauge(
    speedKmh: Double,
    maxScaleKmh: Float = 60f,
    modifier: Modifier = Modifier.size(230.dp),
) {
    val fraction = (speedKmh.toFloat() / maxScaleKmh).coerceIn(0f, 1f)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeW = 14.dp.toPx()
            val pad = strokeW / 2f + 8.dp.toPx()
            val arcSize = Size(size.width - pad * 2, size.height - pad * 2)
            val topLeft = Offset(pad, pad)

            // Track arc
            drawArc(
                color = Color(0xFF2D333B),
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeW, cap = StrokeCap.Round),
            )

            // Active speed arc
            if (fraction > 0f) {
                drawArc(
                    color = BrightYellow,
                    startAngle = 140f,
                    sweepAngle = 260f * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeW, cap = StrokeCap.Round),
                )
            }

            // Needle dot at arc tip
            val angleDeg = 140f + 260f * fraction
            val rad = Math.toRadians(angleDeg.toDouble())
            val radius = arcSize.width / 2f
            val cx = size.width / 2f + (radius * cos(rad)).toFloat()
            val cy = size.height / 2f + (radius * sin(rad)).toFloat()
            drawCircle(color = Color.White, radius = 6.dp.toPx(), center = Offset(cx, cy))

            // Scale labels: 0, mid, maxScaleKmh
            val scalePaint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(215, 255, 214, 0)
                textSize = 11.dp.toPx()
                isFakeBoldText = true
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val labelR = radius + 16.dp.toPx()
            listOf(0f to "0", 0.5f to "${(maxScaleKmh / 2f).toInt()}", 1f to "${maxScaleKmh.toInt()}").forEach { (f, txt) ->
                val aRad = Math.toRadians((140f + 260f * f).toDouble())
                val lx = size.width / 2f + (labelR * cos(aRad)).toFloat()
                val ly = size.height / 2f + (labelR * sin(aRad)).toFloat() + 4.dp.toPx()
                drawContext.canvas.nativeCanvas.drawText(txt, lx, ly, scalePaint)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "%.1f".format(speedKmh),
                color = BrightYellow,
                fontSize = 54.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "km/h",
                color = BrightYellow.copy(alpha = 0.85f),
                fontSize = 18.sp,
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
        // Frame
        val crank = Offset(w * 0.48f, h * 0.68f)
        val seat = Offset(w * 0.42f, h * 0.38f)
        val stem = Offset(w * 0.65f, h * 0.38f)
        drawLine(tint, leftWheel, crank, strokeWidth = stroke)
        drawLine(tint, leftWheel, seat, strokeWidth = stroke)
        drawLine(tint, crank, seat, strokeWidth = stroke)
        drawLine(tint, seat, stem, strokeWidth = stroke)
        drawLine(tint, crank, stem, strokeWidth = stroke)
        drawLine(tint, stem, rightWheel, strokeWidth = stroke)
        // Handlebar & saddle
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
