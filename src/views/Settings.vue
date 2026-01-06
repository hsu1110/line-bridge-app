<template>
  <div class="page-container">
    <div class="glass-panel settings-panel">
      <h2 class="page-title">系統參數設定</h2>
      
      <el-form :model="form" label-width="180px" class="tech-form">
        <el-divider content-position="left" class="tech-divider">網路設定 (NETWORK)</el-divider>
        <el-form-item label="服務 Port">
          <el-input-number v-model="form.port" :min="1024" :max="65535" class="tech-input-number" />
          <div class="tip">內網 API 監聽埠號 (預設 3000)</div>
        </el-form-item>
        
        <el-divider content-position="left" class="tech-divider">LINE API 設定</el-divider>
        <el-form-item label="Channel Token">
          <el-input v-model="form.channelAccessToken" type="textarea" rows="4" placeholder="請輸入 LINE Messaging API 的長期 Token" />
        </el-form-item>

        <el-divider content-position="left" class="tech-divider">整合設定 (INTEGRATION)</el-divider>
        <el-form-item label="綁定模式">
          <el-radio-group v-model="form.bindingMethod">
            <el-radio label="gas">GAS 模式 (Google Sheet)</el-radio>
            <el-radio label="local">本地模式 (Local Webhook) (未完成)</el-radio>
          </el-radio-group>
        </el-form-item>

        <template v-if="form.bindingMethod === 'gas'">
          <el-form-item label="GAS Script URL">
            <el-input v-model="form.gasUrl" placeholder="https://script.google.com/macros/s/.../exec" />
            <div class="tip">用於匯入 ID 的 Google Apps Script 網址</div>
          </el-form-item>
        </template>

        <template v-if="form.bindingMethod === 'local'">
          <el-form-item label="Channel Secret">
            <el-input v-model="form.lineChannelSecret" type="password" show-password placeholder="請輸入 Channel Secret (注意：這不是 Token，請至 Basic Settings 頁面查看)" />
          </el-form-item>
          <el-form-item label="Webhook URL">
            <el-alert
              title="請將此 URL 設定至 LINE Developers 後台"
              type="info"
              :closable="false"
              show-icon
            >
              <template #default>
                <div class="webhook-info">
                  <div class="webhook-url">https://[您的網域]/api/webhook</div>
                  <div class="webhook-note">
                    注意：LINE 要求使用 <strong>HTTPS</strong>。<br>
                    請設定 Nginx 反向代理： <code>https://...</code> ➔ <code>http://localhost:{{ form.port }}</code>
                  </div>
                </div>
              </template>
            </el-alert>
          </el-form-item>
        </template>

        <el-divider content-position="left" class="tech-divider">安全性 (SECURITY)</el-divider>
        <el-form-item label="API Key">
          <el-input v-model="form.apiKey" placeholder="選填，設定後需驗證" show-password />
          <div class="tip">若留空則不啟用驗證。啟用後請在 Header 帶入 x-api-key 或參數 api_key</div>
        </el-form-item>

        <div class="form-actions">
          <el-button type="primary" @click="saveSettings" class="tech-btn-primary">儲存設定</el-button>
          <el-button type="warning" @click="restartServer" :loading="restarting" class="tech-btn-warning">重啟服務</el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'

const form = ref({
  port: 3000,
  channelAccessToken: '',
  gasUrl: '',
  apiKey: '',
  bindingMethod: 'gas',
  lineChannelSecret: ''
})

const restarting = ref(false)

onMounted(async () => {
  const db = await (window as any).ipcRenderer.invoke('db:get-all')
  form.value = { 
    ...db.settings,
    // Ensure defaults if not present in DB
    bindingMethod: db.settings.bindingMethod || 'gas',
    lineChannelSecret: db.settings.lineChannelSecret || ''
  }
})

const saveSettings = async () => {
  const db = await (window as any).ipcRenderer.invoke('db:get-all')
  db.settings = { ...form.value }
  await (window as any).ipcRenderer.invoke('db:save', db)
  ElMessage.success('設定已儲存')
}

const restartServer = async () => {
  restarting.value = true
  try {
    await (window as any).ipcRenderer.invoke('server:restart')
    ElMessage.success('服務已重啟完成')
  } catch (error) {
    ElMessage.error('重啟失敗')
  } finally {
    restarting.value = false
  }
}
</script>

<style scoped>
.page-container {
  padding: 10px;
  display: flex;
  justify-content: center;
}

.settings-panel {
  width: 100%;
  max-width: 800px;
  padding: 40px;
}

.page-title {
  margin-top: 0;
  margin-bottom: 30px;
  font-family: 'Courier New', monospace;
  color: var(--tech-primary);
  letter-spacing: 2px;
  text-align: center;
  border-bottom: 1px solid var(--tech-border);
  padding-bottom: 20px;
}

.tech-divider {
  margin: 30px 0;
  border-color: var(--tech-border);
}
.tech-divider .el-divider__text {
  background-color: var(--tech-bg-panel);
  color: var(--tech-secondary);
  font-family: 'Courier New', monospace;
}

.tip {
  font-size: 12px;
  color: var(--tech-text-dim);
  line-height: 1.5;
  margin-left: 10px;
  margin-top: 5px;
}

.webhook-url {
  font-family: 'Courier New', monospace;
  font-weight: bold;
  color: var(--tech-primary);
  word-break: break-all;
}

.webhook-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.webhook-note {
  font-size: 12px;
  color: var(--tech-text-dim);
  line-height: 1.4;
}

.webhook-note code {
  background: rgba(0, 0, 0, 0.2);
  padding: 2px 4px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  color: var(--tech-secondary);
}

.form-actions {
  margin-top: 40px;
  display: flex;
  justify-content: center;
  gap: 20px;
}

.tech-btn-primary {
  width: 150px;
  height: 40px;
  font-weight: bold;
  letter-spacing: 1px;
  background: linear-gradient(90deg, rgba(0, 243, 255, 0.1), rgba(0, 243, 255, 0.2));
  border: 1px solid var(--tech-primary);
  color: var(--tech-primary);
  position: relative;
  overflow: hidden;
  transition: all 0.3s;
}

.tech-btn-primary:hover {
  background: var(--tech-primary);
  color: #000;
  box-shadow: 0 0 20px var(--tech-primary);
}

.tech-btn-primary::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.4), transparent);
  transition: 0.5s;
}

.tech-btn-primary:hover::before {
  left: 100%;
}

.tech-btn-warning {
  width: 150px;
  height: 40px;
  font-weight: bold;
  letter-spacing: 1px;
  background: repeating-linear-gradient(
    45deg,
    rgba(255, 165, 0, 0.1),
    rgba(255, 165, 0, 0.1) 10px,
    rgba(255, 165, 0, 0.2) 10px,
    rgba(255, 165, 0, 0.2) 20px
  );
  border: 1px solid #ffaa00;
  color: #ffaa00;
  position: relative;
  overflow: hidden;
  transition: all 0.3s;
}

.tech-btn-warning:hover {
  background: #ffaa00;
  color: #000;
  box-shadow: 0 0 20px rgba(255, 170, 0, 0.6);
  text-shadow: none;
}

.tech-btn-warning::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 255, 255, 0.2), transparent);
  transition: 0.5s;
}

.tech-btn-warning:hover::before {
  left: 100%;
}

/* Radio Button Styles */
:deep(.el-radio) {
  color: var(--tech-text-dim);
  margin-right: 30px;
}

:deep(.el-radio__input.is-checked .el-radio__inner) {
  background: var(--tech-primary);
}

:deep(.el-radio__input.is-checked + .el-radio__label) {
  color: var(--tech-primary);
  font-weight: bold;
}
</style>
