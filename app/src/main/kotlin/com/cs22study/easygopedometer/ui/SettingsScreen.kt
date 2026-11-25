package com.cs22study.easygopedometer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cs22study.easygopedometer.calc.DistanceUnit
import com.cs22study.easygopedometer.calc.Gender
import com.cs22study.easygopedometer.data.AppSettings
import com.cs22study.easygopedometer.data.PowerMode
import com.cs22study.easygopedometer.health.HealthConnectBridge
import com.cs22study.easygopedometer.health.HealthSyncAvailability
import java.time.DayOfWeek

private val SettingsBg = Color(0xFFF3F4F6)
private val SectionHeaderColor = Color(0xFF6B7280)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    healthAvailability: HealthSyncAvailability,
    healthTodaySteps: Long?,
    deletedDates: List<String>,
    selectedDateIso: String,
    onBack: () -> Unit,
    onRequestHealthPermissions: () -> Unit,
    onSyncHealthNow: () -> Unit,
    onImportHealthToday: () -> Unit,
    onOpenGoogleFit: () -> Unit,
    onExportCsv: () -> Unit,
    onSoftDeleteSelectedDay: () -> Unit,
    onRestoreDate: (String) -> Unit,
    onUpdateAge: (Int) -> Unit,
    onUpdateGender: (Gender) -> Unit,
    onUpdateWeight: (Double) -> Unit,
    onUpdateHeight: (Double) -> Unit,
    onUpdateStride: (Double?) -> Unit,
    onUpdateDistanceUnit: (DistanceUnit) -> Unit,
    onUpdateWeekStart: (DayOfWeek) -> Unit,
    onUpdatePowerMode: (PowerMode) -> Unit,
) {
    var editDialog by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SettingsBg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "設定",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        HorizontalDivider(color = Color(0xFFE5E7EB))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("Google Fit / Health Connect 串聯")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(16.dp),
            ) {
                Text(
                    text = HealthConnectBridge.availabilityDescription(healthAvailability),
                    fontSize = 14.sp,
                    color = Color(0xFF1F2937),
                )
                if (healthTodaySteps != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Health Connect 今日總計：%,d 步".format(healthTodaySteps),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2563EB),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "同步狀態：${settings.lastHealthSyncMessage}",
                    fontSize = 13.sp,
                    color = SectionHeaderColor,
                )
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (healthAvailability == HealthSyncAvailability.AVAILABLE) {
                        Button(onClick = onRequestHealthPermissions) {
                            Text("授權同步")
                        }
                        OutlinedButton(onClick = onSyncHealthNow) {
                            Text("寫入同步")
                        }
                        OutlinedButton(onClick = onImportHealthToday) {
                            Text("讀取今日步數")
                        }
                    }
                    OutlinedButton(onClick = onOpenGoogleFit) {
                        Text("開啟 Google Fit")
                    }
                }
            }

            SectionTitle("省電")
            SettingsRow(
                title = "計步模式",
                value = if (settings.powerMode == PowerMode.STANDARD_HARDWARE) "標準硬體感測（省電）" else "高靈敏度相容",
                onClick = {
                    val next = if (settings.powerMode == PowerMode.STANDARD_HARDWARE) {
                        PowerMode.HIGH_SENSITIVITY
                    } else {
                        PowerMode.STANDARD_HARDWARE
                    }
                    onUpdatePowerMode(next)
                },
            )

            SectionTitle("畫面")
            SettingsRow(
                title = "顯示距離單位",
                value = if (settings.profile.distanceUnit == DistanceUnit.METERS) "公尺" else "公里",
                onClick = {
                    val next = if (settings.profile.distanceUnit == DistanceUnit.METERS) {
                        DistanceUnit.KILOMETERS
                    } else {
                        DistanceUnit.METERS
                    }
                    onUpdateDistanceUnit(next)
                },
            )
            HorizontalDivider(color = Color(0xFFF0F0F0))
            SettingsRow(
                title = "星期開始",
                value = if (settings.weekStart == DayOfWeek.SUNDAY) "週日" else "週一",
                onClick = {
                    onUpdateWeekStart(if (settings.weekStart == DayOfWeek.SUNDAY) DayOfWeek.MONDAY else DayOfWeek.SUNDAY)
                },
            )

            SectionTitle("個人")
            SettingsRow("年齡", "${settings.profile.age} 歲") { editDialog = "age" }
            HorizontalDivider(color = Color(0xFFF0F0F0))
            SettingsRow(
                title = "性別",
                value = when (settings.profile.gender) {
                    Gender.MALE -> "男"
                    Gender.FEMALE -> "女"
                    Gender.UNSPECIFIED -> "未指定"
                },
                onClick = {
                    val next = when (settings.profile.gender) {
                        Gender.UNSPECIFIED -> Gender.MALE
                        Gender.MALE -> Gender.FEMALE
                        Gender.FEMALE -> Gender.UNSPECIFIED
                    }
                    onUpdateGender(next)
                },
            )
            HorizontalDivider(color = Color(0xFFF0F0F0))
            SettingsRow("體重", "%.1f 公斤".format(settings.profile.weightKg)) { editDialog = "weight" }
            HorizontalDivider(color = Color(0xFFF0F0F0))
            SettingsRow("身高", "%.0f 公分".format(settings.profile.heightCm)) { editDialog = "height" }
            HorizontalDivider(color = Color(0xFFF0F0F0))
            val strideText = if (settings.profile.customStrideMeters == null) {
                "自動 (${(settings.profile.effectiveStrideMeters() * 100).toInt()} 公分)"
            } else {
                "${(settings.profile.effectiveStrideMeters() * 100).toInt()} 公分"
            }
            SettingsRow("步幅", strideText) { editDialog = "stride" }

            SectionTitle("本機 CSV 備份與紀錄管理")
            SettingsRow("匯出本機 CSV 紀錄檔", "立即儲存", onClick = onExportCsv)
            HorizontalDivider(color = Color(0xFFF0F0F0))
            SettingsRow("清除 $selectedDateIso 紀錄（軟刪除）", "移至復原清單", onClick = onSoftDeleteSelectedDay)
            if (deletedDates.isNotEmpty()) {
                HorizontalDivider(color = Color(0xFFF0F0F0))
                deletedDates.forEach { d ->
                    SettingsRow("復原已刪除紀錄 ($d)", "點此復原") { onRestoreDate(d) }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    when (editDialog) {
        "age" -> NumberEditDialog(
            title = "設定年齡",
            initial = settings.profile.age.toString(),
            onDismiss = { editDialog = null },
            onConfirm = { v -> v.toIntOrNull()?.let(onUpdateAge); editDialog = null },
        )
        "weight" -> NumberEditDialog(
            title = "設定體重（公斤）",
            initial = settings.profile.weightKg.toString(),
            onDismiss = { editDialog = null },
            onConfirm = { v -> v.toDoubleOrNull()?.let(onUpdateWeight); editDialog = null },
        )
        "height" -> NumberEditDialog(
            title = "設定身高（公分）",
            initial = settings.profile.heightCm.toInt().toString(),
            onDismiss = { editDialog = null },
            onConfirm = { v -> v.toDoubleOrNull()?.let(onUpdateHeight); editDialog = null },
        )
        "stride" -> NumberEditDialog(
            title = "設定步幅（公分，留空則依身高自動估算）",
            initial = settings.profile.customStrideMeters?.let { (it * 100).toInt().toString() } ?: "",
            onDismiss = { editDialog = null },
            onConfirm = { v ->
                if (v.isBlank()) {
                    onUpdateStride(null)
                } else {
                    v.toDoubleOrNull()?.let { cm -> onUpdateStride(cm / 100.0) }
                }
                editDialog = null
            },
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = SectionHeaderColor,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingsRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = title, fontSize = 16.sp, color = Color(0xFF111827))
        Text(text = value, fontSize = 14.sp, color = SectionHeaderColor)
    }
}

@Composable
private fun NumberEditDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 16.sp) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }) { Text("確定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
