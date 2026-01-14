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
        
        <el-divider content-position="left" class="tech-divider">LINE Bot 帳號管理</el-divider>
        
        <div v-for="(account, index) in form.accounts" :key="index" class="account-card">
          <div class="account-header">
            <span class="account-title">Bot #{{ index + 1 }}</span>
            <el-button type="danger" link size="small" @click="removeAccount(index)" v-if="form.accounts.length > 0">移除</el-button>
          </div>
          
          <el-form-item label="Bot 名稱">
            <el-input v-model="account.name" placeholder="例如：IT 報修機器人" />
          </el-form-item>
          
          <el-form-item label="BotID">
            <el-input v-model="account.lineBotId" placeholder="Bot ID：Uxxxx" />
          </el-form-item>

          <el-form-item label="Channel Token">
            <el-input v-model="account.channelAccessToken" type="textarea" rows="3" placeholder="請輸入 Messaging API Token" />
          </el-form-item>
          
          <el-form-item label="Channel Secret">
             <el-input v-model="account.secret" type="password" show-password placeholder="Channel Secret" />
          </el-form-item>


          
          <el-divider v-if="index < form.accounts.length - 1" border-style="dashed" />
        </div>

        <div class="add-account-btn">
          <el-button type="primary" plain @click="addAccount">+ 新增機器人帳號</el-button>
          <el-button type="success" plain @click="importBotsFromGas" :loading="importingBots" v-if="form.bindingMethod === 'gas'">
            <el-icon><Download /></el-icon> 從 GAS 匯入機器人
          </el-button>
        </div>

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
          <el-form-item label="Global Secret">
            <el-input v-model="form.lineChannelSecret" type="password" show-password placeholder="Global Channel Secret (Legacy)" />
             <div class="tip">若使用多帳號，請在上方個別設定 Secret</div>
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

    <!-- Import Bots Dialog -->
    <el-dialog v-model="importDialogVisible" title="從 GAS 匯入機器人" width="70%" class="tech-dialog">
      <div v-if="loadingGas" class="loading-container">
        <div class="tech-loader"></div>
        <div class="loading-text">讀取設定中...</div>
      </div>
      <div v-else>
        <el-table :data="gasData" @selection-change="handleSelectionChange" height="300px">
          <el-table-column type="selection" width="55" />
          <el-table-column label="Bot 名稱">
            <template #default="scope">
              {{ scope.row.localName || scope.row.name }}
            </template>
          </el-table-column>
          <el-table-column prop="lineBotId" label="Bot ID" show-overflow-tooltip />
          <el-table-column label="狀態" width="100">
             <template #default="scope">
               <el-tag v-if="scope.row.status === 'update'" type="warning" size="small">更新</el-tag>
               <el-tag v-else-if="scope.row.status === 'delete'" type="danger" size="small">移除</el-tag>
               <el-tag v-else-if="scope.row.status === 'skip'" type="info" size="small">略過</el-tag>
               <el-tag v-else type="success" size="small">新增</el-tag>
             </template>
          </el-table-column>
        </el-table>
        <div class="dialog-footer">
          <el-button @click="importDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmImport">確認匯入</el-button>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { v4 as uuidv4 } from 'uuid'
import { Download } from '@element-plus/icons-vue'

interface BotAccount {
  id: string
  name: string
  lineBotId: string
  channelAccessToken: string
  secret: string
  quotaLimit: number
  currentUsage: number
  lastSyncTime?: string
  syncing?: boolean // UI state
}

interface SettingsForm {
  port: number
  accounts: BotAccount[]
  gasUrl: string
  apiKey: string
  bindingMethod: 'gas' | 'local'
  lineChannelSecret: string
}

const form = ref<SettingsForm>({
  port: 3000,
  accounts: [],
  gasUrl: '',
  apiKey: '',
  bindingMethod: 'gas',
  lineChannelSecret: ''
})

const restarting = ref(false)
const importingBots = ref(false)
const importDialogVisible = ref(false)
const loadingGas = ref(false)
const gasData = ref<any[]>([])
const selectedImport = ref<any[]>([])

onMounted(async () => {
  const db = await (window as any).ipcRenderer.invoke('db:get-all')
  form.value = { 
    ...db.settings,
    // Ensure defaults if not present in DB
    bindingMethod: db.settings.bindingMethod || 'gas',
    lineChannelSecret: db.settings.lineChannelSecret || '',
    accounts: db.settings.accounts || []
  }
})

const addAccount = () => {
  form.value.accounts.push({
    id: uuidv4(),
    name: 'New Bot',
    lineBotId: '',
    channelAccessToken: '',
    secret: '',
    quotaLimit: 500,
    currentUsage: 0
  })
}

const removeAccount = async (index: number) => {
  try {
    await ElMessageBox.confirm(
      '確定要刪除此機器人帳號嗎？此動作無法復原。',
      '刪除確認',
      {
        confirmButtonText: '刪除',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    form.value.accounts.splice(index, 1)
    ElMessage.success('已移除帳號 (請記得儲存設定)')
  } catch (e) {
    // Cancelled
  }
}

const saveSettings = async () => {
  const db = await (window as any).ipcRenderer.invoke('db:get-all')
  db.settings = JSON.parse(JSON.stringify(form.value))
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





const importBotsFromGas = async () => {
  if (!form.value.gasUrl) {
    return ElMessage.warning('請先設定 GAS Script URL')
  }

  importDialogVisible.value = true
  loadingGas.value = true
  gasData.value = []
  selectedImport.value = [] // Clear previous selection
  
  try {
    // Append ?type=config to URL
    const url = form.value.gasUrl.includes('?') 
      ? `${form.value.gasUrl}&type=config` 
      : `${form.value.gasUrl}?type=config`
      
    const data = await (window as any).ipcRenderer.invoke('gas:fetch', url)
    
    if (!Array.isArray(data)) {
      throw new Error('GAS 回傳格式錯誤')
    }

    const processedData = data.map((item: any) => {
      if (!item.lineBotId) return null
      
      const existing = form.value.accounts.find(a => a.lineBotId === item.lineBotId)
      const changes = []
      let status = 'new'
      let localName = ''

      if (existing) {
        localName = existing.name
        // Check for Token/Secret changes
        if (existing.channelAccessToken !== item.channelAccessToken) changes.push('Token')
        if (existing.secret !== item.secret) changes.push('Secret')
        
        if (changes.length > 0) {
          status = 'update'
        } else {
          status = 'skip'
        }
      }

      return {
        ...item,
        localName,
        status,
        changes,
        existingId: existing?.id
      }
    }).filter(Boolean)

    // Identify Deleted Bots
    const gasIds = new Set(data.map((item: any) => item.lineBotId))
    const deletedBots = form.value.accounts
      .filter(a => a.lineBotId && !gasIds.has(a.lineBotId))
      .map(a => ({
        name: a.name, // GAS name unknown, use local
        localName: a.name,
        lineBotId: a.lineBotId,
        status: 'delete',
        changes: ['Local Only'],
        existingId: a.id
      }))

    // Filter out 'skip' status to match Contacts logic
    gasData.value = [...processedData, ...deletedBots].filter(item => item.status !== 'skip')
    
    if (gasData.value.length === 0) {
      ElMessage.info('GAS 上沒有資料，且本地無須更新')
    }

  } catch (e: any) {
    ElMessage.error('讀取失敗: ' + e.message)
    importDialogVisible.value = false
  } finally {
    loadingGas.value = false
  }
}

const handleSelectionChange = (val: any[]) => {
  selectedImport.value = val
}

const confirmImport = async () => {
  let added = 0
  let updated = 0
  let deleted = 0

  for (const item of selectedImport.value) {
    if (item.status === 'update') {
      const index = form.value.accounts.findIndex(a => a.id === item.existingId)
      if (index !== -1) {
        // Update Token & Secret ONLY. Keep Name.
        form.value.accounts[index].channelAccessToken = item.channelAccessToken
        form.value.accounts[index].secret = item.secret
        updated++
      }
    } else if (item.status === 'delete') {
      const index = form.value.accounts.findIndex(a => a.id === item.existingId)
      if (index !== -1) {
        form.value.accounts.splice(index, 1)
        deleted++
      }
    } else if (item.status === 'new') {
      form.value.accounts.push({
        id: uuidv4(),
        name: item.name, // New bot uses GAS name
        lineBotId: item.lineBotId,
        channelAccessToken: item.channelAccessToken,
        secret: item.secret,
        quotaLimit: 0,
        currentUsage: 0
      })
      added++
    }
  }

  // Save immediately
  await saveSettings()
  importDialogVisible.value = false
  ElMessage.success(`匯入完成：新增 ${added} 筆，更新 ${updated} 筆，移除 ${deleted} 筆`)
}
</script>

<style scoped>
.page-container {
  padding: 10px;
  display: flex;
  height: 100%; /* Ensure full height */
  overflow-y: auto; /* Allow scrolling */
}

.settings-panel {
  padding: 20px; /* Reduced padding */
  margin: auto; /* Center vertically and horizontally, safe for overflow */
  flex-shrink: 0; /* Prevent shrinking */
  max-width: 800px;
  width: 100%;
}

.page-title {
  margin: 0;
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
  flex-wrap: wrap; /* Allow wrapping */
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

/* Account Card Styles */
.account-card {
  background: rgba(0, 0, 0, 0.2);
  border: 1px solid var(--tech-border);
  padding: 15px;
  margin-bottom: 20px;
  border-radius: 4px;
}

.account-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.account-title {
  color: var(--tech-secondary);
  font-weight: bold;
  font-family: 'Courier New', monospace;
}

.add-account-btn {
  display: flex;
  justify-content: center;
  margin-bottom: 30px;
}

.usage-info {
  margin-left: 10px;
  color: var(--tech-text-dim);
  font-size: 12px;
}

/* Responsive Form */
@media (max-width: 600px) {
  :deep(.el-form-item) {
    flex-direction: column;
  }
  :deep(.el-form-item__label) {
    width: 100% !important;
    text-align: left !important;
    justify-content: flex-start;
    margin-bottom: 5px;
  }
  :deep(.el-form-item__content) {
    margin-left: 0 !important;
    width: 100%;
  }
  .tech-btn-primary, .tech-btn-warning {
    width: 100%; /* Full width buttons on mobile */
  }
}

.loading-container {
  text-align: center;
  padding: 40px;
}

.tech-loader {
  width: 40px;
  height: 40px;
  border: 2px solid var(--tech-primary);
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin: 0 auto 20px;
}

.loading-text {
  color: var(--tech-primary);
  letter-spacing: 2px;
  font-size: 12px;
  animation: blink 1s infinite;
}

.dialog-footer {
  margin-top: 20px;
  text-align: right;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

@keyframes blink {
  50% { opacity: 0.5; }
}

</style>
