package com.cs22study.easygopedometer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cs22study.easygopedometer.calc.DaySummaryView
import com.cs22study.easygopedometer.calc.DistanceUnit
import com.cs22study.easygopedometer.calc.Gender
import com.cs22study.easygopedometer.calc.GpsRideState
import com.cs22study.easygopedometer.calc.HourlyCsvRecord
import com.cs22study.easygopedometer.calc.PeriodAggregator
import com.cs22study.easygopedometer.calc.PeriodSummaryView
import com.cs22study.easygopedometer.calc.SpeedMode
import com.cs22study.easygopedometer.data.LocalCsvExporter
import com.cs22study.easygopedometer.data.PowerMode
import com.cs22study.easygopedometer.data.PreferencesRepository
import com.cs22study.easygopedometer.data.db.AppDatabase
import com.cs22study.easygopedometer.data.db.HourlyStepEntity
import com.cs22study.easygopedometer.gps.GpsRideTracker
import com.cs22study.easygopedometer.health.HealthConnectBridge
import com.cs22study.easygopedometer.health.HealthSyncAvailability
import com.cs22study.easygopedometer.service.CapturedScreenshot
import com.cs22study.easygopedometer.service.ScreenCaptureBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.YearMonth

enum class HomeTab(val label: String) {
    DAY("日"),
    WEEK("週"),
    MONTH("月"),
    YEAR("年"),
}

data class HomeUiState(
    val selectedTab: HomeTab = HomeTab.DAY,
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedMonth: YearMonth = YearMonth.now(),
    val selectedYear: Int = LocalDate.now().year,
    val daySummary: DaySummaryView? = null,
    val weekSummary: PeriodSummaryView? = null,
    val monthSummary: PeriodSummaryView? = null,
    val yearSummary: PeriodSummaryView? = null,
    val distanceUnit: DistanceUnit = DistanceUnit.METERS,
    val canGoNext: Boolean = false,
    val healthAvailability: HealthSyncAvailability = HealthSyncAvailability.UNSUPPORTED_OS,
    val healthStatusMessage: String = "",
    val healthTodaySteps: Long? = null,
    val deletedDates: List<String> = emptyList(),
    val statusBanner: String? = null,
    val capturedScreenshot: CapturedScreenshot? = null,
    val gpsRideState: GpsRideState = GpsRideState(),
)

class PedometerViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).pedometerDao()
    val prefsRepo = PreferencesRepository(application)
    private val gpsTracker = GpsRideTracker(application)

    private val selectedTab = MutableStateFlow(HomeTab.DAY)
    private val selectedDate = MutableStateFlow(LocalDate.now())
    private val selectedMonth = MutableStateFlow(YearMonth.now())
    private val selectedYear = MutableStateFlow(LocalDate.now().year)
    private val statusBanner = MutableStateFlow<String?>(null)
    private val healthTodaySteps = MutableStateFlow<Long?>(null)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val cleaned = dao.softDeleteImpossibleSpikes(OffsetDateTime.now().toString())
            if (cleaned > 0) {
                statusBanner.value = "已自動清除 $cleaned 筆異常感測跳躍紀錄（可於設定頁檢視）"
            }
            backfillMissingActiveDurationForToday()
            healthTodaySteps.value = HealthConnectBridge.readTodaySteps(getApplication())
        }
        viewModelScope.launch {
            ScreenCaptureBus.error.collect { err ->
                if (err != null) {
                    statusBanner.value = err
                    ScreenCaptureBus.clearError()
                }
            }
        }
    }

    private suspend fun backfillMissingActiveDurationForToday() {
        val todayIso = LocalDate.now().toString()
        val rows = dao.getRangeHourly(todayIso, todayIso)
        val totalSteps = rows.sumOf { it.steps }
        val totalSec = rows.sumOf { it.activeSeconds }
        if (totalSteps > 100L && totalSec == 0L) {
            val hcDuration = HealthConnectBridge.readTodayExerciseSeconds(getApplication())
            val targetSec = if (hcDuration > 0L) hcDuration else (totalSteps * 10L) / 16L
            val targetRow = rows.maxByOrNull { it.steps } ?: return
            dao.upsertHourly(
                targetRow.copy(
                    activeSeconds = targetSec,
                    updatedAtIso = OffsetDateTime.now().toString(),
                )
            )
        }
    }

    private val navFlow = combine(selectedTab, selectedDate, selectedMonth, selectedYear) { tab, date, month, year ->
        NavState(tab, date, month, year)
    }

    private val metaFlow = combine(
        dao.observeDeletedDates(),
        statusBanner,
        ScreenCaptureBus.latest,
        healthTodaySteps,
        gpsTracker.rideState,
    ) { deletedDates, banner, screenshot, hcSteps, ride ->
        MetaState(deletedDates, banner, screenshot, hcSteps, ride)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        dao.observeAllActive(),
        prefsRepo.settings,
        navFlow,
        metaFlow,
    ) { entities, settings, nav, meta ->
        val records = entities.map { it.toDomain() }
        val today = LocalDate.now()
        val maxHour = if (nav.date == today) LocalTime.now().hour else 23

        val dayView = PeriodAggregator.aggregateDay(
            date = nav.date,
            records = records,
            profile = settings.profile,
            maxHourToPlot = maxHour,
        )
        val weekView = PeriodAggregator.aggregateWeek(
            anchorDate = nav.date,
            records = records,
            profile = settings.profile,
            weekStart = settings.weekStart,
        )
        val monthView = PeriodAggregator.aggregateMonth(
            yearMonth = nav.month,
            records = records,
            profile = settings.profile,
        )
        val yearView = PeriodAggregator.aggregateYear(
            year = nav.year,
            records = records,
            profile = settings.profile,
        )

        val canNext = when (nav.tab) {
            HomeTab.DAY -> nav.date.isBefore(today)
            HomeTab.WEEK -> com.cs22study.easygopedometer.calc.DateDisplayFormatter
                .weekBounds(nav.date, settings.weekStart).second.isBefore(today)
            HomeTab.MONTH -> nav.month.isBefore(YearMonth.now())
            HomeTab.YEAR -> nav.year < today.year
        }

        val hcAvail = HealthConnectBridge.checkAvailability(getApplication())

        HomeUiState(
            selectedTab = nav.tab,
            selectedDate = nav.date,
            selectedMonth = nav.month,
            selectedYear = nav.year,
            daySummary = dayView,
            weekSummary = weekView,
            monthSummary = monthView,
            yearSummary = yearView,
            distanceUnit = settings.profile.distanceUnit,
            canGoNext = canNext,
            healthAvailability = hcAvail,
            healthStatusMessage = settings.lastHealthSyncMessage,
            healthTodaySteps = meta.healthTodaySteps,
            deletedDates = meta.deletedDates,
            statusBanner = meta.banner,
            capturedScreenshot = meta.screenshot,
            gpsRideState = meta.rideState,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = HomeUiState(
            healthAvailability = HealthConnectBridge.checkAvailability(application),
        ),
    )

    fun selectTab(tab: HomeTab) {
        selectedTab.value = tab
    }

    fun goPrevious() {
        when (selectedTab.value) {
            HomeTab.DAY -> selectedDate.value = selectedDate.value.minusDays(1)
            HomeTab.WEEK -> selectedDate.value = selectedDate.value.minusWeeks(1)
            HomeTab.MONTH -> selectedMonth.value = selectedMonth.value.minusMonths(1)
            HomeTab.YEAR -> selectedYear.value = selectedYear.value - 1
        }
    }

    fun goNext() {
        val today = LocalDate.now()
        when (selectedTab.value) {
            HomeTab.DAY -> if (selectedDate.value.isBefore(today)) {
                selectedDate.value = selectedDate.value.plusDays(1)
            }
            HomeTab.WEEK -> {
                val next = selectedDate.value.plusWeeks(1)
                selectedDate.value = if (next.isAfter(today)) today else next
            }
            HomeTab.MONTH -> {
                val nowYm = YearMonth.now()
                if (selectedMonth.value.isBefore(nowYm)) {
                    selectedMonth.value = selectedMonth.value.plusMonths(1)
                }
            }
            HomeTab.YEAR -> if (selectedYear.value < today.year) {
                selectedYear.value = selectedYear.value + 1
            }
        }
    }

    fun exportCsvNow() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val all = dao.getAllIncludingDeleted()
                LocalCsvExporter.exportAll(getApplication(), all, prefsRepo.settings.value.profile)
            }
            statusBanner.value = "CSV 已匯出（${result.dailyRows} 日）：${result.dailyLocation}"
        }
    }

    fun syncHealthConnectNow() {
        viewModelScope.launch {
            val res = withContext(Dispatchers.IO) {
                dao.softDeleteImpossibleSpikes(OffsetDateTime.now().toString())
                val all = dao.getAllIncludingDeleted()
                val syncResult = HealthConnectBridge.syncHourlyRecords(getApplication(), all)
                healthTodaySteps.value = HealthConnectBridge.readTodaySteps(getApplication())
                syncResult
            }
            prefsRepo.update { it.copy(lastHealthSyncMessage = res.message) }
            statusBanner.value = res.message
        }
    }

    fun importTodayFromHealthConnect() {
        viewModelScope.launch {
            val hcSteps = withContext(Dispatchers.IO) {
                HealthConnectBridge.readTodaySteps(getApplication())
            }
            healthTodaySteps.value = hcSteps
            if (hcSteps == null) {
                statusBanner.value = "尚未授權讀取 Health Connect，請先點擊「授權同步」"
                return@launch
            }
            val today = LocalDate.now()
            val todayIso = today.toString()
            withContext(Dispatchers.IO) {
                val existingRows = dao.getRangeHourly(todayIso, todayIso)
                val currentEasyGo = existingRows.sumOf { it.steps }
                val currentSec = existingRows.sumOf { it.activeSeconds }
                val hcSec = HealthConnectBridge.readTodayExerciseSeconds(getApplication())
                if (hcSteps > currentEasyGo && hcSteps <= 150_000L) {
                    val diff = hcSteps - currentEasyGo
                    val estDiffSec = if (hcSec > currentSec) (hcSec - currentSec) else (diff * 10L) / 16L
                    val hour = LocalTime.now().hour
                    val hourRow = dao.getHourly(todayIso, hour)
                    dao.upsertHourly(
                        HourlyStepEntity(
                            dateIso = todayIso,
                            hour = hour,
                            steps = (hourRow?.steps ?: 0L) + diff,
                            activeSeconds = (hourRow?.activeSeconds ?: 0L) + estDiffSec,
                            updatedAtIso = OffsetDateTime.now().toString(),
                            isDeleted = false,
                            deletedAtIso = "",
                        )
                    )
                    statusBanner.value = "已從 Health Connect 補齊今日步數（共 %,d 步）".format(hcSteps)
                } else {
                    backfillMissingActiveDurationForToday()
                    statusBanner.value = "本機今日步數已達 %,d 步（Health Connect 為 %,d 步）".format(currentEasyGo, hcSteps)
                }
            }
        }
    }

    fun softDeleteSelectedDay() {
        val targetDate = selectedDate.value.toString()
        viewModelScope.launch(Dispatchers.IO) {
            dao.softDeleteDay(targetDate, OffsetDateTime.now().toString())
            statusBanner.value = "已軟刪除 $targetDate 紀錄（可於下方清單一鍵復原）"
        }
    }

    fun restoreDeletedDay(dateIso: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.restoreDay(dateIso)
            statusBanner.value = "已復原 $dateIso 計步紀錄"
        }
    }

    fun isGpsEnabled(): Boolean = gpsTracker.isGpsProviderEnabled()

    fun setSpeedMode(mode: SpeedMode) {
        gpsTracker.setSpeedMode(mode)
    }

    fun startBikeRide() {
        gpsTracker.startRide(prefsRepo.settings.value.profile.weightKg)
    }

    fun pauseBikeRide() {
        gpsTracker.pauseRide()
    }

    fun resetBikeRide() {
        gpsTracker.resetRide()
    }

    override fun onCleared() {
        gpsTracker.stopUpdates()
        super.onCleared()
    }

    fun dismissScreenshotPreview() {
        ScreenCaptureBus.clearPreview()
    }

    fun showBanner(msg: String) {
        statusBanner.value = msg
    }

    fun clearBanner() {
        statusBanner.value = null
    }

    fun updateAge(age: Int) = prefsRepo.update {
        it.copy(profile = it.profile.copy(age = age.coerceIn(1, 130)))
    }

    fun updateGender(gender: Gender) = prefsRepo.update {
        it.copy(profile = it.profile.copy(gender = gender))
    }

    fun updateWeight(weightKg: Double) = prefsRepo.update {
        it.copy(profile = it.profile.copy(weightKg = weightKg.coerceIn(20.0, 300.0)))
    }

    fun updateHeight(heightCm: Double) = prefsRepo.update {
        it.copy(profile = it.profile.copy(heightCm = heightCm.coerceIn(80.0, 250.0)))
    }

    fun updateCustomStride(strideMeters: Double?) = prefsRepo.update {
        it.copy(profile = it.profile.copy(customStrideMeters = strideMeters?.coerceIn(0.30, 1.50)))
    }

    fun updateDistanceUnit(unit: DistanceUnit) = prefsRepo.update {
        it.copy(profile = it.profile.copy(distanceUnit = unit))
    }

    fun updateWeekStart(dayOfWeek: DayOfWeek) = prefsRepo.update {
        it.copy(weekStart = dayOfWeek)
    }

    fun updatePowerMode(mode: PowerMode) = prefsRepo.update {
        it.copy(powerMode = mode)
    }

    private data class NavState(
        val tab: HomeTab,
        val date: LocalDate,
        val month: YearMonth,
        val year: Int,
    )

    private data class MetaState(
        val deletedDates: List<String>,
        val banner: String?,
        val screenshot: CapturedScreenshot?,
        val healthTodaySteps: Long?,
        val rideState: GpsRideState,
    )

    private fun HourlyStepEntity.toDomain() = HourlyCsvRecord(
        date = LocalDate.parse(dateIso),
        hour = hour,
        steps = steps,
        activeSeconds = activeSeconds,
        updatedAtIso = updatedAtIso,
        isDeleted = isDeleted,
        deletedAtIso = deletedAtIso,
    )
}
