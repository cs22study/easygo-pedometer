# Spoke 5：一鍵全螢幕截圖與計步暴衝修復（`v0.3.0`）

## 1. Galaxy A70 實測 `419,064` 步暴衝根因與修復

### 根因分析
實測 Galaxy A70 上同時出現 `419,064` 步與 `58:12:29`（`209,549` 秒，恰約為步數的一半），原因為：
1. 部分裝置（如三星）在註冊 `Sensor.TYPE_STEP_COUNTER` 後，感測器冷啟動時會先回報一次 `0`（或初始檢查點為 `0`），緊接著在數秒內回傳「自上次手機重開機以來的累計步數」（`419,064` 步）。
2. `StepDeltaProcessor` 將 `0 -> 419,064` 視為真實步行增量，連帶使 `ActiveTimeTracker` 以單批步數估算出 58 小時運動時間。

### 修復措施
1. **冷啟動與物理極限防護（`StepDeltaProcessor.kt`）**：
   - 偵測 `previous.lastRawCounter == 0L && currentRawCounter > 250L`，或單次時間間隔內增量超過人類極限（`> 5 步/秒 + 250 步緩衝`）時，判定為感測器重校準並重新錨定基準點（`baselineReanchored = true`），不寫入步數。
2. **單批運動時間上限（`ActiveTimeTracker.kt`）**：`estimateBurstSeconds` 上限鎖定為 `60 秒`，防止單次感測批次灌入數小時假運動時間。
3. **升級自動清理與 Health Connect 防呆**：
   - `PedometerDao.softDeleteImpossibleSpikes()` 於啟動時自動軟刪除單小時超過 `30,000` 步的異常紀錄（升級 `v0.3.0` 即自動清除舊異常）；
   - `HealthConnectBridge` 拒絕寫入單小時超過 `30,000` 步的紀錄，並新增 `readTodaySteps()` 與設定頁「讀取今日步數」、「清除單日紀錄（軟刪除）」。

---

## 2. Spoke 5：一鍵含系統狀態列截圖與底部預覽小卡

- **元件**：`ScreenCaptureService.kt`（短生命週期 `foregroundServiceType="mediaProjection"` 前景服務）+ `ScreenCaptureBus` + `HomeScreen` 底部 `ScreenshotPreviewCard`。
- **流程**：
  1. 點擊首頁右上角相機圖示 → 彈出系統畫面擷取授權（`MediaProjectionManager.createScreenCaptureIntent()`）。
  2. 授權後延遲 `350 ms` 待系統彈窗完全淡出，透過 `ImageReader` + `VirtualDisplay` 擷取**含頂部系統狀態列時間與電量**的完整畫面（符合「揮汗有禮・全民動起來」審核要求）。
  3. 直接存成 PNG 至相簿 `Pictures/EasyGo-Pedometer/EasyGo_yyyyMMdd_HHmmss.png`，並立即停止前景服務。
  4. 首頁底部浮現 **預覽小卡**（顯示縮圖、檔名、「檢視」、「分享」按鈕，8 秒後自動收合）。

---

## 3. UI 版面優化與測試驗證（`v0.3.0`）

- 修正 2x2 格大數值時「千卡」、「公尺」垂直換行問題（固定單行顯示並自適應字級）。
- 修正「年」頁籤 X 軸 `10月 11月 12月` 字距過密問題。
- **自動化測試**：`:core-calc:test`（**24 項全數通過**，含新增冷啟動 `0 -> 419064` 與 `> 5 步/秒` 防護測試）+ `:app:testDebugUnitTest`（1/1 通過，共 25 項全過）+ `:app:lintDebug` + `:app:lintVitalRelease` 通過。
