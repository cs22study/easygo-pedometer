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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cs22study.easygopedometer.calc.CalculatedMetrics
import com.cs22study.easygopedometer.calc.DistanceUnit
import com.cs22study.easygopedometer.service.CapturedScreenshot
import kotlinx.coroutines.delay

private val DarkBg = Color(0xFF0B0E14)
private val TopBarBg = Color(0xFF141922)
private val HeroCardTop = Color(0xFF232936)
private val HeroCardBottom = Color(0xFF181D27)
private val CardBg = Color(0xFF171C24)
private val CardBorder = Color(0xFF2B323F)
private val UnitBadgeBg = Color(0xFF2A2612)
private val ProgressTrackBg = Color(0xFF2A303C)
private val DailyGoalSteps = 8000L

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
            // Top dark navigation bar with 4 pill tabs + screenshot button + menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TopBarBg)
                    .statusBarsPadding()
                    .height(52.dp),
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
                                .padding(horizontal = 3.dp, vertical = 7.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (selected) BrightYellow else CardBg)
                                .clickable { onSelectTab(tab) }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = tab.label,
                                color = if (selected) Color.Black else BrightYellow.copy(alpha = 0.88f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

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
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (needsStepPermission) {
                    Surface(
                        color = UnitBadgeBg,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, BrightYellow.copy(alpha = 0.65f)),
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
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onPrevious() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                    Text(
                        text = currentHeader,
                        color = BrightYellow,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "▶",
                        color = if (state.canGoNext) BrightYellow else Color.Transparent,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .then(if (state.canGoNext) Modifier.clickable { onNext() } else Modifier)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }

                // Card 1: Hero Steps Card (with 8,000-step progress bar on Day tab)
                HeroStepsCard(
                    title = stepsCardTitle,
                    steps = currentMetrics.steps,
                    showDailyGoal = state.selectedTab == HomeTab.DAY,
                    sparklineValues = sparklineValues,
                )

                // Card 2: 累計時間 (時:分:秒 in crisp title badge, zero truncation!)
                CompactMetricCard(
                    title = "累計時間",
                    unitBadge = "時:分:秒",
                    value = currentMetrics.formattedDuration,
                    icon = { StopwatchIcon(tint = BrightYellow) },
                    sparklineValues = sparklineValues,
                )

                // Card 3: 換算距離 (公尺 / 公里)
                CompactMetricCard(
                    title = "換算距離",
                    unitBadge = distUnitText,
                    value = distValue,
                    icon = { RulerIcon(tint = BrightYellow) },
                    sparklineValues = sparklineValues,
                )

                // Card 4: 燃燒熱量 (千卡)
                CompactMetricCard(
                    title = "燃燒熱量",
                    unitBadge = "千卡",
                    value = "%.1f".format(currentMetrics.caloriesKcal),
                    icon = { FlameIcon(tint = BrightYellow) },
                    sparklineValues = sparklineValues,
                )

                Spacer(modifier = Modifier.height(2.dp))

                // GPS Speedometer Entry Card in remaining space
                Surface(
                    onClick = onOpenGpsSpeedometer,
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.5.dp, BrightYellow.copy(alpha = 0.9f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .background(Brush.horizontalGradient(listOf(HeroCardTop, CardBg)))
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(UnitBadgeBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            BikeIcon(tint = BrightYellow)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "GPS 即時時速表",
                                color = BrightYellow,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "單車 60 · 車 160 · 高鐵 400",
                                color = BrightYellow.copy(alpha = 0.78f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "進入",
                            tint = BrightYellow,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            }
        }

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
private fun HeroStepsCard(
    title: String,
    steps: Long,
    showDailyGoal: Boolean,
    sparklineValues: List<Long>,
) {
    val progress = (steps.toFloat() / DailyGoalSteps.toFloat()).coerceIn(0f, 1f)
    val goalPct = ((steps * 100L) / DailyGoalSteps).coerceAtMost(999L)
    val badgeText = if (steps >= DailyGoalSteps) "已達 8,000 步 ✓" else "目標 8,000 · $goalPct%"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        border = BorderStroke(1.2.dp, BrightYellow.copy(alpha = 0.55f)),
    ) {
        Column(
            modifier = Modifier
                .background(Brush.verticalGradient(listOf(HeroCardTop, HeroCardBottom)))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FootprintIcon(tint = BrightYellow)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        color = BrightYellow,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    UnitPill("步")
                }

                if (showDailyGoal) {
                    Surface(
                        color = UnitBadgeBg,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BrightYellow.copy(alpha = 0.45f)),
                    ) {
                        Text(
                            text = badgeText,
                            color = BrightYellow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "%,d".format(steps),
                    color = BrightYellow,
                    style = TextStyle(
                        fontSize = if (steps >= 100_000L) 34.sp else 42.sp,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                    maxLines = 1,
                    softWrap = false,
                )

                if (sparklineValues.isNotEmpty()) {
                    MiniSparklineBars(
                        values = sparklineValues,
                        accentColor = BrightYellow,
                    )
                }
            }

            if (showDailyGoal) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(ProgressTrackBg),
                ) {
                    if (progress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(BrightYellow),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactMetricCard(
    title: String,
    unitBadge: String,
    value: String,
    icon: @Composable () -> Unit,
    sparklineValues: List<Long>,
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    UnitPill(unitBadge)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value,
                    color = BrightYellow,
                    style = TextStyle(
                        fontSize = 29.sp,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                    maxLines = 1,
                    softWrap = false,
                )
            }

            if (sparklineValues.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                MiniSparklineBars(
                    values = sparklineValues,
                    accentColor = BrightYellow,
                )
            }
        }
    }
}

@Composable
private fun UnitPill(text: String) {
    Surface(
        color = UnitBadgeBg,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            text = text,
            color = BrightYellow.copy(alpha = 0.92f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
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
