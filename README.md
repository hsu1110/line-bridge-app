# LINE Message Bridge

企業內部專用的 LINE Messaging API 統一發送與 Webhook 管理平台（純 Web 前後端分離架構，單一 JAR 部署）。

## 技術架構

- **前端 (`frontend/`)**：Vue 3 + TypeScript + Vite 5 + Element Plus
- **後端 (`backend/`)**：Spring Boot 3.3.5 + JDK 17 + MyBatis-Plus 3.5.9 + Oracle JDBC (`ojdbc8:19.24.0.0`)
- **資料庫**：Oracle Database（建表腳本見 [oracle_init.sql](backend/sql/oracle_init.sql)）

## 目錄結構

```text
line-bridge-app/
├── frontend/                         # 管理後台前端專案（build 輸出至 backend static）
├── backend/                          # Spring Boot 3 後端專案
│   ├── sql/oracle_init.sql           # Oracle DDL 初始化腳本
│   ├── DEPLOY_LINUX.md               # Linux + Nginx 反向代理部署指南
│   └── src/main/resources/
│       ├── application.yml                # 共用設定（預設 profile: local）
│       ├── application-local.example.yml  # 本機/測試環境設定範本（複製為 application-local.yml）
│       └── application-prod.example.yml   # 正式環境設定範本（複製為 application-prod.yml）
├── LINE_MESSAGING_API_DOC_ZH_TW.md   # LINE Messaging API 參考文件
└── API_INTEGRATION_GUIDE.md          # 外部系統 API 串接操作手冊
```

## 本機開發與建置

### 0. 準備環境設定檔（首次 Clone）

`application-local.yml` 與 `application-prod.yml` 含有資料庫帳密與金鑰，已由 `.gitignore` 排除：

```bash
cp backend/src/main/resources/application-local.example.yml backend/src/main/resources/application-local.yml
cp backend/src/main/resources/application-prod.example.yml backend/src/main/resources/application-prod.yml
```

請依實際環境修改對應連線與密碼後再打包或啟動。

### 1. 前端開發

```bash
cd frontend
npm install
npm run dev
```

Vite 開發伺服器預設執行於 `http://localhost:5173`，並將 `/api` 代理至 `http://localhost:3000`。

### 2. 打包整合（前端 + 後端單一 JAR）

前端 `npm run build` 會執行型別檢查並將靜態檔案直接輸出到 `backend/src/main/resources/static/`：

```bash
cd frontend
npm run build

cd ../backend
mvn clean package
```

打包完成後會產生可執行檔 `backend/target/line-bridge-server.jar`。

### 3. 啟動後端

- 測試區（預設啟用 `local` profile）：
  ```bash
  java -jar backend/target/line-bridge-server.jar
  ```
- 正式區（指定 `prod` profile，部署細節請參考 [DEPLOY_LINUX.md](backend/DEPLOY_LINUX.md)）：
  ```bash
  java -jar backend/target/line-bridge-server.jar --spring.profiles.active=prod
  ```

## 主要 API 端點

| 用途 | 方法與路徑 | 驗證方式 |
| --- | --- | --- |
| 服務健康檢查 | `GET /api/status` | 無 |
| 外部系統發送訊息 | `POST /api/send` | Header `x-api-key` |
| 查詢非同步發送工作狀態 | `GET /api/send/jobs/{jobId}` | Header `x-api-key` |
| LINE Webhook 接收（指定 Bot） | `POST /api/webhook/{botCode}` | `X-Line-Signature` |
| LINE Webhook 接收（依 Destination） | `POST /api/webhook` | `X-Line-Signature` |
| 管理後台登入 | `POST /api/admin/auth/login` | 帳號密碼 |

### `POST /api/send` 請求欄位

支援蛇形或駝峰命名：
- `bot_code`：Bot 代碼
- `target_code` / `to`：聯絡人短代碼或 LINE User/Group/Room ID
- `template_code` / `message`：樣板代碼（搭配 `params`）或純文字訊息
- `params`：樣板變數物件
- `async`：是否非同步發送（預設非同步，回傳 HTTP `202` 與 `jobId`）
