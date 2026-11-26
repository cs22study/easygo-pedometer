# EasyGo-Pedometer 問題排查與修復紀錄 (Troubleshooting Log)

本文件完整記錄開發與實機測試期間遭遇的所有環境、相容性、感測器、簽章與 UI 問題，作為後續維護與 Hub-and-Spoke 交接依據。

---

## 案例 1：Windows 中文專案路徑導致 JDK 17 測試執行器 `ClassNotFoundException`
- **發生階段**：Spoke 1 本機執行 `.\gradlew.bat :core-calc:test`
- **現象**：Gradle 編譯成功，但 JUnit 測試執行器啟動時拋出 `ClassNotFoundException`，且控制台路徑顯示亂碼。
- **根因**：專案目錄包含中文，Windows 繁體中文環境下 JDK 17 子行程讀取 Gradle `@argfile` 暫存檔時使用系統預設編碼（MS950/Cp950），與 Gradle 寫入的 UTF-8 路徑不符；同時 AGP 預設拒絕非 ASCII 路徑。
- **解法**：
  1. 於 `gradle.properties` 加入 `android.overridePathCheck=true`。
  2. 新增 `scripts/gradle-win.ps1`，執行前自動透過 Windows `subst` 暫時映射純 ASCII 虛擬磁碟機（如 `P:\`），執行完畢後自動解除映射。
- **狀態**：✅ 已解決並納為標準本機建置腳本。

---

## 案例 2：OPPO R11s Plus（Android 7.1.1 / API 25）顯示「無法剖析套件 / APK」
- **發生階段**：`v0.1.0` 安裝測試
- **現象**：手機點擊 `v0.1.0` APK 後系統立即報錯「剖析套件時發生問題」。
- **根因**：`v0.1.0` 的 `minSdk` 設為 `26`（Android 8.0），高於 Android 7.1.1（API 25）。
- **解法**：
  1. `app/build.gradle.kts` 將 `minSdk` 降為 `24`（支援 Android 7.0 以上全機種）。
  2. 啟用 `isCoreLibraryDesugaringEnabled = true` 與 `desugar_jdk_libs:2.1.3`，確保 Android 7.x 正常執行 `java.time` 日期運算。
  3. 搭配 `<uses-sdk tools:overrideLibrary="androidx.health.connect.client" />` 並以 `Build.VERSION.SDK_INT >= 28` 保護所有 Health Connect 呼叫。
- **狀態**：✅ 已於 `v0.1.1` 解決（已以 `aapt2` 實測 `minSdkVersion:'24'`）。

---

## 案例 3：OPPO R11s Plus 安裝 `v0.1.1` Debug APK 時「一直在安裝、跑不停息」
- **發生階段**：`v0.1.1` 安裝測試
- **現象**：可剖析 APK，但進度條無限轉圈。
- **根因**：`v0.1.1` 為未壓縮 Debug APK，且引入了 `material-icons-extended`，內含 **7 個 DEX 檔、高達 57.6 MB 未壓縮位元組碼**（單一 `classes.dex` 即 44.7 MB），在 2017 年硬體（Snapdragon 660）`dex2oat` 驗證耗時極長；同時缺失專屬 PNG 啟動圖示。
- **解法**：
  1. 移除 `material-icons-extended`，改用輕量 Compose `Canvas` 自繪圖示。
  2. 補齊 `mipmap-mdpi` 至 `xxxhdpi` 標準 PNG 圖示。
  3. 測試發布改用啟用 R8 壓縮與資源縮減之 Release 建置，APK 體積由 `17.3 MB` 驟降至約 `1.3–1.4 MB`（2 個精簡 DEX）。
- **狀態**：✅ 已於 `v0.1.2` 解決。

---

## 案例 4：Samsung Galaxy A70 與 Google Fit 同步後，5 分鐘內步數暴衝至 `419,064` 步、時間 `58:12:29`
- **發生階段**：`v0.2.0` 實機測試
- **現象**：實際當日步數約 `6,608` 步，但 App 內突然跳為 `419,064` 步與 `58:12:29`（時段圖在 19 點垂直飆升）。
- **根因**：
  1. 部分機型（如三星）在註冊硬體 `TYPE_STEP_COUNTER` 時，冷啟動會先回報一次 `0`，數秒後才吐出「自上次手機重開機以來的累計總步數（`419,064`）」，使 `StepDeltaProcessor` 誤將 `0 → 419,064` 當成單次增量。
  2. `ActiveTimeTracker` 對單次巨大增量估算爆發秒數（`419,064 / 2 ≈ 209,532 秒 = 58 小時`）。
- **解法**：
  1. `StepDeltaProcessor` 加入冷啟動跳躍防護（`lastRawCounter == 0 && currentRawCounter > 250`）與物理極限防暴衝（超過 `5 步/秒` 視為感測器重校準，僅重新錨定基準點不加步數）。
  2. `ActiveTimeTracker.estimateBurstSeconds` 上限鎖定為 `60 秒`。
  3. `PedometerDao.softDeleteImpossibleSpikes()` 啟動時自動軟刪除單小時 `> 30,000` 步之異常資料；`HealthConnectBridge` 亦拒絕上傳 `> 30,000` 步/小時之異常紀錄。
- **狀態**：✅ 已於 `v0.3.0` 解決並補齊對應單元測試。

---

## 案例 5：Samsung 手機覆蓋更新 APK 出現「未完成安裝」，必須移除舊版才能安裝
- **發生階段**：`v0.3.0` 升級 `v0.4.0`
- **現象**：系統詢問是否更新，但安裝最後顯示「未完成安裝」。
- **根因**：GitHub Actions 每次啟動新 Runner 都會重新產生一把隨機 `debug.keystore`，導致每版 APK 簽章憑證不同（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`）。
- **解法**：
  1. 於本機產生持久化簽章金鑰（置於 `.gitignore` 隔離的 `keystore/` 目錄，憑證 `CN=EasyGo Pedometer, O=cs22study, C=TW`）。
  2. 設定 GitHub Repository Secrets（`ANDROID_KEYSTORE_BASE64` 等 4 項），使雲端 CI 與本機永遠使用同一把永久金鑰進行 v1 + v2 雙重簽章。
- **狀態**：✅ 已於 `v0.5.0` 解決並經 `apksigner` 驗證憑證一致；實測 `v0.5.1 → v0.5.2` 原地覆蓋更新保留資料成功。

---

## 案例 6：OPPO R11s Plus（ColorOS 3.2）點擊下載 APK 仍卡在安裝進度條
- **發生階段**：`v0.5.0`–`v0.5.2` 實機透過瀏覽器/檔案管理員點擊安裝
- **現象**：本機與雲端皆已切換為 `minSdk 24`、`extractNativeLibs=true`、移除 `profileinstaller` 且經永久雙重簽章，但 OPPO 手機端直接點擊 APK 仍卡在進度條。
- **實測 `adb` 與 `logcat` 查出之雙重根因**：
  1. **首次排查（`v0.5.0 → v0.5.1`）**：手機內原本殘留早期隨機憑證舊版，觸發 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，而 ColorOS 3.2 圖形安裝器遇到該錯誤時未提示失敗，反而無限轉圈。
  2. **二次排查（`v0.5.1 → v0.5.2` 實測日誌）**：手機端 `logcat` 捕捉到 **`com.coloros.safecenter`（OPPO 手機管家）發生 ANR 阻塞（`cost = 2194 ms`）** 與 **OPPO 軟體商店背景雲端簽章掃描（`cdo_md5` / `market_check_upgrade`, `cost: 2946 ms`）**。當 ColorOS 3.2 圖形安裝介面在 Wi-Fi 下連線舊版中國大陸安全伺服器逾時或安全中心阻塞時，進度條即卡死；而 **`adb install -r` 直接呼叫底層 `PackageManagerService` 則於 3 秒內覆蓋安裝成功（`Success`）**。
- **建議處置**：
  - 有電腦時：使用 `adb install -r` 原地覆蓋升級，快速且完整保留步數資料。
  - 無電腦僅用手機時：**開啟飛航模式（切斷網路連線）並改由手機內建「檔案管理」點擊 APK 安裝**（可避免 ColorOS 軟體商店雲端檢核與安全中心網路請求逾時卡死）；若仍受阻才需改用第三方安裝器（如 `SAI`）。
- **狀態**：✅ 根因已定位並雙重實測驗證——電腦端 `adb install -r` 與**手機端「飛航模式＋檔案管理員安裝」皆已於 OPPO R11s Plus 實機成功完成更新（`v0.6.0` 驗證通過）**。

---

## 案例 7：首頁累計時間「時:分:秒」在三星與 OPPO 手機上顯示不全（`時:...` / `時:分:...`）
- **發生階段**：`v0.5.0`–`v0.5.1` 實機顯示回饋
- **現象**：時間數值右側的「時:分:秒」單位文字遭裁切。
- **根因**：`00:00:00`（`28.sp`）與「時:分:秒」（`14.sp`）排在同一行，右側又緊鄰 `108dp` 寬的長條小圖，使文字欄寬不足約 `188dp` 而溢位裁切。
- **解法**：將單位改以深金膠囊徽章（`UnitPill`）移至卡片標題列右側，時間數值獨立佔滿下方行寬並套用等寬數字（`tnum`）。
- **狀態**：✅ 已於 `v0.5.2` 解決並於 OPPO R11s Plus 實機截圖確認完全清晰。
