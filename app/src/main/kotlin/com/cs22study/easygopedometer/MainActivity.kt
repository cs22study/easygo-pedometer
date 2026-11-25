package com.cs22study.easygopedometer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import com.cs22study.easygopedometer.service.ScreenCaptureService
import com.cs22study.easygopedometer.service.StepCounterService
import com.cs22study.easygopedometer.ui.BrightYellow
import com.cs22study.easygopedometer.ui.GpsSpeedometerScreen
import com.cs22study.easygopedometer.ui.HomeScreen
import com.cs22study.easygopedometer.ui.PedometerViewModel
import com.cs22study.easygopedometer.ui.SettingsScreen

private enum class AppRoute {
    HOME,
    SETTINGS,
    GPS_SPEEDOMETER,
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val vm: PedometerViewModel = viewModel()
            val uiState by vm.uiState.collectAsState()
            val settings by vm.prefsRepo.settings.collectAsState()
            val context = LocalContext.current

            var currentRoute by remember { mutableStateOf(AppRoute.HOME) }
            var needsStepPermission by remember { mutableStateOf(!hasRequiredStepPermissions()) }
            var hasLocationPermission by remember { mutableStateOf(checkLocationPermission()) }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) {
                needsStepPermission = !hasRequiredStepPermissions()
                if (!needsStepPermission) {
                    StepCounterService.start(context)
                }
            }

            val locationLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions()
            ) {
                hasLocationPermission = checkLocationPermission()
                if (hasLocationPermission) {
                    vm.startBikeRide()
                }
            }

            val screenCaptureLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                val data = result.data
                if (result.resultCode == Activity.RESULT_OK && data != null) {
                    ScreenCaptureService.startCapture(context, result.resultCode, data)
                } else {
                    vm.showBanner("已取消螢幕截圖")
                }
            }

            val launchSystemScreenCapture: () -> Unit = {
                val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                screenCaptureLauncher.launch(mgr.createScreenCaptureIntent())
            }

            val storageForCaptureLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) {
                launchSystemScreenCapture()
            }

            val triggerScreenshot: () -> Unit = {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                ) {
                    storageForCaptureLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                } else {
                    launchSystemScreenCapture()
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

            BackHandler(enabled = currentRoute != AppRoute.HOME) {
                currentRoute = AppRoute.HOME
            }

            MaterialTheme(
                colorScheme = darkColorScheme(primary = BrightYellow)
            ) {
                when (currentRoute) {
                    AppRoute.SETTINGS -> {
                        MaterialTheme(colorScheme = lightColorScheme()) {
                            SettingsScreen(
                                settings = settings,
                                healthAvailability = uiState.healthAvailability,
                                healthTodaySteps = uiState.healthTodaySteps,
                                deletedDates = uiState.deletedDates,
                                selectedDateIso = uiState.selectedDate.toString(),
                                onBack = { currentRoute = AppRoute.HOME },
                                onRequestHealthPermissions = {
                                    if (uiState.healthAvailability == HealthSyncAvailability.AVAILABLE && healthPermissionLauncher != null) {
                                        healthPermissionLauncher.launch(HealthConnectBridge.requiredPermissions)
                                    } else {
                                        vm.showBanner(HealthConnectBridge.availabilityDescription(uiState.healthAvailability))
                                    }
                                },
                                onSyncHealthNow = { vm.syncHealthConnectNow() },
                                onImportHealthToday = { vm.importTodayFromHealthConnect() },
                                onOpenGoogleFit = { HealthConnectBridge.openGoogleFitOrStore(context) },
                                onExportCsv = { vm.exportCsvNow() },
                                onSoftDeleteSelectedDay = { vm.softDeleteSelectedDay() },
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
                        }
                    }

                    AppRoute.GPS_SPEEDOMETER -> {
                        GpsSpeedometerScreen(
                            rideState = uiState.gpsRideState,
                            hasLocationPermission = hasLocationPermission,
                            isGpsEnabled = vm.isGpsEnabled(),
                            capturedScreenshot = uiState.capturedScreenshot,
                            onBack = { currentRoute = AppRoute.HOME },
                            onRequestLocationPermission = {
                                locationLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    )
                                )
                            },
                            onStartRide = vm::startBikeRide,
                            onPauseRide = vm::pauseBikeRide,
                            onResetRide = vm::resetBikeRide,
                            onScreenshotClick = triggerScreenshot,
                            onViewScreenshot = { uri -> openScreenshot(context, uri) },
                            onShareScreenshot = { uri -> shareScreenshot(context, uri) },
                            onDismissScreenshotPreview = vm::dismissScreenshotPreview,
                        )
                    }

                    AppRoute.HOME -> {
                        HomeScreen(
                            state = uiState,
                            needsStepPermission = needsStepPermission,
                            onRequestPermissions = { permissionLauncher.launch(requiredRuntimePermissions()) },
                            onSelectTab = vm::selectTab,
                            onPrevious = vm::goPrevious,
                            onNext = vm::goNext,
                            onOpenGpsSpeedometer = { currentRoute = AppRoute.GPS_SPEEDOMETER },
                            onScreenshotClick = triggerScreenshot,
                            onViewScreenshot = { uri -> openScreenshot(context, uri) },
                            onShareScreenshot = { uri -> shareScreenshot(context, uri) },
                            onDismissScreenshotPreview = vm::dismissScreenshotPreview,
                            onExportCsv = vm::exportCsvNow,
                            onSyncHealthConnect = vm::syncHealthConnectNow,
                            onOpenSettings = { currentRoute = AppRoute.SETTINGS },
                            onClearBanner = vm::clearBanner,
                        )
                    }
                }
            }
        }
    }

    private fun openScreenshot(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "image/png")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun shareScreenshot(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "分享截圖").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
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
