# LINE Message Bridge (LINE 訊息橋接系統)

這是一個整合 LINE Messaging API 的訊息發送管理系統，結合了 Electron 桌面應用程式與 Google Apps Script (GAS) 雲端綁定功能。本系統支援 **雙重綁定模式 (Dual Binding Methods)**，並提供即時儀表板監控與本地 API 伺服器。

---

## 🌟 主要功能

### 1. 儀表板監控 (Dashboard)

- **服務狀態**：即時顯示本地 API Server 運作 Port 與狀態。
- **發送統計**：顯示今日發送量與近期發送紀錄。
- **額度監控**：整合 LINE Messaging API Quota，顯示本月剩餘額度 (支援吃到飽/有限額度顯示)。
- **模板分析**：統計今日最常使用的訊息模板。

### 2. 雙重綁定模式 (Dual Binding)

系統支援兩種運作模式，可於「設定」頁面切換：

| 模式 | GAS 模式 (Google Apps Script) | 本地模式 (Local Webhook) |
| :--- | :--- | :--- |
| **適用場景** | 無固定 IP、不想架設反向代理 | 有固定 IP/網域、需即時互動 |
| **接收方式** | Google Apps Script (Webhook) | Electron 本地伺服器 (Webhook) |
| **資料儲存** | Google Sheet | 本地 `contacts.json` |
| **即時性** | 需手動匯入 | **即時生效** |
| **智慧綁定** | 支援 (需更新 GAS) | 支援 |

### 3. 智慧綁定 (Smart Binding)

無論使用哪種模式，系統皆支援自動抓取名稱：

- **加好友 (Follow)**：自動抓取使用者暱稱。
- **機器人入群 (Join)**：自動抓取群組名稱。
- **改名指令**：輸入 `系統綁定：[名稱]` (或 `System Bind: [Name]`) 可手動修改名稱。

### 4. 通訊錄與模板管理

- **通訊錄**：支援從 GAS 匯入 (GAS 模式) 或本地自動儲存 (本地模式)。具備重複代碼檢測功能。
- **模板**：支援 `{{name}}`, `{{time}}` 等自訂變數，並提供測試發送功能。

### 5. API 伺服器 (Local API Server)

- 內建 Express 伺服器，提供 RESTful API。
- **安全性**：支援 API Key 驗證 (可在設定頁面配置)。
- **嚴格目標檢測**：若 API 請求的 `target_code` 對應到多個聯絡人，系統將拒絕發送以避免誤傳。

---

## 📂 專案結構 (Project Structure)

```text
line-bridge-app/
├── electron/               # Electron 主進程與後端邏輯
│   ├── main.ts             # 應用程式入口
│   ├── server/             # 本地 Express 伺服器
│   │   ├── index.ts        # API Endpoints 定義
│   │   └── lineService.ts  # LINE API 串接邏輯
│   └── store/              # 本地資料庫 (LowDB)
├── src/                    # Vue 3 前端介面
│   ├── views/              # 頁面元件 (Dashboard, Contacts, etc.)
│   ├── components/         # 重用元件
│   └── App.vue             # 主畫面佈局
├── gas_script.gs           # Google Apps Script 程式碼 (供 GAS 模式使用)
└── dist-electron/          # 建置後的 Electron 檔案
```

---

## 📖 操作手冊 (Operation Manual)

### 1. 初始設定 (Setup)

#### A. GAS 模式 (推薦初學者)

1. **部署 GAS**：將 `gas_script.gs` 內容複製到 Google Apps Script 專案。
2. **設定屬性**：在 GAS 專案設定中新增 `CHANNEL_ACCESS_TOKEN`。
3. **部署 Web App**：選擇「執行身分：我」、「存取權：任何人」。
4. **App 設定**：選擇 **「GAS 模式」**，填入 GAS URL 與 Channel Access Token。

#### B. 本地模式 (進階)

1. **App 設定**：選擇 **「本地模式」**，填入 Channel Secret 與 Channel Access Token。
2. **Nginx 反向代理**：需將 HTTPS (443) 轉發至 `http://localhost:3000` (務必轉發 `x-line-signature` Header)。
3. **LINE Developer Console**：Webhook URL 填入 `https://[您的網域]/api/webhook` 並開啟 Webhook。

### 2. API 使用指南 (API Usage)

外部系統可呼叫本機 API 發送訊息。

- **Base URL**: `http://localhost:3000` (Port 可於設定修改)

#### 檢查狀態

- **GET** `/api/status`
- **Response**: `{ "status": "running", "port": 3000 }`

#### 發送訊息

- **POST** `/api/send`
- **Headers**:
  - `Content-Type`: `application/json`
  - `x-api-key`: `[您的 API Key]` (若有設定)
- **Body (JSON)**:

```json
{
  "target_code": "IT_DEPT",       // 目標代碼 (優先)
  // "to": "U12345678...",        // 或直接指定 LINE ID
  "template_code": "SERVER_ALERT", // 模板代碼 (優先)
  // "message": "直接發送內容",     // 或直接指定內容
  "params": {                     // 模板變數
    "server": "Web-01",
    "status": "Down"
  }
}
```

---

## 💡 常見問題

### Q1: 如何更新 GAS 程式碼？

在 GAS 編輯器點擊 **「部署」** -> **「管理部署」** -> **「編輯」** -> 版本選擇 **「建立新版本」** -> **「部署」**。

### Q2: 本地模式收不到 Webhook？

請確認您的伺服器有固定 IP 或網域，且 Nginx 已正確設定 SSL (HTTPS)。LINE Webhook **不支援** HTTP。

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
