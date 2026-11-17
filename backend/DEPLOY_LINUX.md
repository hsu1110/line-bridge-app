# LINE Message Hub 部署手冊（Vue 3 + Spring Boot 3 + Oracle）

## 1. 資料庫建表（Oracle 測試區 / 正式區皆同）

請分別於測試區與正式區 Oracle 執行 [oracle_init.sql](sql/oracle_init.sql) 建立 7 張 `LB_*` 資料表（相容 Oracle 11g / 12c / 19c+）。

## 2. 環境設定檔區分

* [application-local.yml](src/main/resources/application-local.yml)：測試區連線（本機預設啟動即使用 `local`）。
* [application-prod.yml](src/main/resources/application-prod.yml)：正式區連線（上線時指定 `--spring.profiles.active=prod`）。

## 3. 打包單一執行檔（Windows 開發機）

```powershell
# 1. 編譯 Vue 3 靜態資源並輸出至 backend/src/main/resources/static
cd frontend
npm install
npm run build

# 2. 執行測試並打包 Spring Boot Fat JAR
cd ../backend
mvn clean package
```

產出檔案：`backend/target/line-bridge-server.jar`

## 4. 部署至 CustomerServiceLAN（192.168.10.186）

上傳 `line-bridge-server.jar` 至 `/home/user/george/work/line-bridge/`，指定 `prod` Profile 啟動：

### 方式 A：沿用原本習慣的 nohup 背景啟動
```bash
cd /home/user/george/work/line-bridge
nohup java -jar line-bridge-server.jar --spring.profiles.active=prod > app.log 2>&1 &
```
*(若正式機帳密不希望包進 JAR，可在同目錄放外部 `application-prod.yml`，Spring Boot 會自動優先讀取外部檔案)*

### 方式 B（推薦）：systemd 託管
建立 `/etc/systemd/system/line-bridge.service`：
```ini
[Unit]
Description=LINE Message Hub Server
After=network.target

[Service]
Type=simple
User=george
WorkingDirectory=/home/user/george/work/line-bridge
ExecStart=/usr/bin/java -jar /home/user/george/work/line-bridge/line-bridge-server.jar --spring.profiles.active=prod
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```
```bash
sudo systemctl daemon-reload
sudo systemctl enable --now line-bridge
```

## 5. DMZ Nginx 反向代理設定（CustomerServiceDMZ `172.21.1.50`）

新增 `/etc/nginx/conf.d/line-webhook.conf`（不影響原本 `1688` 客服系統）：

```nginx
server {
    listen 1689 ssl http2;
    server_name ytcs.ytec.com.tw;

    ssl_certificate     /home/user/george/work/cert/server.cer;
    ssl_certificate_key /home/user/george/work/cert/private.key;
    ssl_protocols       TLSv1.2 TLSv1.3;

    location /line-webhook/ {
        limit_except POST {
            deny all;
        }

        proxy_pass http://192.168.10.186:58080/api/webhook/;

        proxy_http_version 1.1;
        proxy_set_header Host $http_host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        proxy_request_buffering off;
        proxy_connect_timeout 3s;
        proxy_read_timeout 5s;
    }

    location / {
        return 404;
    }
}
```

## 6. 驗收網址

* **管理後台（僅內網）**：`http://192.168.10.186:58080/`
* **對外 Webhook（DMZ 1689 ➔ 192.168.10.186:58080）**：`https://ytcs.ytec.com.tw:1689/line-webhook/<botCode>`
* **發送 API（內網呼叫）**：`POST http://192.168.10.186:58080/api/send`
