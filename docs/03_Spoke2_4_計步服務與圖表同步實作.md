# Spoke 2–4：ABI 分包、日週月年統計圖表、背景計步與 Google Fit / Health Connect 串聯

## 1. arm64 / arm32 / universal ABI 分包設計

於 `app/build.gradle.kts` 啟用 `splits.abi`：
- `arm64-v8a`：適用於現代 64 位元 Android 手機（如 OPPO R11s Plus、近年主流機型）。
- `armeabi-v7a`（arm32）：適用於舊款 32 位元架構手機。
- `universal`：包含雙架構的通用版 APK。
- 搭配 R8 程式碼壓縮、資源縮減與 v1+v2 雙重簽章，每顆 APK 僅約 **1.2–1.3 MB**。

---

## 2. 日 / 週 / 月 / 年統計與首頁設計

- **核心聚合引擎（`:core-calc`）**：
  - `PeriodAggregator.kt`：依據有效（排除 `isDeleted == true`）時段資料，分別彙算「日（0–24h 累計曲線）」、「週（7 日長條圖）」、「月（當月每日長條圖）」、「年（1–12 月長條圖）」。
  - `AccelStepDetector.kt`：3 軸加速度計波峰偵測 + 4 步連續確認視窗，作為無硬體計步晶片機型之備援。
- **參考圖風格首頁（`HomeScreen.kt` + `ChartComponents.kt`）**：
  - 頂部深藍頁籤列：`日` | `週` | `月` | `年` + 相機截圖按鈕 + `⋮` 選單，完全無廣告。
  - 藍天漸層數據區：明確日期星期標題（如 `2026年11月25日 週三`）、超大等寬步數、2x2 格（千卡、公尺/公里、累積運動時間 `HH:MM:SS`、平均時速）、自繪 Canvas 圖表。

---

## 3. 背景計步服務與 Google Fit / Health Connect 串聯

- **背景持續累計**：`StepCounterService.kt`（Foreground Service）於開機或啟動後自動監聽硬體 `TYPE_STEP_COUNTER`（或備援加速度計），持續寫入本機 Room 並顯示靜音通知。
- **Google Fit / Health Connect 橋接（`HealthConnectBridge.kt`）**：
  - Google 原先的 `play-services-fitness` API 已停止新申請並將於 2026 年底全面停服，官方全面轉向 **Android Health Connect**（Google Fit App 可直接透過 Health Connect 雙向讀取步數與運動紀錄，且資料留存於本機，符合原則 2 零資料外洩）。
  - **Android 9+（API 28+）**：支援授權並一鍵寫入 `StepsRecord` 與 `ExerciseSessionRecord`（使用固定 `clientRecordId` 避免重複寫入）。
  - **Android 7.0–8.1（API 24–27，含 Android 7.1.1）**：因系統未內建 Health Connect，程式自動降級為「本機計步 + 本機 CSV 備份」，不閃退並於設定頁顯示明確說明。

---

## 4. 自動化測試與建置驗證（`v0.2.0`）

- `:core-calc:test`：**7 個測試類別、21 項單元測試全數通過**（含 `PeriodAggregatorTest` 3 項、`AccelStepDetectorTest` 2 項）。
- `:app:testDebugUnitTest`：**1 項通過**；`:app:lintDebug` 與 `:app:lintVitalRelease` 通過。
- `:app:assembleRelease` 成功產出三款 APK：
  - `app-arm64-v8a-release.apk`（`1,259,380` bytes）
  - `app-armeabi-v7a-release.apk`（`1,256,537` bytes）
  - `app-universal-release.apk`（`1,309,720` bytes）
