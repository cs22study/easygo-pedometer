package com.cs22study.easygopedometer.ui

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cs22study.easygopedometer.calc.ActiveTimeTracker
import com.cs22study.easygopedometer.calc.DateDisplayFormatter
import com.cs22study.easygopedometer.calc.GpsRideState
import com.cs22study.easygopedometer.calc.SpeedMode
import com.cs22study.easygopedometer.service.CapturedScreenshot
import kotlinx.coroutines.delay
import java.time.LocalDate

private val GpsDarkBg = Color(0xFF0D1117)
private val GpsTopBar = Color(0xFF161B22)
private val GpsCardBg = Color(0xFF1C2128)
private val GpsCardBorder = Color(0xFF30363D)

@Composable
fun GpsSpeedometerScreen(
    rideState: GpsRideState,
    hasLocationPermission: Boolean,
    isGpsEnabled: Boolean,
    capturedScreenshot: CapturedScreenshot?,
    onBack: () -> Unit,
    onSelectSpeedMode: (SpeedMode) -> Unit,
    onRequestLocationPermission: () -> Unit,
    onStartRide: () -> Unit,
    onPauseRide: () -> Unit,
    onResetRide: () -> Unit,
    onScreenshotClick: () -> Unit,
    onViewScreenshot: (Uri) -> Unit,
    onShareScreenshot: (Uri) -> Unit,
    onDismissScreenshotPreview: () -> Unit,
) {
    val context = LocalContext.current

    // Keep screen awake while measuring speed
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(capturedScreenshot?.capturedAtMillis) {
        if (capturedScreenshot != null) {
            delay(8000L)
            onDismissScreenshotPreview()
        }
    }

    val gpsStatusText = when {
        !hasLocationPermission -> "未授權定位（點此授權）"
        !isGpsEnabled -> "請開啟手機 GPS 定位"
        rideState.lastAccuracyMeters != null -> "GPS 精度 ±%.0fm".format(rideState.lastAccuracyMeters)
        rideState.isTracking -> "搜尋 GPS 衛星中..."
        else -> "GPS 待命中"
    }

    val distanceText = if (rideState.distanceMeters >= 1000L) {
        "%.2f".format(rideState.distanceMeters / 1000.0) to "公里"
    } else {
        "%,d".format(rideState.distanceMeters) to "公尺"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GpsDarkBg),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar with Back, explicit today's date/weekday, and Screenshot button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GpsTopBar)
                    .statusBarsPadding()
                    .height(54.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回首頁",
                            tint = BrightYellow,
                        )
                    }
                    Column {
                        Text(
                            text = "GPS 即時時速表",
                            color = BrightYellow,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = DateDisplayFormatter.formatDayHeader(LocalDate.now()),
                            color = BrightYellow.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                IconButton(onClick = onScreenshotClick) {
                    CameraIcon(tint = BrightYellow)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 3-Mode Scale Selector: 單車(60) | 汽車火車(150) | 高鐵(400)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(GpsCardBg)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SpeedMode.entries.forEach { mode ->
                        val selected = mode == rideState.speedMode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) BrightYellow else Color.Transparent)
                                .clickable { onSelectSpeedMode(mode) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${mode.label}(${mode.maxScaleKmh.toInt()})",
                                color = if (selected) Color.Black else BrightYellow.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }

                // GPS Status Pill
                Surface(
                    color = GpsCardBg,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, if (hasLocationPermission) GpsCardBorder else BrightYellow),
                    onClick = { if (!hasLocationPermission) onRequestLocationPermission() },
                ) {
                    Text(
                        text = gpsStatusText,
                        color = BrightYellow,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }

                // Speedometer Gauge scaled dynamically by selected mode (60 / 150 / 400 km/h)
                SpeedometerGauge(
                    speedKmh = rideState.currentSpeedKmh,
                    maxScaleKmh = rideState.speedMode.maxScaleKmh,
                )

                // 2x2 Metrics Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GpsMetricCard(
                        title = "行程時間",
                        value = ActiveTimeTracker.formatDuration(rideState.elapsedSeconds),
                        unit = "時:分:秒",
                        modifier = Modifier.weight(1f),
                    )
                    GpsMetricCard(
                        title = "移動距離",
                        value = distanceText.first,
                        unit = distanceText.second,
                        modifier = Modifier.weight(1f),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GpsMetricCard(
                        title = "均速 / 極速",
                        value = "%.1f / %.1f".format(rideState.avgSpeedKmh, rideState.maxSpeedKmh),
                        unit = "km/h",
                        modifier = Modifier.weight(1f),
                    )
                    GpsMetricCard(
                        title = if (rideState.speedMode.trackCalories) "燃燒熱量" else "燃燒熱量(乘車不計)",
                        value = "%.1f".format(rideState.caloriesKcal),
                        unit = "千卡",
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Start / Pause & Reset Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = {
                            if (!hasLocationPermission) {
                                onRequestLocationPermission()
                            } else if (rideState.isTracking) {
                                onPauseRide()
                            } else {
                                onStartRide()
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrightYellow,
                            contentColor = Color.Black,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp),
                    ) {
                        Text(
                            text = when {
                                !hasLocationPermission -> "授權 GPS 定位"
                                rideState.isTracking -> "暫停測速"
                                else -> "開始測速"
                            },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    OutlinedButton(
                        onClick = onResetRide,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, BrightYellow.copy(alpha = 0.7f)),
                        modifier = Modifier.height(54.dp),
                    ) {
                        Text(
                            text = "歸零",
                            color = BrightYellow,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        if (capturedScreenshot != null) {
            ScreenshotPreviewCard(
                screenshot = capturedScreenshot,
                onView = { onViewScreenshot(capturedScreenshot.uri) },
                onShare = { onShareScreenshot(capturedScreenshot.uri) },
                onDismiss = onDismissScreenshotPreview,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(12.dp),
            )
        }
    }
}

@Composable
private fun GpsMetricCard(
    title: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = GpsCardBg),
        border = BorderStroke(1.dp, GpsCardBorder),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                color = BrightYellow.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    color = BrightYellow,
                    fontSize = if (value.length >= 9) 18.sp else 23.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    color = BrightYellow.copy(alpha = 0.82f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
        }
    }
}
