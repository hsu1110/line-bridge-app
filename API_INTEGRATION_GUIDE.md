# LINE Message Bridge 外部系統 API 串接操作手冊

> 本手冊說明外部系統（ERP、MES、HR、監控告警、排程腳本等）如何透過 HTTP REST API 呼叫 **LINE Message Bridge** 發送 LINE 訊息與查詢發送結果。本文件設計為可獨立閱讀之技術規格，方便直接提供給開發人員或其他 AI 開發助手進行系統串接。

---

## 1. 服務位址與認證方式

### 1.1 Base URL
- **正式環境（內網呼叫）**：`http://192.168.10.186:58080`
- **測試／本機環境**：`http://localhost:3000`（依啟動 `SERVER_PORT` 而定）

> 外部系統僅能於公司內網呼叫 `/api/send/**` 與 `/api/status`，DMZ（Port `1689`）僅開放給 LINE Platform 傳入 Webhook，不對外開放發送 API。

### 1.2 認證與權限控管
所有發送與查詢任務端點（`/api/send/**`）皆須透過 HTTP Header 帶入系統專屬 API Key：

```http
x-api-key: <你的系統 API Key>
Content-Type: application/json; charset=utf-8
```

- **取得方式**：請聯繫管理員於後台「外部系統 API 金鑰」頁面建立系統並取得 `API Key`（例：`lbk_...`）。
- **安全注意事項**：請勿將 `API Key` 寫死在前端程式碼或放在 URL Query String（易留存於伺服器 access log）；應存放於呼叫端系統的設定檔或環境變數。
- **權限限制**：每個 API Key 可設定允許呼叫的 **Bot 白名單**、**模板白名單** 及 **每分鐘速率上限（Rate Limit）**，超限會回傳 HTTP `403` 或 `429`。

---

## 2. 串接前置作業（管理後台操作）

外部系統寫程式前，請先向平台管理員取得或確認以下代碼：

1. **`bot_code`（發送機器人代碼）**
   - 例如：`IT_BOT`、`HR_BOT`（於後台「機器人帳號設定」查看）。
2. **`target_code`（接收對象短代碼）**
   - 讓收件人先將該官方帳號加為好友，或將該機器人邀請加入目標 LINE 群組。
   - 平台收到 LINE `follow` / `join` 事件後，會自動在後台「通訊錄與綁定」建檔並產生不可變動的純英數短代碼：
     - 個人：`USR_XXXXXX`
     - 群組：`GRP_XXXXXX`
   - 管理員亦可於後台自訂易記代碼（例如 `IT_ALERT`、`ERP_ADMIN`）。
   - **重要**：本系統預設開啟嚴格綁定檢查（`strict-binding-check: true`），目標聯絡人或群組必須已綁定該 `bot_code`，否則發送會被擋下（回傳 `400`）。
3. **`template_code` 與必填變數（建議使用模板）**
   - 於後台「訊息模板與測試」建立模板，變數格式為 `{{變數名稱}}`（僅允許英數字、底線、點與連字號，例如 `{{machine_id}}`、`{{msg}}`）。
   - 若不需要模板，亦可直接使用 `message` 欄位傳送純文字。

---

## 3. API 端點規格

### 3.1 健康檢查（無需驗證）

- **Endpoint**: `GET /api/status`
- **用途**：確認服務是否存活。
- **成功回應 (`200 OK`)**：
```json
{
  "status": "running",
  "port": 58080,
  "timestamp": "2026-11-18T09:30:00.123456"
}
```

---

### 3.2 發送 LINE 訊息

- **Endpoint**: `POST /api/send`
- **Headers**:
  - `x-api-key`: `<API_KEY>`（必填）
  - `Content-Type`: `application/json; charset=utf-8`（必填）

#### 請求欄位（Body JSON）
欄位同時相容 `snake_case`（推薦）與 `camelCase`：

| 欄位名稱 | 必填 | 型別 | 說明 |
| --- | --- | --- | --- |
| `bot_code` / `botCode` | 是 | String | 發送用機器人代碼（亦相容 Bot UUID 或 `U...` User ID），例：`"IT_BOT"` |
| `target_code` / `targetCode` | 與 `to` 擇一 | String | 通訊錄聯絡人／群組代碼（推薦使用），例：`"GRP_A3F9K2"` 或 `"IT_ALERT"` |
| `to` | 與 `target_code` 擇一 | String | 直接指定 LINE `userId` / `groupId`（若該對象已建檔仍會檢查 Bot 綁定） |
| `template_code` / `templateCode` | 與 `message` 擇一 | String | 訊息模板代碼，例：`"MACHINE_ERR"`（同時帶入時優先於 `message`） |
| `message` | 與 `template_code` 擇一 | String | 直接發送的純文字訊息（當未指定 `template_code` 時使用） |
| `params` | 使用模板時視需要 | Object | 模板變數鍵值對，例如 `{"machine_id": "CNC-01", "reason": "溫度異常"}` |
| `async` | 否 | Boolean | 是否非同步發送，預設 `true`（建議正式串接維持預設）。設為 `false` 則同步等待 LINE API 回傳結果 |

#### 回應結果

**情況 A：非同步發送（預設 `async` 省略或 `true`）→ HTTP `202 Accepted`**

系統驗證參數、權限、綁定關係與渲染完模板後，立即寫入任務並秒回 `jobId`（背景執行緒自動對 LINE API 最多重試 3 次並帶 `X-Line-Retry-Key` 防重複扣款）：

```json
{
  "success": true,
  "jobId": "6f8c2a1b-9e4d-4c1a-8b2f-123456789abc",
  "status": "QUEUED",
  "renderedContent": "機台 CNC-01 發生 溫度異常",
  "error": null
}
```

**情況 B：同步發送（`async: false`）→ HTTP `200 OK`**

```json
{
  "success": true,
  "jobId": "6f8c2a1b-9e4d-4c1a-8b2f-123456789abc",
  "status": "SUCCESS",
  "renderedContent": "機台 CNC-01 發生 溫度異常",
  "error": null
}
```

> 若同步模式推送失敗（例如 Token 錯誤、用戶已封鎖或 LINE 額度用盡），會直接回傳 HTTP `502 Bad Gateway`。

**情況 C：失敗回應格式（4xx / 5xx）**

```json
{
  "success": false,
  "error": "缺少模板必填參數: reason"
}
```

---

### 3.3 查詢非同步發送任務結果

- **Endpoint**: `GET /api/send/jobs/{jobId}`
- **Headers**:
  - `x-api-key`: `<API_KEY>`（必填）

> **權限與限流提醒**：
> - 僅允許查詢**同一 API Key** 所建立的任務，跨系統查詢其他系統的 `jobId` 會回傳 `403 Forbidden`。
> - 本端點與 `POST /api/send` **共用該 API Key 的每分鐘速率上限（Rate Limit）**，請避免使用無間隔迴圈密集輪詢（建議間隔 2~5 秒以上，並設定最大查詢次數）。

#### 成功回應 (`200 OK`)

```json
{
  "id": "6f8c2a1b-9e4d-4c1a-8b2f-123456789abc",
  "clientId": "c1a2b3...",
  "clientName": "ERP 系統",
  "botId": "b1a2c3...",
  "botCode": "IT_BOT",
  "targetCode": "IT_ALERT",
  "targetLineId": "C1234567890abcdef...",
  "templateCode": "MACHINE_ERR",
  "content": "機台 CNC-01 發生 溫度異常",
  "status": "SUCCESS",
  "retryKey": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "errorMessage": null,
  "sourceIp": "192.168.10.50",
  "durationMs": 182,
  "createdAt": "2026-11-18T09:35:10.456"
}
```

`status` 狀態說明：
- `QUEUED`：已進入發送佇列，等待執行或重試中
- `SUCCESS`：已成功投遞至 LINE Platform
- `FAILED`：發送失敗，失敗原因請檢視 `errorMessage`

---

## 4. HTTP 狀態碼與常見錯誤排除

| HTTP 狀態碼 | 常見原因與錯誤訊息 | 處理方式 |
| --- | --- | --- |
| `200 OK` | 同步發送成功，或查詢任務狀態成功 | 正常處理 |
| `202 Accepted` | 非同步發送任務已受理 (`status: QUEUED`) | 若需確認送達結果，可保存 `jobId` 稍後輪詢 `/api/send/jobs/{jobId}` |
| `400 Bad Request` | 1. `缺少 bot_code 參數`<br>2. `請提供 target_code 或 to`<br>3. `請提供 template_code 或 message`<br>4. `目標 [xxx] 尚未綁定機器人 [yyy]`<br>5. `缺少模板必填參數: var1, var2`<br>6. `訊息長度不可超過 5000 字元` | 檢查必填欄位與訊息長度（<= 5000 字）；若為未綁定錯誤，請確認目標對象已加好友／機器人已在群組中；若為缺少參數，補齊 `params` 內鍵值（值不可為 `null`） |
| `401 Unauthorized` | `缺少 API Key` 或 `無效或已停用的 API Key` | 檢查 Header `x-api-key` 是否正確（僅支援 Header，不支援 URL 參數）及後台該系統是否為啟用狀態 |
| `403 Forbidden` | 1. `機器人已停用`<br>2. `此 API Key 無權使用機器人`<br>3. `此 API Key 無權使用模板`<br>4. `無權查詢其他系統的發送任務` | 至後台確認機器人狀態與該 API Client 的白名單權限，或確認 `jobId` 屬於目前呼叫端系統 |
| `404 Not Found` | 找不到指定的 `bot_code`、`target_code`、`template_code` 或 `jobId` | 核對後台代碼拼字是否完全一致（區分大小寫） |
| `429 Too Many Requests` | `超過每分鐘呼叫上限 (N 次/分)` | 呼叫端降低請求頻率、錯峰發送，或請管理員調高該 API Client 的 Rate Limit |
| `502 Bad Gateway` | 同步模式（`async: false`）呼叫 LINE API 失敗 | 檢視 `error` 訊息（常見為本月額度已滿、使用者封鎖 Bot 或 Token 失效） |
| `503 Service Unavailable` | `目前發送佇列忙碌中，請稍後再試` | 後端非同步佇列已滿載，呼叫端請實作退避重試（backoff retry） |

---

## 5. 各程式語言呼叫範例

以下皆以正式內網位址 `http://192.168.10.186:58080` 為例（測試時請自行替換 Base URL、API Key 與代碼）。

### 5.1 curl（Linux / macOS / Git Bash）

**使用模板發送（非同步，推薦）：**
```bash
curl -X POST "http://192.168.10.186:58080/api/send" \
  -H "Content-Type: application/json; charset=utf-8" \
  -H "x-api-key: <YOUR_API_KEY>" \
  -d '{
    "bot_code": "IT_BOT",
    "target_code": "IT_ALERT",
    "template_code": "MACHINE_ERR",
    "params": {
      "machine_id": "CNC-01",
      "reason": "主軸溫度過高"
    }
  }'
```

**直接發送純文字（同步等待結果）：**
```bash
curl -X POST "http://192.168.10.186:58080/api/send" \
  -H "Content-Type: application/json; charset=utf-8" \
  -H "x-api-key: <YOUR_API_KEY>" \
  -d '{
    "bot_code": "IT_BOT",
    "target_code": "IT_ALERT",
    "message": "排程備份已完成。\n耗時：12 秒",
    "async": false
  }'
```

### 5.2 Windows PowerShell（UTF-8 請求與回應正確寫法）

> - **請求 Body**：Windows PowerShell 5.1 傳中文字串須顯式轉成 UTF-8 bytes，否則中文會變 `???`。
> - **回應解析**：Windows PowerShell 5.1 的 `Invoke-RestMethod` 解析不帶 `charset` 的 JSON 回應時中文容易亂碼；使用下方 `Invoke-WebRequest -UseBasicParsing` + UTF-8 解碼可同時相容 **PS 5.1 與 PS 7**。

```powershell
$headers = @{
    "x-api-key"    = "<YOUR_API_KEY>"
    "Content-Type" = "application/json; charset=utf-8"
}

$bodyObj = @{
    bot_code      = "IT_BOT"
    target_code   = "IT_ALERT"
    template_code = "MACHINE_ERR"
    params        = @{
        machine_id = "CNC-01"
        reason     = "冷卻水壓異常"
    }
}

$jsonBytes = [System.Text.Encoding]::UTF8.GetBytes(($bodyObj | ConvertTo-Json -Depth 5))

$rawResp = Invoke-WebRequest `
    -Uri "http://192.168.10.186:58080/api/send" `
    -Method Post `
    -Headers $headers `
    -Body $jsonBytes `
    -UseBasicParsing

# PS 5.1 若直接讀 $rawResp.Content 遇中文亂碼時，從 RawContentStream 強制以 UTF-8 解碼
if ($PSVersionTable.PSVersion.Major -lt 6 -and $rawResp.RawContentStream) {
    $rawJson = [System.Text.Encoding]::UTF8.GetString($rawResp.RawContentStream.ToArray())
} else {
    $rawJson = $rawResp.Content
}

$resp = $rawJson | ConvertFrom-Json
$resp
```

### 5.3 Python (`requests`)

```python
import os
import requests

LINE_BRIDGE_URL = os.getenv("LINE_BRIDGE_URL", "http://192.168.10.186:58080")
LINE_BRIDGE_API_KEY = os.getenv("LINE_BRIDGE_API_KEY", "<YOUR_API_KEY>")

def send_line_template(bot_code: str, target_code: str, template_code: str, params: dict, async_mode: bool = True) -> dict:
    resp = requests.post(
        f"{LINE_BRIDGE_URL}/api/send",
        headers={
            "x-api-key": LINE_BRIDGE_API_KEY,
            "Content-Type": "application/json; charset=utf-8",
        },
        json={
            "bot_code": bot_code,
            "target_code": target_code,
            "template_code": template_code,
            "params": params,
            "async": async_mode,
        },
        timeout=5,
    )
    resp.raise_for_status()
    return resp.json()

# 呼叫範例
result = send_line_template(
    bot_code="IT_BOT",
    target_code="IT_ALERT",
    template_code="MACHINE_ERR",
    params={"machine_id": "CNC-01", "reason": "異常停機"},
)
print("Job ID:", result["jobId"], "Status:", result["status"])
```

### 5.4 Java 17+ (`java.net.http.HttpClient`)

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public class LineBridgeClient {
    private static final String BASE_URL = "http://192.168.10.186:58080";
    private static final String API_KEY = System.getenv("LINE_BRIDGE_API_KEY");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public static String sendTemplate(String botCode, String targetCode, String templateCode, Map<String, Object> params) throws Exception {
        String payload = MAPPER.writeValueAsString(Map.of(
                "bot_code", botCode,
                "target_code", targetCode,
                "template_code", templateCode,
                "params", params
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/api/send"))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("x-api-key", API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("LINE Bridge 呼叫失敗 (" + response.statusCode() + "): " + response.body());
        }
        return response.body();
    }
}
```

### 5.5 C# (.NET `HttpClient`)

```csharp
using System.Net.Http.Json;
using System.Text.Json;

using var client = new HttpClient
{
    BaseAddress = new Uri("http://192.168.10.186:58080"),
    Timeout = TimeSpan.FromSeconds(8)
};
client.DefaultRequestHeaders.Add("x-api-key", Environment.GetEnvironmentVariable("LINE_BRIDGE_API_KEY"));

var body = new
{
    bot_code = "IT_BOT",
    target_code = "IT_ALERT",
    template_code = "MACHINE_ERR",
    @params = new Dictionary<string, object>
    {
        ["machine_id"] = "CNC-01",
        ["reason"] = "主軸負載過高"
    }
};

var response = await client.PostAsJsonAsync("/api/send", body);
var content = await response.Content.ReadAsStringAsync();
if (!response.IsSuccessStatusCode)
{
    throw new Exception($"LINE 發送失敗 {(int)response.StatusCode}: {content}");
}
Console.WriteLine(content);
```

### 5.6 Node.js (`fetch`, Node 18+)

```javascript
const res = await fetch(`${process.env.LINE_BRIDGE_URL || 'http://192.168.10.186:58080'}/api/send`, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json; charset=utf-8',
    'x-api-key': process.env.LINE_BRIDGE_API_KEY,
  },
  body: JSON.stringify({
    bot_code: 'IT_BOT',
    target_code: 'IT_ALERT',
    template_code: 'MACHINE_ERR',
    params: {
      machine_id: 'CNC-01',
      reason: '感測器離線',
    },
  }),
  signal: AbortSignal.timeout(5000),
});

const data = await res.json();
if (!res.ok) {
  throw new Error(`LINE Bridge error (${res.status}): ${data.error}`);
}
console.log('Queued jobId:', data.jobId);
```

---

## 6. 串接最佳實務與注意事項

1. **優先採用非同步模式（預設 `async: true`）**：外部系統的主交易流程不應等待 LINE API 回應才結束；若對送達時間敏感或執行批次發送，建議設定較短 HTTP timeout（如 `3~5 秒`），拿到 `202` 與 `jobId` 即視為派發成功。
2. **使用模板 + `target_code` 取代寫死文案與 LINE ID**：
   - 文案要修改字眼或增減欄位時，管理員直接於後台編輯模板即可生效，呼叫端不必重新部署程式。
   - 使用者改 LINE 暱稱不影響 `USR_XXXXXX` / `GRP_XXXXXX` 短代碼。
3. **LINE 官方額度扣減計算規則**：
   - LINE Messaging API Push 訊息依**實際觸及人數**計費。發送到一個 30 人的群組 1 次會扣 **30 則**月額度。
   - 開發測試期間請優先通知單一測試帳號或小群組，或先使用後台「Dry-Run 預覽」確認內容。
4. **模板變數特別注意**：
   - `params` 中不可漏傳模板裡出現的任意 `{{var}}`，且對應值不可為 `null`（傳空字串 `""` 可通過，建議仍填有意義字串如 `"-"`）。
   - 文字內需要換行請使用 `\n`，單則 LINE 文字訊息上限為 5000 字元。
