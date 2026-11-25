# EasyGo-Pedometer 想走就走計步器 APP — Hub-and-Spoke 開發總覽

> **專案路徑**：`D:\志松資料\GAS程式AI運用小松資料區cs22study\EasyGo-Pedometer想走就走計步器APP`  
> **GitHub Repo**：`https://github.com/cs22study/easygo-pedometer`  
> **目標平台**：Android APK（離線優先、無廣告、本機 CSV 記錄、支援活動截圖）

---

## 五大系統開發原則落地對應

1. **自動化測試驗證**：核心演算法（步數差分、跨日/重開機補償、運動時間、距離/速度/熱量、CSV 讀寫與跳脫）抽離為純 Kotlin / JVM 單元測試模組，搭配 JUnit 5 與 Robolectric / Instrumented Test 自動化驗證。
2. **資安與防外洩**：不接入任何廣告 SDK 與第三方追蹤 SDK（關閉參考圖中的「與開發商共享」）；無 WebView 避免 XSS；不儲存明文敏感帳密；若後續串接 GAS 雲端備份，採用白名單鎖定 Google Sheet / Drive ID 與 Token 雲端比對。
3. **隨時可復原**：資料表與 CSV 匯出欄位內建軟刪除欄位（`is_deleted`, `deleted_at`）；本地採 Git 版控，雲端腳本（若啟用 GAS）採 `clasp` 雙向版控。
4. **零花費架構**：Android APK 透過免費開源工具鏈（Android Studio / Gradle / GitHub Actions）編譯與自動化測試；網頁發布頁使用 GitHub Pages；選用雲端備份使用 Google Apps Script + Google Sheets + Google Drive 免費額度。
5. **節省 Token（Hub-and-Spoke 文件化）**：每個模組在獨立對話開發後，必須將規格、變更與測試結果回寫至 `docs/`。

---

## 文件索引

| 文件 | 說明 | 狀態 |
| :--- | :--- | :--- |
| [01_開發內容分析與設計原理.md](./01_開發內容分析與設計原理.md) | 需求與參考圖對照、活動截圖合規設計、UI 規格、感測器與數學計算原理、CSV 規格 | 已完成 |
| [02_Spoke1_核心引擎與建置紀錄.md](./02_Spoke1_核心引擎與建置紀錄.md) | 本機建置環境、Gradle 專案結構、`:core-calc` 與 Room 實作清單、測試驗證結果 | 已完成 |
| [03_Spoke2_4_計步服務與圖表同步實作.md](./03_Spoke2_4_計步服務與圖表同步實作.md) | arm64/arm32/universal 分包、日週月年圖表首頁、背景計步服務、CSV 匯出與 Health Connect / Google Fit 橋接 | 已完成 |
| [04_Spoke5_一鍵截圖與計步防暴衝修復.md](./04_Spoke5_一鍵截圖與計步防暴衝修復.md) | 419,064 步暴衝根因分析與修復、Spoke 5 `MediaProjection` 含狀態列截圖與預覽小卡、UI 排版優化 | 已完成 |

---

## Hub-and-Spoke 模組開發路線圖 (Spoke Roadmap)

| 階段 | Spoke 模組名稱 | 核心交付項目 | 狀態 |
| :--- | :--- | :--- | :--- |
| **Phase 0** | 開發內容分析與設計規格 | `docs/README.md`、`docs/01_開發內容分析與設計原理.md`、UX 定案 | ✅ 已完成（UX 已確認） |
| **Spoke 1** | 專案骨架與核心計算引擎 | Android Gradle 專案、Room DB + 軟刪除、步數/時間/熱量純計算模組 + 單元測試 | ✅ 已完成（17 項單元測試全數通過 + Debug APK 建置成功） |
| **Spoke 2** | 背景計步服務與感測器管線 | `StepCounterService` (`TYPE_STEP_COUNTER` + 加速度計備援)、`BootReceiver`、單元測試 | ✅ 已完成（`v0.2.0`） |
| **Spoke 3** | 本機 CSV 匯出、設定頁與健康平台串聯 | `LocalCsvExporter`、`PreferencesRepository`、`SettingsScreen`、`HealthConnectBridge` (Google Fit) | ✅ 已完成（`v0.2.0`，CSV 匯入還原可後續擴充） |
| **Spoke 4** | 首頁 UI 與「日/週/月/年」圖表 | 參考圖風格 UI（無廣告）、`日/週/月/年` 頁籤、累計折線圖與長條圖 | ✅ 已完成（`v0.2.0`） |
| **Spoke 5** | 首頁一鍵截圖與預覽小卡模組 | `ScreenCaptureService` (`MediaProjection` 含狀態列截圖)、儲存相簿與底部預覽小卡 | ✅ 已完成（`v0.3.0`） |
| **Spoke 6** | GitHub CI/CD 與選用雲端備份 | GitHub Actions 自動測試與建置 APK、GitHub Pages 下載頁、（選用）GAS/Drive 備份 | ⏳ 待啟動 |
