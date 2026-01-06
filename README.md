# LINE Message Bridge (LINE 訊息橋接系統)

這是一個整合 LINE Messaging API 的訊息發送管理系統，結合了 Electron 桌面應用程式與 Google Apps Script (GAS) 雲端綁定功能。

## 🌟 主要功能

### 1. 儀表板 (Dashboard)

- **即時統計**：顯示今日模板使用量統計。
- **發送監控**：即時顯示最近的 API 發送紀錄與狀態。

### 2. 通訊錄管理 (Contacts)

- **雙向同步**：支援從 Google Apps Script (GAS) 匯入使用者綁定資料。
- **重複代碼檢測**：
  - **新增/匯入時**：若代碼 (Code) 重複，顯示黃色警告圖示 ⚠️，但允許存檔。
  - **編輯時**：若代碼與他人重複，**禁止存檔** 並顯示錯誤提示，確保 API 發送安全性。
- **類型支援**：區分「個人 (User)」與「群組 (Group)」。

### 3. 模板管理 (Templates)

- **變數支援**：支援 `{{name}}`, `{{time}}` 等自訂變數。
- **測試發送**：可直接在介面上選擇聯絡人進行測試發送。

### 4. 系統日誌 (Logs)

- 完整記錄所有 API 發送請求，包含成功與失敗的紀錄。

### 5. API 伺服器 (Local API Server)

- 內建 Express 伺服器，提供 RESTful API 供外部系統呼叫。
- 支援 API Key 安全驗證。
- **嚴格目標檢測**：若 API 請求的 `target_code` 對應到多個聯絡人，系統將拒絕發送並回報錯誤。

---

## 🛠️ 系統架構

- **Frontend**: Vue 3 + TypeScript + Element Plus (Dark Mode Tech Style)
- **Backend**: Electron (Main Process) + Express.js
- **Database**: LowDB (本地 JSON 檔案儲存)
- **Cloud Helper**: Google Apps Script (處理 LINE Webhook 與綁定)

---

## 🚀 使用流程 (Workflow)

### 步驟 1：使用者綁定 (User Binding)

1. 將 LINE 機器人加入群組或加為好友。
2. 機器人會自動發送歡迎訊息。
3. 使用者輸入綁定指令：

   ```text
   系統綁定：[自訂名稱]
   範例：系統綁定：IT部門
   ```

4. GAS 會將 `[自訂名稱]` 與 `LINE ID` 存入 Google Sheet。

### 步驟 2：匯入聯絡人 (Import Contacts)

1. 開啟 Electron 應用程式 -> **通訊錄管理**。
2. 點擊 **「從 GAS 匯入」**。
3. 系統會自動比對：
   - **舊 ID**：更新名稱，保留原本設定的代碼 (Code)。
   - **新 ID**：新增聯絡人，預設代碼 = 名稱。
4. 若發現代碼重複，列表會顯示警告，請管理員手動修正。

### 步驟 3：呼叫 API 發送訊息 (Send Message)

外部系統 (如 PowerShell, Python, Postman) 可呼叫本機 API 發送訊息。

- **Endpoint**: `POST http://localhost:3000/api/send`
- **Headers**:
  - `Content-Type`: `application/json`
  - `x-api-key`: `[您的 API Key]` (若有設定)
- **Body (JSON)**:

  ```json
  {
    "target_code": "IT部門",
    "message": "伺服器異常警告！"
  }
  ```

  或是使用模板：

  ```json
  {
    "target_code": "IT部門",
    "template_code": "SERVER_ALERT",
    "params": {
      "server": "Web-01",
      "status": "Down"
    }
  }
  ```

---

## ⚙️ 設定說明 (Configuration)

請至 **「設定 (Settings)」** 頁面配置：

1. **GAS URL**：Google Apps Script 的 Web App URL (用於匯入聯絡人)。
2. **API Port**：內建伺服器的 Port (預設 3000)。
3. **API Key**：用於保護 API 的金鑰 (選填)。
4. **LINE Channel Access Token**：請在 GAS 的「指令碼屬性」中設定，Electron 端僅負責轉發。

---

## 📦 開發與建置 (Development)

```bash
# 安裝依賴
npm install

# 啟動開發模式
npm run dev

# 打包應用程式
npm run build
```

---

## API 使用指南 (API Usage Guide)

本指南說明如何從**其他電腦** (External Client) 呼叫 LINE Message Bridge 的 API 來發送訊息。

### 1. 基本資訊 (Basic Info)

- **API URL**: `http://<Server_IP>:<Port>/api/send`
  - `<Server_IP>`: 請填入安裝 LINE Message Bridge 那台電腦的 IP (例如 `192.168.1.100`)。
  - `<Port>`: 預設為 `3000` (可在設定中修改)。
- **Method**: `POST`
- **Content-Type**: `application/json`

### 2. 認證 (Authentication)

如果在設定中啟用了 **API Key**，請在 Header 加入：

- **Header Name**: `x-api-key`
- **Value**: `[您的 API Key]`

### 3. 請求格式 (Request Body)

您可以選擇「直接發送文字」或「使用模板發送」。

#### 3.1 直接發送文字 (Direct Message)

最簡單的方式，直接指定對象代碼 (target_code) 和訊息內容 (message)。

```json
{
  "target_code": "IT_DEPT",
  "message": "系統警告：伺服器 CPU 使用率過高！"
}
```

- `target_code`: 在通訊錄中設定的代碼 (Code)。
- `message`: 要發送的文字內容。

#### 3.2 使用模板發送 (Template Message)

使用預先定義好的模板，並帶入變數。

```json
{
  "target_code": "IT_DEPT",
  "template_code": "SERVER_ALERT",
  "params": {
    "server": "Web-01",
    "status": "Down",
    "time": "2024-01-01 12:00:00"
  }
}
```

- `template_code`: 在模板管理中設定的代碼。
- `params`: 對應模板內容中的變數 (例如 `{{server}}`)。

### 4. 範例程式碼 (Code Examples)

#### PowerShell (Windows)

```powershell
$url = "http://192.168.15.83:3000/api/send"
$headers = @{
    "Content-Type" = "application/json"
    "x-api-key" = "ITTEST123456"
}
$body = @{
    target_code = "IT_ALERT"
    message = "Hello from PowerShell!"
} | ConvertTo-Json

Invoke-RestMethod -Uri $url -Method Post -Headers $headers -Body $body

#### PowerShell (Windows) - Template Example

```powershell
$url = "http://192.168.15.83:3000/api/send"
$headers = @{
    "Content-Type" = "application/json"
    "x-api-key" = "ITTEST123456"
}
$body = @{
    target_code = "IT_ALERT"
    template_code = "MACHINE_ERROR"
    params = @{
        machine_id = "M001"
        error_code = "ERROR001"
    }
} | ConvertTo-Json

Invoke-RestMethod -Uri $url -Method Post -Headers $headers -Body $body
```

#### Python

```python
import requests
import json

url = "http://192.168.1.100:3000/api/send"
headers = {
    "Content-Type": "application/json",
    "x-api-key": "your-secret-key"
}
data = {
    "target_code": "IT_DEPT",
    "message": "Hello from Python!"
}

response = requests.post(url, headers=headers, json=data)
print(response.json())
```

#### cURL (Linux/Mac)

```bash
curl -X POST http://192.168.1.100:3000/api/send \
  -H "Content-Type: application/json" \
  -H "x-api-key: your-secret-key" \
  -d '{"target_code": "IT_DEPT", "message": "Hello from cURL!"}'
```
