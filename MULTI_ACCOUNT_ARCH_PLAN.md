# 多帳號架構分析報告 (Multi-Account Architecture Analysis) v2

## 1. 目標 (Goal)
透過支援多個 LINE 官方帳號 (Official Accounts) 發送訊息，以利用多個「輕用量方案 (Light Plan)」的免費額度，降低整體營運成本。並精確統計用量與費用狀態。

## 2. 架構變更分析 (Architecture Changes)

### 2.1 Google Apps Script (GAS) 端
目前 GAS 負責將 LINE User ID 與員工資料綁定。
*   **現狀**：單一 `CHANNEL_ACCESS_TOKEN`。
*   **變更**：
    *   需要維護一個 **Token Pool** (帳號池)。
    *   當使用者加入好友時，GAS 需要知道是**哪一個機器人**被加入。
    *   **困難點**：Webhook Event 會包含 `destination` (Bot ID)，GAS 需要根據這個 ID 去撈對應的 Token 來回覆或獲取 Profile。
    *   **資料結構**：需要新增一個 `BotConfig` Sheet 來存 `BotID` 對應 `Token`。

### 2.2 資料庫 (Electron Store)
*   **現狀**：`settings.json` 只有一組 `channelAccessToken` 和 `lineChannelSecret`。
*   **變更**：
    *   廢除單一欄位。
    *   新增 `accounts` 陣列 (Bot 帳號管理)：
      ```typescript
      interface BotAccount {
        id: string;          // UUID (系統內部 ID)
        name: string;        // 例如：IT 報修機器人
        lineBotId: string;   // 例如：@12345xyz (LINE Basic ID)
        channelAccessToken: string;
        secret: string;
        
        // [新增] 用量統計欄位
        quotaLimit: number;  // 每月免費額度 (例如 500)
        currentUsage: number;// 本月已用量 (可透過 API 同步)
        lastSyncTime: string;// 上次同步時間
      }
      ```
    *   `contacts.json` (通訊錄) 需要新增欄位 `linkedBotIds`：
      ```typescript
      interface Contact {
        // ... 原有欄位
        linkedBotIds: string[]; // [新增] 紀錄此人加了哪些機器人 (例如 ["bot_uuid_1", "bot_uuid_2"])
      }
      ```
      *   **用途**：發送訊息時，系統可以自動判斷「這個人有加哪個機器人」，如果他只加了 Bot A，就只能用 Bot A 發給他；如果都加了，則可選擇額度剩最多的發送。

### 2.3 前端介面 (Frontend)
*   **Settings.vue (設定頁)**：
    *   **帳號管理列表**：CRUD 機器人帳號，並設定每個帳號的「每月額度」。
    *   **用量儀表板**：顯示每個 Bot 目前的用量 (例如：Bot A: 450/500, Bot B: 100/500)。
*   **Dashboard.vue (總覽頁)**：
    *   新增「多帳號用量總覽」，一眼看出哪個帳號快爆了。

### 2.4 後端邏輯 (Backend)
*   **用量同步 (Quota Sync)**：
    *   實作 `syncQuota(botId)` 功能，呼叫 LINE API `GET /v2/bot/message/quota/consumption`。
    *   **計費判斷**：
        *   **Push API (主動推播)**：計入額度 (付費/消耗免費額度)。
        *   **Reply API (被動回覆)**：通常免費 (不計入額度)。
        *   系統應定期 (例如每天或每次發送後) 同步 API 回傳的 `totalUsage`，這是最準確的計費依據。
*   **發送邏輯 (Sending Logic)**：
    *   **檢查關係**：發送前檢查 `Contact.linkedBotIds`，確認目標用戶是否為該 Bot 的好友。
    *   **檢查額度**：發送前檢查 `BotAccount.currentUsage < BotAccount.quotaLimit`。
    *   **預估費用**：如果 `currentUsage > quotaLimit`，標記為「付費發送 (Paid)」。

## 3. 針對您問題的回答 (Q&A)

1.  **帳號會根據業務拆分**：
    *   **可行**。這是最推薦的做法 (例如 IT Bot, HR Bot)。系統中每個 Bot 可以設定不同的 `name` 和 `quotaLimit`。

2.  **有沒有辦法記錄發了多少訊息 (人數 * 訊息數)**：
    *   **有**。LINE API `GET /v2/bot/message/quota/consumption` 會回傳該帳號本月「已發送的總則數」。
    *   **注意**：LINE 的計算方式是「發送對象數」。例如發給 10 人，就是 10 則。如果一次 Request 發給 10 人，也是算 10 則。我們的系統會以 API 回傳的數字為準。

3.  **統計付費或是不付費發送**：
    *   **邏輯**：
        *   **免費**：當月累積發送量 <= 免費額度 (例如 500)。
        *   **付費**：當月累積發送量 > 免費額度。
    *   **實作**：系統會在 DB 記錄每個 Bot 的 `quotaLimit`。每次同步用量後，若 `currentUsage > quotaLimit`，介面上會顯示「超額 (付費中)」。

4.  **每個人加過哪個 LINE 帳號需要統計**：
    *   **實作**：在 `contacts.json` 中新增 `linkedBotIds` 陣列。
    *   **資料來源**：
        *   **Webhook (Follow Event)**：當用戶加好友時，Webhook 會收到 `destination` (Bot ID) 和 `userId`。系統會自動將該 Bot ID 加入該 User 的 `linkedBotIds` 列表。
        *   **手動/匯入**：如果是從 GAS 匯入，可能需要 GAS 端也記錄這個關係並傳過來。

## 4. 預估工時 (Estimated Effort)
*   **資料庫與後端重構 (含用量 API 串接)**：約 6-8 小時
*   **前端介面重寫 (含用量顯示)**：約 4-5 小時
*   **GAS 修改與測試**：約 2-3 小時
*   **整合測試**：約 2 小時
*   **總計**：約 14-18 小時 (約 2-2.5 個工作天)
