# EasyGo-Pedometer 想走就走計步器 APP — 專案待辦清單 (TODO List)

> 最後更新：2026-11-26  
> 開發模式：Hub-and-Spoke 模組化開發（嚴格遵守 5 大原則：自動化測試、資安防外洩、軟刪除復原、零花費、回寫 `docs/`）

---

## 一、 已完成項目 (Completed)

- [x] **Phase 0：開發內容分析、參考圖對照與 UX 定案**（`docs/01_開發內容分析與設計原理.md`）
  - 確定無廣告、四頁籤（日/週/月/年）、全自動持續累計、一鍵含狀態列截圖
  - 完成台灣運動部「揮汗有禮・全民動起來」截圖合規要件分析
- [x] **Spoke 1：Android 專案骨架與純 Kotlin 核心計算引擎**（`v0.1.0`–`v0.1.2`）
  - `:core-calc` 模組：`UserProfile`、`StepDeltaProcessor`、`ActiveTimeTracker`、`MetricsCalculator`、`DateDisplayFormatter`、`CsvCodec`
  - Room SQLite 資料庫含軟刪除欄位 (`isDeleted`, `deletedAtIso`)
  - `scripts/gradle-win.ps1` 解決 Windows 中文路徑編碼建置問題
  - GitHub Actions CI 自動化單元測試與 Release 打包（`android-ci.yml`）
- [x] **Spoke 2 & 3 & 4：背景計步、ABI 分包、四維度統計與 Health Connect**（`v0.2.0`）
  - `arm64-v8a`、`armeabi-v7a`、`universal` 三架構分包
  - `StepCounterService`（硬體 `TYPE_STEP_COUNTER` + 3 軸加速度計備援 + 開機自啟）
  - `LocalCsvExporter`（UTF-8 含 BOM、防 CSV 公式注入）
  - `HealthConnectBridge`（Android 9+ 雙向寫入/讀取 Google Fit 生態，Android 7–8 安全降級）
- [x] **Spoke 5：一鍵全螢幕截圖 + 預覽小卡 + 計步防暴衝修復**（`v0.3.0`）
  - `ScreenCaptureService`（`MediaProjection` 含頂部系統狀態列截圖、存相簿與底部預覽小卡）
  - 修復三星等機型 `TYPE_STEP_COUNTER` 冷啟動 `0 → 419,064` 步暴衝與 58 小時假運動時間 Bug
- [x] **Spoke 6：首頁暗黑亮黃粗體 4 卡改版與 GPS 時速表初版**（`v0.4.0`–`v0.5.2`）
  - 首頁改為深黑底 + 亮黃粗體 4 卡（步數、累計時間、換算距離、燃燒熱量）+ 8,000 步進度條
  - 永久簽章金鑰機制（`keystore/` 本機隔離 + GitHub Secrets），解決覆蓋升級「未完成安裝」
  - OPPO R11s Plus（Android 7.1.1 / ColorOS 3.2）相容性強化（`extractNativeLibs=true`、移除 `profileinstaller`）
  - 修正首頁 `時:分:秒` 標籤遭擠壓截斷問題（改用標題列 `UnitPill` 徽章）

---

## 二、 已完成最新項目 (`v0.6.0` — Spoke 7)

- [x] **Spoke 7：GPS 雙顯（實體指針 + 數位）儀表與 Logo 實裝（`v0.6.0`）**
  - [x] 首頁 GPS 入口卡片副標精簡為 **`單車 60 · 車 160 · 高鐵 400`**（修正 `...` 截斷）
  - [x] 測速模式調整為 **`單車 (60)` / `車 (160)` / `高鐵 (400)`**（刻度與極限重設）
  - [x] `SpeedometerGauge` 升級為 **40 格主副刻度線 + 動態旋轉實體指針 + 中心數位時速（`km/h`）**
  - [x] **「腳丫丫 + EzGo 漸層彩色」專屬 APP Logo** 產出全尺寸 `mipmap-*` 與 Android Adaptive Icon
  - [x] 補齊 `GpsRideCalculatorTest` 針對 `車(160)` 模式與 5 刻度計算單元測試（全數 30 項單元測試通過）
  - [x] GitHub Release `v0.6.0` 已發布並驗證簽章；OPPO R11s Plus 實機透過「飛航模式＋檔案管理」順利完成更新

---

## 三、 後續排程 (Backlog)

- [ ] **Spoke 8：本機 CSV 匯入還原與設定頁擴充**
  - [ ] 實作設定頁「從本機 CSV 匯入還原」功能（含欄位與注入防呆驗證）
  - [ ] 設定頁新增「保留天數 / 清除快取 / 資料統計總覽」與「首頁深色黃 / 經典藍天主題切換」
- [ ] **Spoke 9：零花費雲端選用備份與發布官網（原則 4）**
  - [ ] GitHub Pages 靜態下載引導頁（直接指向最新 GitHub Release `arm64`/`arm32`/`universal` APK）
  - [ ] （選用）Google Apps Script + Google Sheets + Google Drive 加密備份腳本（採 `clasp` 雙向版控、白名單鎖定 ID、軟刪除同步）
- [ ] **Spoke 10：Google Play 正式發布準備（選用）**
  - [ ] 產生獨立 Play App Signing / Upload Key 與隱私權政策頁面（GitHub Pages）
