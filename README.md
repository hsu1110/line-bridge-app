# LINE Message Bridge (LINE 訊息橋接系統)

這是一個整合 LINE Messaging API 的訊息發送管理系統，結合了 Electron 桌面應用程式與 Google Apps Script (GAS) 雲端綁定功能。本系統支援 **雙重綁定模式 (Dual Binding Methods)**，可依據需求選擇使用 GAS 或本地 Webhook 接收訊息。

---

## 🌟 主要功能

### 1. 雙重綁定模式 (Dual Binding)

系統支援兩種運作模式，可於「設定」頁面切換：

| 模式 | GAS 模式 (Google Apps Script) | 本地模式 (Local Webhook) |
| :--- | :--- | :--- |
| **適用場景** | 無固定 IP、不想架設反向代理 | 有固定 IP/網域、需即時互動 |
| **接收方式** | Google Apps Script (Webhook) | Electron 本地伺服器 (Webhook) |
| **資料儲存** | Google Sheet | 本地 `contacts.json` |
| **即時性** | 需手動匯入 | **即時生效** |
| **智慧綁定** | 支援 (需更新 GAS) | 支援 |

### 2. 智慧綁定 (Smart Binding)

無論使用哪種模式，系統皆支援自動抓取名稱：

- **加好友 (Follow)**：自動抓取使用者暱稱。
- **機器人入群 (Join)**：自動抓取群組名稱。
- **改名指令**：輸入 `系統綁定：[名稱]` 可手動修改名稱。

### 3. 通訊錄管理 (Contacts)

- **雙向同步**：支援從 GAS 匯入資料 (GAS 模式)。
- **重複代碼檢測**：
  - **匯入/新增時**：自動過濾完全重複 (ID & 名稱相同) 的資料。
  - **編輯時**：若代碼 (Code) 重複，禁止存檔並顯示錯誤提示。
- **類型支援**：區分「個人 (User)」與「群組 (Group)」。

### 4. 模板管理 (Templates)

- **變數支援**：支援 `{{name}}`, `{{time}}` 等自訂變數。
- **測試發送**：可直接在介面上選擇聯絡人進行測試發送。

### 5. API 伺服器 (Local API Server)

- 內建 Express 伺服器，提供 RESTful API 供外部系統呼叫。
- 支援 API Key 安全驗證。
- **嚴格目標檢測**：若 API 請求的 `target_code` 對應到多個聯絡人，系統將拒絕發送。

---

## � 操作手冊 (Operation Manual)

### 1. 初始設定 (Setup)

#### A. GAS 模式 (推薦初學者)

1. **部署 GAS**：將 `gas_script.gs` 的內容複製到 Google Apps Script 專案。
2. **設定屬性**：在 GAS 專案設定中，新增 `CHANNEL_ACCESS_TOKEN`。
3. **部署 Web App**：選擇「執行身分：我」、「存取權：任何人」。
4. **App 設定**：
    - 綁定模式選擇 **「GAS 模式」**。
    - 填入 **GAS URL** 與 **Channel Access Token**。

#### B. 本地模式 (進階)

1. **App 設定**：
    - 綁定模式選擇 **「本地模式」**。
    - 填入 **Channel Secret** 與 **Channel Access Token**。
2. **Nginx 反向代理**：
    - 設定 Nginx 將 HTTPS (443) 轉發至 `http://localhost:3000`。
    - 務必轉發 `x-line-signature` Header。
3. **LINE Developer Console**：
    - Webhook URL 填入 `https://[您的網域]/api/webhook`。
    - 開啟 Webhook 功能。

### 2. 使用者綁定流程

1. **使用者/群組** 將 LINE 機器人加為好友或邀請入群。
2. **系統自動綁定**：
    - 機器人會自動抓取使用者暱稱或群組名稱。
    - 若需自訂名稱，請輸入指令：`系統綁定：[名稱]` (例如：`系統綁定：IT部門`)。
3. **匯入資料 (僅 GAS 模式)**：
    - 進入「通訊錄管理」 -> 點擊 **「從 GAS 匯入」**。
    - 系統會自動過濾重複資料，僅顯示新增或名稱變更的項目。

### 3. 發送訊息 (API Usage)

外部系統 (如 PowerShell, Python) 可呼叫本機 API 發送訊息。

- **Endpoint**: `POST http://localhost:3000/api/send`
- **Headers**:
  - `Content-Type`: `application/json`
  - `x-api-key`: `[您的 API Key]`
- **Body (JSON)**:

```json
{
  "target_code": "IT_DEPT",
  "template_code": "SERVER_ALERT",  // 或是直接用 "message": "內容"
  "params": {
    "server": "Web-01",
    "status": "Down"
  }
}
```

---

## 💡 常見問題與 SOP

### Q1: 如何更新 GAS 程式碼但不變動 URL？

1. 在 GAS 編輯器點擊 **「部署」** -> **「管理部署」**。
2. 點擊左側 **「編輯」** (鉛筆圖示)。
3. **關鍵步驟**：在「版本」下拉選單選擇 **「建立新版本 (New version)」**。
4. 點擊 **「部署」** 即可。

### Q2: SOP - 更換 LINE 官方帳號 (Bot)

若需切換至另一個 Bot，請依模式執行：

#### 情境 A：使用 GAS 模式

1. **LINE 後台**：取得新 Bot 的 **Channel Access Token**。
2. **GAS 專案**：更新「指令碼屬性」中的 `CHANNEL_ACCESS_TOKEN`，並**建立新版本**重新部署。
3. **App 設定**：更新 **Channel Access Token** 欄位並儲存。
4. **檢查 Webhook**：確認 LINE 後台的 Webhook URL 仍指向您的 GAS URL。

#### 情境 B：使用本地模式

1. **LINE 後台**：取得新 Bot 的 **Channel Access Token** 與 **Channel Secret**。
2. **App 設定**：更新這兩個欄位，儲存並 **重啟服務**。
3. **更新 Webhook**：將 Webhook URL 填入新 Bot 的 LINE 後台並驗證。

---

## 🛠️ 開發與建置

```bash
# 安裝依賴
npm install

# 啟動開發模式
npm run dev

# 打包應用程式 (Windows)
npm run build
```
