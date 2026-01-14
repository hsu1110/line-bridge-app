# GAS 設定指南 (Auto-Register Version)

## 1. Google Sheet 結構

請確保您的 Google Sheet (LineBotData) 有以下兩個分頁：

### A. `BotConfig` (機器人設定)
| Timestamp | BotName | BotID | ChannelAccessToken | ChannelSecret |
|-----------|---------|-------|--------------------|---------------|
| (自動) | (New Bot) | Uxxxx... | **PENDING_INPUT** | **PENDING_INPUT** |

### B. `Contacts` (聯絡人清單)
| Timestamp | Name | ID | Type | LinkedBotID |
|-----------|------|----|------|-------------|

## 2. 自動註冊流程

1.  **部署新程式碼**：將 `gas_script.gs` 部署到 Apps Script。
2.  **觸發機器人**：對新的機器人傳送訊息或加好友。
3.  **檢查 Sheet**：
    *   GAS 會自動偵測到陌生的 Bot ID。
    *   它會自動在 `BotConfig` 新增一列。
    *   Token 欄位會顯示 `PENDING_INPUT`。
4.  **填寫 Token**：
    *   請手動將 `PENDING_INPUT` 替換為正確的 Channel Access Token 和 Secret。
    *   **BotName** 也可以順便改成您喜歡的名字。
5.  **完成**：下次該機器人就能正常運作並回覆訊息了！
