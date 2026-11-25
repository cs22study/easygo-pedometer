package com.cs22study.easygopedometer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cs22study.easygopedometer.health.HealthConnectBridge
import com.cs22study.easygopedometer.health.HealthSyncAvailability
import com.cs22study.easygopedometer.service.StepCounterService
import com.cs22study.easygopedometer.ui.HomeScreen
import com.cs22study.easygopedometer.ui.PedometerViewModel
import com.cs22study.easygopedometer.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val vm: PedometerViewModel = viewModel()
            val uiState by vm.uiState.collectAsState()
            val settings by vm.prefsRepo.settings.collectAsState()
            val context = LocalContext.current

            var showSettings by remember { mutableStateOf(false) }
            var needsPermission by remember { mutableStateOf(!hasRequiredStepPermissions()) }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) {
                needsPermission = !hasRequiredStepPermissions()
                if (!needsPermission) {
                    StepCounterService.start(context)
                }
            }

            val healthPermissionLauncher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                rememberLauncherForActivityResult(
                    contract = PermissionController.createRequestPermissionResultContract()
                ) { granted ->
                    if (granted.containsAll(HealthConnectBridge.requiredPermissions)) {
                        vm.syncHealthConnectNow()
                    } else {
                        vm.showBanner("未完整授予 Health Connect 權限")
                    }
                }
            } else {
                null
            }

            LaunchedEffect(Unit) {
                if (hasRequiredStepPermissions()) {
                    StepCounterService.start(context)
                } else {
                    permissionLauncher.launch(requiredRuntimePermissions())
                }
            }

            MaterialTheme {
                if (showSettings) {
                    SettingsScreen(
                        settings = settings,
                        healthAvailability = uiState.healthAvailability,
                        deletedDates = uiState.deletedDates,
                        onBack = { showSettings = false },
                        onRequestHealthPermissions = {
                            if (uiState.healthAvailability == HealthSyncAvailability.AVAILABLE && healthPermissionLauncher != null) {
                                healthPermissionLauncher.launch(HealthConnectBridge.requiredPermissions)
                            } else {
                                vm.showBanner(HealthConnectBridge.availabilityDescription(uiState.healthAvailability))
                            }
                        },
                        onSyncHealthNow = { vm.syncHealthConnectNow() },
                        onOpenGoogleFit = { HealthConnectBridge.openGoogleFitOrStore(context) },
                        onExportCsv = { vm.exportCsvNow() },
                        onRestoreDate = { vm.restoreDeletedDay(it) },
                        onUpdateAge = vm::updateAge,
                        onUpdateGender = vm::updateGender,
                        onUpdateWeight = vm::updateWeight,
                        onUpdateHeight = vm::updateHeight,
                        onUpdateStride = vm::updateCustomStride,
                        onUpdateDistanceUnit = vm::updateDistanceUnit,
                        onUpdateWeekStart = vm::updateWeekStart,
                        onUpdatePowerMode = vm::updatePowerMode,
                    )
                } else {
                    HomeScreen(
                        state = uiState,
                        needsStepPermission = needsPermission,
                        onRequestPermissions = { permissionLauncher.launch(requiredRuntimePermissions()) },
                        onSelectTab = vm::selectTab,
                        onPrevious = vm::goPrevious,
                        onNext = vm::goNext,
                        onScreenshotClick = {
                            vm.showBanner("請同時按手機電源鍵＋音量下鍵截取含上方狀態列之完整畫面（一鍵自動截圖將於下階段串接）")
                        },
                        onExportCsv = vm::exportCsvNow,
                        onSyncHealthConnect = vm::syncHealthConnectNow,
                        onOpenSettings = { showSettings = true },
                        onClearBanner = vm::clearBanner,
                    )
                }
            }
        }
    }

    private fun requiredRuntimePermissions(): Array<String> {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            list.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list.toTypedArray()
    }

    private fun hasRequiredStepPermissions(): Boolean {
        return requiredRuntimePermissions().all { perm ->
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }
    }
}
