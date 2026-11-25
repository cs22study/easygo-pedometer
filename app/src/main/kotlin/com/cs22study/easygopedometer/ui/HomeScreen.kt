package com.cs22study.easygopedometer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cs22study.easygopedometer.calc.CalculatedMetrics
import com.cs22study.easygopedometer.calc.DistanceUnit

private val TopNavNavy = Color(0xFF283D5B)
private val TabIndicatorBlue = Color(0xFF5D9CE6)
private val SkyBlueTop = Color(0xFF4D8AD6)
private val SkyBlueBottom = Color(0xFF87B9EE)

@Composable
fun HomeScreen(
    state: HomeUiState,
    needsStepPermission: Boolean,
    onRequestPermissions: () -> Unit,
    onSelectTab: (HomeTab) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onScreenshotClick: () -> Unit,
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

    val currentHeader: String
    val currentMetrics: CalculatedMetrics
    when (state.selectedTab) {
        HomeTab.DAY -> {
            currentHeader = state.daySummary?.headerText ?: ""
            currentMetrics = state.daySummary?.metrics ?: emptyMetrics()
        }
        HomeTab.WEEK -> {
            currentHeader = state.weekSummary?.headerText ?: ""
            currentMetrics = state.weekSummary?.metrics ?: emptyMetrics()
        }
        HomeTab.MONTH -> {
            currentHeader = state.monthSummary?.headerText ?: ""
            currentMetrics = state.monthSummary?.metrics ?: emptyMetrics()
        }
        HomeTab.YEAR -> {
            currentHeader = state.yearSummary?.headerText ?: ""
            currentMetrics = state.yearSummary?.metrics ?: emptyMetrics()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SkyBlueTop, SkyBlueBottom))),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top dark navy tab bar matching reference screenshot
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TopNavNavy)
                    .statusBarsPadding()
                    .height(52.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    HomeTab.entries.forEach { tab ->
                        val selected = tab == state.selectedTab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onSelectTab(tab) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = tab.label,
                                color = if (selected) Color.White else Color.White.copy(alpha = 0.65f),
                                fontSize = 17.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .background(TabIndicatorBlue),
                                )
                            }
                        }
                    }
                }

                // Home screen screenshot button
                IconButton(onClick = onScreenshotClick) {
                    CameraIcon(tint = Color.White)
                }

                // Overflow menu
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "選單",
                            tint = Color.White,
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
                            text = { Text("同步至 Google Fit / Health Connect") },
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
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (needsStepPermission) {
                    Surface(
                        color = Color.White.copy(alpha = 0.22f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable { onRequestPermissions() },
                    ) {
                        Text(
                            text = "點此授權計步感測與通知權限，確保背景持續累計",
                            color = Color.White,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
                        )
                    }
                }

                // Date Header Row: ◀   2026年11月25日 週三   ▶
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "◀",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 18.sp,
                        modifier = Modifier
                            .clickable { onPrevious() }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                    Text(
                        text = currentHeader,
                        color = Color.White,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "▶",
                        color = if (state.canGoNext) Color.White.copy(alpha = 0.9f) else Color.Transparent,
                        fontSize = 18.sp,
                        modifier = Modifier
                            .then(if (state.canGoNext) Modifier.clickable { onNext() } else Modifier)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }

                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.45f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
                )

                // Hero Steps Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "步數",
                            color = Color.White.copy(alpha = 0.95f),
                            fontSize = 14.sp,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FootprintIcon()
                    }
                    Text(
                        text = "%,d".format(currentMetrics.steps),
                        color = Color.White,
                        fontSize = 54.sp,
                        fontWeight = FontWeight.Light,
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2x2 Metrics Grid matching reference screenshot
                MetricsGrid(metrics = currentMetrics, distanceUnit = state.distanceUnit)

                Spacer(modifier = Modifier.height(18.dp))

                // Chart section
                when (state.selectedTab) {
                    HomeTab.DAY -> {
                        DayCumulativeLineChart(
                            points = state.daySummary?.hourlyCurve ?: emptyList(),
                        )
                    }
                    HomeTab.WEEK -> {
                        PeriodBarChart(
                            bars = state.weekSummary?.bars ?: emptyList(),
                            defaultYMax = 12000L,
                        )
                    }
                    HomeTab.MONTH -> {
                        PeriodBarChart(
                            bars = state.monthSummary?.bars ?: emptyList(),
                            defaultYMax = 24000L,
                        )
                    }
                    HomeTab.YEAR -> {
                        PeriodBarChart(
                            bars = state.yearSummary?.bars ?: emptyList(),
                            defaultYMax = 240000L,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
        )
    }
}

@Composable
private fun MetricsGrid(
    metrics: CalculatedMetrics,
    distanceUnit: DistanceUnit,
) {
    val (distValue, distLabel) = when (distanceUnit) {
        DistanceUnit.METERS -> "%,d".format(metrics.distanceMeters) to "公尺"
        DistanceUnit.KILOMETERS -> "%.2f".format(metrics.distanceMeters / 1000.0) to "公里"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.78f))
            .background(Color.White.copy(alpha = 0.08f)),
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            MetricCell(
                icon = { FlameIcon() },
                value = "%.1f".format(metrics.caloriesKcal),
                unit = "千卡",
                modifier = Modifier.weight(1f),
            )
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.7f)))
            MetricCell(
                icon = { RulerIcon() },
                value = distValue,
                unit = distLabel,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.7f), thickness = 1.dp)
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            MetricCell(
                icon = { StopwatchIcon() },
                value = metrics.formattedDuration,
                unit = "",
                modifier = Modifier.weight(1f),
            )
            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.7f)))
            MetricCell(
                icon = { SpeedometerIcon() },
                value = "%.1f".format(metrics.avgSpeedKmh),
                unit = "公里/時",
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MetricCell(
    icon: @Composable () -> Unit,
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = value,
            color = Color.White,
            fontSize = 21.sp,
            fontWeight = FontWeight.Normal,
        )
        if (unit.isNotEmpty()) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = unit,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 12.sp,
            )
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
