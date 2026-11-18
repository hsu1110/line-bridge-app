-- =========================================================================
-- 既有線上資料庫升級腳本：LB_BOT_ACCOUNT 新增歡迎詞開關與自訂內容欄位
-- 註：新版本應用程式啟動時亦會自動檢查並補齊缺少欄位，手動升級可執行此腳本
-- =========================================================================

ALTER TABLE LB_BOT_ACCOUNT ADD WELCOME_ENABLED NUMBER(1) DEFAULT 1 NOT NULL;
ALTER TABLE LB_BOT_ACCOUNT ADD WELCOME_MESSAGE CLOB;
