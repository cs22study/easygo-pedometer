package com.cs22study.easygopedometer.ui

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cs22study.easygopedometer.calc.CalculatedMetrics
import com.cs22study.easygopedometer.calc.DistanceUnit
import com.cs22study.easygopedometer.service.CapturedScreenshot
import kotlinx.coroutines.delay

private val DarkBg = Color(0xFF0D1117)
private val TopBarBg = Color(0xFF161B22)
private val CardBg = Color(0xFF1C2128)
private val CardBorder = Color(0xFF30363D)

@Composable
fun HomeScreen(
    state: HomeUiState,
    needsStepPermission: Boolean,
    onRequestPermissions: () -> Unit,
    onSelectTab: (HomeTab) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenGpsSpeedometer: () -> Unit,
    onScreenshotClick: () -> Unit,
    onViewScreenshot: (Uri) -> Unit,
    onShareScreenshot: (Uri) -> Unit,
    onDismissScreenshotPreview: () -> Unit,
    onExportCsv: () -> Unit,
    onSyncHealthConnect: () -> Unit,
    onOpenSettings: () -> Unit,
    onClearBanner: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.statusBanner) {
        val msg = state.statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            onClearBanner()
        }
    }

    LaunchedEffect(state.capturedScreenshot?.capturedAtMillis) {
        if (state.capturedScreenshot != null) {
            delay(8000L)
            onDismissScreenshotPreview()
        }
    }

    val currentHeader: String
    val currentMetrics: CalculatedMetrics
    val sparklineValues: List<Long>
    val stepsCardTitle: String
    when (state.selectedTab) {
        HomeTab.DAY -> {
            currentHeader = state.daySummary?.headerText ?: ""
            currentMetrics = state.daySummary?.metrics ?: emptyMetrics()
            stepsCardTitle = "今日步數"
            val curve = state.daySummary?.hourlyCurve ?: emptyList()
            sparklineValues = if (curve.size <= 1) {
                listOf(currentMetrics.steps)
            } else {
                // Convert cumulative curve into per-2-hour buckets for mini trend bars
                val hourly = curve.mapIndexed { idx, pt ->
                    if (idx == 0) pt.cumulativeSteps else (pt.cumulativeSteps - curve[idx - 1].cumulativeSteps).coerceAtLeast(0L)
                }
                hourly.chunked(2).map { it.sum() }
            }
        }
        HomeTab.WEEK -> {
            currentHeader = state.weekSummary?.headerText ?: ""
            currentMetrics = state.weekSummary?.metrics ?: emptyMetrics()
            stepsCardTitle = "本週步數"
            sparklineValues = state.weekSummary?.bars?.map { it.steps } ?: emptyList()
        }
        HomeTab.MONTH -> {
            currentHeader = state.monthSummary?.headerText ?: ""
            currentMetrics = state.monthSummary?.metrics ?: emptyMetrics()
            stepsCardTitle = "本月步數"
            sparklineValues = state.monthSummary?.bars?.map { it.steps }?.chunked(3)?.map { it.sum() } ?: emptyList()
        }
        HomeTab.YEAR -> {
            currentHeader = state.yearSummary?.headerText ?: ""
            currentMetrics = state.yearSummary?.metrics ?: emptyMetrics()
            stepsCardTitle = "今年步數"
            sparklineValues = state.yearSummary?.bars?.map { it.steps } ?: emptyList()
        }
    }

    val (distValue, distUnitText) = when (state.distanceUnit) {
        DistanceUnit.METERS -> "%,d".format(currentMetrics.distanceMeters) to "公尺"
        DistanceUnit.KILOMETERS -> "%.2f".format(currentMetrics.distanceMeters / 1000.0) to "公里"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top dark navigation bar with 4 tabs + screenshot button + menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TopBarBg)
                    .statusBarsPadding()
                    .height(54.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HomeTab.entries.forEach { tab ->
                        val selected = tab == state.selectedTab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 3.dp, vertical = 8.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (selected) BrightYellow else CardBg)
                                .clickable { onSelectTab(tab) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = tab.label,
                                color = if (selected) Color.Black else BrightYellow.copy(alpha = 0.85f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                // One-click full-screen screenshot button
                IconButton(onClick = onScreenshotClick) {
                    CameraIcon(tint = BrightYellow)
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "選單",
                            tint = BrightYellow,
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("匯出本機 CSV 紀錄") },
                            onClick = {
                                menuExpanded = false
                                onExportCsv()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("同步 Google Fit / Health Connect") },
                            onClick = {
                                menuExpanded = false
                                onSyncHealthConnect()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("設定") },
                            onClick = {
                                menuExpanded = false
                                onOpenSettings()
                            },
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (needsStepPermission) {
                    Surface(
                        color = Color(0xFF2D2500),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, BrightYellow.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRequestPermissions() },
                    ) {
                        Text(
                            text = "點此授權計步感測與通知權限，確保背景持續累計",
                            color = BrightYellow,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
                        )
                    }
                }

                // Explicit Date & Weekday Header (Bold + Bright Yellow)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "◀",
                        color = BrightYellow,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onPrevious() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    Text(
                        text = currentHeader,
                        color = BrightYellow,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "▶",
                        color = if (state.canGoNext) BrightYellow else Color.Transparent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .then(if (state.canGoNext) Modifier.clickable { onNext() } else Modifier)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                // Card 1: 今日步數 (Bold Bright Yellow)
                MetricTrendCard(
                    title = stepsCardTitle,
                    value = "%,d".format(currentMetrics.steps),
                    unit = "步",
                    icon = { FootprintIcon(tint = BrightYellow) },
                    sparklineValues = sparklineValues,
                    accentColor = BrightYellow,
                    isHero = true,
                )

                // Card 2: 累計時間 (時:分:秒, Bold Bright Yellow)
                MetricTrendCard(
                    title = "累計時間",
                    value = currentMetrics.formattedDuration,
                    unit = "時:分:秒",
                    icon = { StopwatchIcon(tint = BrightYellow) },
                    sparklineValues = sparklineValues,
                    accentColor = BrightYellow.copy(alpha = 0.85f),
                )

                // Card 3: 換算距離 (公尺, Bold Bright Yellow)
                MetricTrendCard(
                    title = "換算距離",
                    value = distValue,
                    unit = distUnitText,
                    icon = { RulerIcon(tint = BrightYellow) },
                    sparklineValues = sparklineValues,
                    accentColor = BrightYellow.copy(alpha = 0.85f),
                )

                // Card 4: 燃燒熱量 (千卡, Bold Bright Yellow)
                MetricTrendCard(
                    title = "燃燒熱量",
                    value = "%.1f".format(currentMetrics.caloriesKcal),
                    unit = "千卡",
                    icon = { FlameIcon(tint = BrightYellow) },
                    sparklineValues = sparklineValues,
                    accentColor = BrightYellow.copy(alpha = 0.85f),
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Cycling GPS Speedometer Entry Button in remaining space
                Button(
                    onClick = onOpenGpsSpeedometer,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CardBg,
                        contentColor = BrightYellow,
                    ),
                    border = BorderStroke(1.5.dp, BrightYellow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp),
                ) {
                    BikeIcon(tint = BrightYellow)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "GPS 即時時速表（單車 / 車輛 / 高鐵）",
                        color = BrightYellow,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // Bottom Screenshot Preview Card
        val shot = state.capturedScreenshot
        if (shot != null) {
            ScreenshotPreviewCard(
                screenshot = shot,
                onView = { onViewScreenshot(shot.uri) },
                onShare = { onShareScreenshot(shot.uri) },
                onDismiss = onDismissScreenshotPreview,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(12.dp),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(12.dp),
        )
    }
}

@Composable
private fun MetricTrendCard(
    title: String,
    value: String,
    unit: String,
    icon: @Composable () -> Unit,
    sparklineValues: List<Long>,
    accentColor: Color,
    isHero: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (isHero) 16.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    icon()
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        color = BrightYellow,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = value,
                        color = BrightYellow,
                        fontSize = if (isHero) 36.sp else 28.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = unit,
                        color = BrightYellow.copy(alpha = 0.88f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }

            if (sparklineValues.isNotEmpty()) {
                MiniSparklineBars(
                    values = sparklineValues,
                    accentColor = accentColor,
                )
            }
        }
    }
}

@Composable
fun ScreenshotPreviewCard(
    screenshot: CapturedScreenshot,
    onView: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937).copy(alpha = 0.96f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                bitmap = screenshot.thumbnail.asImageBitmap(),
                contentDescription = "截圖預覽",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 48.dp, height = 76.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .clickable { onView() },
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "已儲存至相簿",
                    color = BrightYellow,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = screenshot.fileName,
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row {
                    TextButton(onClick = onView) {
                        Text("檢視", color = BrightYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onShare) {
                        Text("分享", color = BrightYellow, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "關閉", tint = Color.White.copy(alpha = 0.8f))
            }
        }
    }
}

private fun emptyMetrics() = CalculatedMetrics(
    steps = 0L,
    activeSeconds = 0L,
    distanceMeters = 0L,
    avgSpeedKmh = 0.0,
    caloriesKcal = 0.0,
    formattedDuration = "00:00:00",
)
