# Spoke 1：Android 專案骨架、核心計算引擎與建置紀錄

## 1. 本機輕量開發環境配置

- **JDK**：Microsoft Build of OpenJDK 17 (`17.0.21.102-hotspot`)，安裝於 `C:\Program Files\Microsoft\jdk-17.0.21.102-hotspot`
- **Android SDK**：Android Command-Line Tools + `platform-tools` + `platforms;android-35` + `build-tools;35.0.0` (+ Gradle 自動補齊 `build-tools;34.0.0`)，位於 `%LOCALAPPDATA%\Android\Sdk`
- **Gradle Wrapper**：Gradle `8.11.1` + Android Gradle Plugin `8.7.3` + Kotlin `2.0.21` + Compose BOM `2024.12.01` + Room `2.6.1`

### Windows 中文路徑建置與測試說明
由於專案資料夾路徑包含中文，JDK 17 在 Windows 繁體中文語系下啟動 Gradle `TestWorker` 時，原生 `@argfile` 解析會受系統預設編碼影響。本專案提供一鍵腳本 `scripts/gradle-win.ps1`，透過系統 `subst` 暫時映射純 ASCII 虛擬磁碟機執行測試與打包，完成後自動卸載，不影響原始資料夾結構：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\gradle-win.ps1 :core-calc:test :app:testDebugUnitTest :app:assembleDebug
```

---

## 2. 已建立模組與程式清單

### `:core-calc`（純 Kotlin / JVM 核心計算與 CSV 引擎）
- `UserProfile.kt`：身高/性別步幅自動估算、自訂步幅與體重參數驗證
- `StepDeltaProcessor.kt`：硬體 `TYPE_STEP_COUNTER` 增量差分、重開機歸零偵測、跨午夜比例切割
- `ActiveTimeTracker.kt`：步頻節奏視窗過濾（排除單步誤觸與久坐間隔）與 `HH:MM:SS` 格式化
- `MetricsCalculator.kt`：距離、平均時速 (`km/h`)、動態 METs 熱量計算
- `DateDisplayFormatter.kt`：強制顯示明確月日與星期（如 `2026年11月25日 週三`）與週/月/年標題
- `CsvCodec.kt`：UTF-8（含 BOM）雙表 CSV 編解碼、軟刪除欄位 (`is_deleted`, `deleted_at`)、防 CSV 公式注入跳脫

### `:app`（Android Jetpack Compose + Room 骨架）
- `AndroidManifest.xml`：宣告計步與前景服務權限（未申請 `INTERNET` 權限，落實零資料外洩）
- `data/db/Entities.kt`、`PedometerDao.kt`、`AppDatabase.kt`：Room SQLite 時段步數表（含軟刪除與復原 DAO）與感測器檢查點
- `MainActivity.kt`：可啟動並顯示當日明確日期與星期之 Compose 啟動骨架
- `.github/workflows/android-ci.yml`：GitHub Actions 自動化測試與 Debug APK 建置工作流

---

## 3. 自動化測試與編譯驗證結果（2026-11-25）

執行指令：`powershell -ExecutionPolicy Bypass -File .\scripts\gradle-win.ps1 :core-calc:test :app:testDebugUnitTest :app:assembleDebug --rerun-tasks`

- `:core-calc`（JUnit 5）：**5 個測試類別、共 16 項測試全數通過**（0 failed, 0 skipped）
  - `ActiveTimeTrackerTest` (3/3)
  - `CsvCodecTest` (3/3)
  - `DateDisplayFormatterTest` (3/3)
  - `MetricsCalculatorTest` (3/3)
  - `StepDeltaProcessorTest` (4/4)
- `:app`（JUnit 4）：**1 項整合檢查測試通過**（`AppSanityTest` 1/1）
- `:app:assembleDebug`：**成功產出 `app/build/outputs/apk/debug/app-debug.apk`**（16,499,440 bytes）
